"""Deterministic PT-2 continuation contracts; no live network/model/runtime calls."""
import importlib.util
from datetime import datetime, timedelta, timezone
import json
import hashlib
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SPEC = importlib.util.spec_from_file_location(
    "pt2_continuation", Path(__file__).with_name("pt2-realistic-continuation.py")
)
cont = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(cont)
ROOT = Path(__file__).resolve().parents[2]
SHA = "continuation-dispatch-sha"
RECOVERY_RUN_ID = cont.ZERO_RUN_ID + 1


def history_run(run_id, sha, title=cont.CONTINUATION_RUN_TITLE):
    return {"id": run_id, "run_attempt": 1, "head_sha": sha, "head_branch": "main",
            "event": "workflow_dispatch", "display_title": title, "workflow_id": cont.WORKFLOW_ID,
            "path": cont.WORKFLOW_PATH, "repository": {"full_name": cont.REPOSITORY},
            "status": "completed", "conclusion": "failure"}


def history_pages(extra=()):
    runs = [history_run(RECOVERY_RUN_ID, SHA), history_run(cont.ZERO_RUN_ID, cont.ZERO_DISPATCH_SHA),
            history_run(cont.PRIOR_RUN_ID, cont.PRIOR_DISPATCH_SHA, "original baseline"), *extra]
    return [{"total_count": len(runs), "workflow_runs": runs}]


def frozen_dataset():
    return json.loads((ROOT / "evaluation" / cont.pt2.DATASET / "dataset.json").read_text())


def prior_observations():
    rows = [
        {
            "caseId": cont.CASE1_ID,
            "status": "SUCCEEDED",
            "uploadAttempts": 1,
            "analysisJobId": cont.CASE1_JOB_ID,
            "projectId": 5,
        },
        {
            "caseId": cont.CASE2_ID,
            "status": "TERMINAL_NOT_OBSERVED",
            "uploadAttempts": 1,
            "analysisJobId": cont.CASE2_JOB_ID,
            "projectId": cont.CASE2_PROJECT_ID,
            "requestStartedAt": "2026-10-06T14:45:04.739214+00:00",
            "acceptedResponseObservedAt": "2026-10-06T14:45:06.190778+00:00",
            "censoredObservationMs": cont.CASE2_CENSORED_MS,
        },
    ]
    rows.extend({"caseId": case_id, "status": "NOT_RUN", "uploadAttempts": 0}
                for case_id in cont.CONTINUATION_CASE_IDS)
    return {"runId": f"pt2-realistic-{cont.PRIOR_RUN_ID}", "cases": rows}


class FakeClient:
    def __init__(self, mode="normal"):
        self.mode = mode
        self.calls = []
        self.posts = []
        self.time = 0
        self.current_job = None
        self.current_project = None
        self.job_polls = {}

    def request(self, method, path, directory, name, **kwargs):
        self.time += 1
        self.calls.append((method, path, name))
        status = 200
        body = {}
        transport = 0

        if method == "POST":
            self.posts.append(kwargs["fixture"].name)
            number = len(self.posts) + 6
            self.current_job = f"job-{number}"
            self.current_project = number
            if self.mode == "duplicate":
                self.current_job = cont.CASE2_JOB_ID
            body = {
                "analysisJobId": self.current_job,
                "projectId": self.current_project,
                "sourceFileId": 100 + number,
                "createdAt": f"2026-10-06T00:00:{number:02}Z",
            }
            status = 201
            if self.mode == "lost": transport = 28
            if self.mode == "malformed": body = {}
            if self.mode == "auth": status = 403
            if self.mode == "rejected": status = 400
            if self.mode == "duplicate-project": body["projectId"] = cont.CASE2_PROJECT_ID
        elif path == "/api/analysis/jobs/" + cont.CASE2_JOB_ID:
            count = self.job_polls.get(path, 0) + 1
            self.job_polls[path] = count
            body = {
                "id": cont.CASE2_JOB_ID,
                "projectId": cont.CASE2_PROJECT_ID,
                "status": "RUNNING" if self.mode == "prior-stuck" else "SUCCEEDED",
                "resultObjectKey": "prior-result",
                "quality": {"technicalStatus": "PASS", "qualityStatus": "UNKNOWN"},
            }
            if self.mode == "prior-auth": status = 403
            if self.mode == "prior-wrong-job": body["id"] = "unrelated-job"
            if self.mode == "prior-failed":
                body.update(status="FAILED", resultObjectKey=None, failureReason="natural rejection")
        elif path.startswith("/api/analysis/jobs/"):
            job_id = path.rsplit("/", 1)[-1]
            count = self.job_polls.get(path, 0) + 1
            self.job_polls[path] = count
            status_value = "SUCCEEDED"
            if self.mode == "first-censored-drains" and job_id == "job-7":
                status_value = "RUNNING" if self.time < 10 else "SUCCEEDED"
            if self.mode == "first-never-drains" and job_id == "job-7":
                status_value = "RUNNING"
            body = {
                "id": job_id,
                "projectId": int(job_id.split("-")[-1]),
                "status": status_value,
                "resultObjectKey": "result-" + job_id,
                "quality": {"technicalStatus": "PASS", "qualityStatus": "EVIDENCE_BACKED"},
            }
            if self.mode in ("measurement-crossing", "drain-crossing") and job_id == "job-7":
                # Request begins inside the bound, but its terminal response arrives outside it.
                self.time += 8 if self.mode == "measurement-crossing" else 50
            if self.mode == "wrong-job": body["id"] = "unrelated-job"
            if self.mode == "poll-auth": status = 401
            if self.mode == "natural-failure": body.update(status="FAILED", resultObjectKey=None)
        elif path.endswith("/terraform/main.tf"):
            project_id = int(path.split("/")[3])
            latest = cont.CASE2_JOB_ID if project_id == cont.CASE2_PROJECT_ID else f"job-{project_id}"
            body = {
                "projectId": project_id,
                "latestAnalysisJobId": latest,
                "latestResultObjectKey": "prior-result" if project_id == cont.CASE2_PROJECT_ID else "result-" + latest,
                "content": 'resource "aws_s3_bucket" "example" {}\n',
            }
            if self.mode == "wrong-result": body["latestResultObjectKey"] = "unrelated-result"
            if (self.mode == "natural-failure" and project_id != cont.CASE2_PROJECT_ID
                    or self.mode == "prior-failed" and project_id == cont.CASE2_PROJECT_ID):
                status, body = 404, None
        elif path.startswith("/api/projects/"):
            project_id = int(path.split("/")[3])
            latest = cont.CASE2_JOB_ID if project_id == cont.CASE2_PROJECT_ID else f"job-{project_id}"
            body = {
                "projectId": project_id,
                "latestAnalysisJobId": latest,
                "analysisStatus": "SUCCEEDED",
            }
            if self.mode == "wrong-project": body["latestAnalysisJobId"] = "unrelated-job"
            if self.mode == "project-auth": status = 403
        elif path == "/actuator/prometheus":
            body = "metrics"

        response = {
            "method": method,
            "path": path,
            "httpStatus": status,
            "transportExitCode": transport,
            "observedAt": (datetime(2026,10,6,tzinfo=timezone.utc) + timedelta(seconds=self.time)).isoformat(),
            "json": body,
        }
        cont.pt2.write_json(directory / (name + ".json"), response)
        return {**response, "monotonic": self.time}

    def wait(self, seconds):
        self.time += seconds


class ContinuationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.output = Path(self.temp.name) / "out"
        self.prior = Path(self.temp.name) / "prior"
        self.prior.mkdir()

    def run_fake(self, client, measurement=20, drain=40, read_main=lambda: SHA):
        with patch.object(cont, "verify", return_value=(frozen_dataset(), prior_observations())), \
                patch.object(cont, "verify_dispatch_history"):
            return cont.run(
                ROOT,
                self.prior,
                self.output,
                "continuation-test",
                SHA,
                client,
                read_main=read_main,
                logs=lambda job, since: {"jobId": job, "stageTimings": []},
                clock=lambda: client.time,
                wait=client.wait,
                measurement_deadline=measurement,
                drain_deadline=drain,
            )

    def result(self):
        return json.loads((self.output / "continuation-observations.json").read_text())

    def test_starts_at_case03_and_never_resubmits_prior_cases(self):
        client = FakeClient()
        self.assertTrue(self.run_fake(client))
        self.assertEqual(client.posts, [case_id + ".png" for case_id in cont.CONTINUATION_CASE_IDS])
        self.assertNotIn(cont.CASE1_ID + ".png", client.posts)
        self.assertNotIn(cont.CASE2_ID + ".png", client.posts)
        self.assertTrue(self.result()["priorCase2Recovered"])
        self.assertTrue(self.result()["continuationComplete"])

    def test_censored_case_drains_same_job_then_next_case_runs(self):
        client = FakeClient("first-censored-drains")
        self.assertTrue(self.run_fake(client, measurement=3, drain=30))
        rows = self.result()["cases"]
        self.assertEqual(rows[0]["status"], "TERMINAL_NOT_OBSERVED_AT_MEASUREMENT_DEADLINE")
        self.assertEqual(rows[0]["drainStatus"], "TERMINAL_OBSERVED")
        self.assertEqual(rows[0]["drainTerminalStatus"], "SUCCEEDED")
        self.assertIn("censoredObservationMs", rows[0])
        self.assertGreaterEqual(len(client.posts), 2)
        job3_paths = [path for method, path, _ in client.calls if path == "/api/analysis/jobs/job-7"]
        self.assertGreaterEqual(len(job3_paths), 2)

    def test_prior_case02_must_drain_before_any_new_upload(self):
        client = FakeClient("prior-stuck")
        self.assertFalse(self.run_fake(client, measurement=3, drain=8))
        self.assertEqual(client.posts, [])
        self.assertFalse(self.result()["priorCase2Recovered"])
        recovery = json.loads((self.output / "prior-case2-recovery" / "recovery.json").read_text())
        self.assertEqual(recovery["originalCensoredObservationMs"], cont.CASE2_CENSORED_MS)
        self.assertEqual(recovery["status"], "RECOVERY_TERMINAL_NOT_OBSERVED")

    def test_new_case_that_cannot_drain_stops_before_next_upload(self):
        client = FakeClient("first-never-drains")
        self.assertFalse(self.run_fake(client, measurement=3, drain=12))
        self.assertEqual(len(client.posts), 1)
        rows = self.result()["cases"]
        self.assertEqual(rows[0]["drainStatus"], "TERMINAL_NOT_OBSERVED")
        self.assertTrue(all(row["status"] == "NOT_RUN" for row in rows[1:]))

    def test_duplicate_prior_job_identity_is_rejected(self):
        client = FakeClient("duplicate")
        with patch.object(cont, "verify", return_value=(frozen_dataset(), prior_observations())), \
                patch.object(cont, "verify_dispatch_history"):
            with self.assertRaisesRegex(ValueError, "duplicate accepted job identity"):
                cont.run(
                    ROOT, self.prior, self.output, "continuation-test", SHA, client,
                    read_main=lambda: SHA,
                    logs=lambda job, since: {"jobId": job, "stageTimings": []},
                    clock=lambda: client.time, wait=client.wait,
                    measurement_deadline=3, drain_deadline=20,
                )
        self.assertEqual(len(client.posts), 1)
        self.assertTrue((self.output / "observation-error.json").exists())

    def test_protocol_has_separate_measurement_and_drain_bounds(self):
        self.assertEqual(cont.MEASUREMENT_DEADLINE_SECONDS, 420)
        self.assertEqual(cont.DRAIN_DEADLINE_SECONDS, 1200)
        self.assertEqual(cont.CONTINUATION_CASE_IDS, cont.pt2.CASE_IDS[2:])

    def test_terminal_response_crossing_measurement_deadline_stays_censored(self):
        client=FakeClient("measurement-crossing")
        self.assertTrue(self.run_fake(client,measurement=3,drain=30))
        row=self.result()["cases"][0]
        self.assertEqual(row["status"],"TERMINAL_NOT_OBSERVED_AT_MEASUREMENT_DEADLINE")
        self.assertEqual(row["drainStatus"],"TERMINAL_OBSERVED")
        self.assertNotIn("acceptedToTerminalObservedMs",row)
        self.assertNotIn("terminalObservedAt",row)
        snapshot=json.loads((self.output/cont.CONTINUATION_CASE_IDS[0]/"observation.json").read_text())
        self.assertTrue(snapshot["measurementCensored"])
        self.assertTrue(snapshot["terminalObservedDuringDrain"])
        checkpoints=[json.loads(path.read_text())[0] for path in sorted(self.output.glob("ledger-*.json"))]
        censored=[row for row in checkpoints if row["status"]=="TERMINAL_NOT_OBSERVED_AT_MEASUREMENT_DEADLINE"]
        self.assertTrue(any("drainStatus" not in row for row in censored))
        self.assertEqual({row["censoredObservationMs"] for row in censored},{self.result()["cases"][0]["censoredObservationMs"]})

    def test_terminal_response_crossing_drain_bound_stops_before_next_upload(self):
        client=FakeClient("drain-crossing")
        self.assertFalse(self.run_fake(client,measurement=3,drain=30))
        self.assertEqual(len(client.posts),1)
        row=self.result()["cases"][0]
        self.assertEqual(row["drainStatus"],"TERMINAL_OBSERVED_AFTER_DRAIN_DEADLINE")
        self.assertNotIn("acceptedToTerminalObservedMs",row)

    def test_next_post_occurs_only_after_same_job_terminal_observation(self):
        client=FakeClient("first-censored-drains")
        self.assertTrue(self.run_fake(client,measurement=3,drain=30))
        posts=[i for i,call in enumerate(client.calls) if call[0]=="POST"]
        for previous,following in zip(posts,posts[1:]):
            job=f"job-{posts.index(previous)+7}"
            reads=[call for call in client.calls[previous+1:following] if call[1]=="/api/analysis/jobs/"+job]
            self.assertTrue(reads)
            case_id=cont.CONTINUATION_CASE_IDS[posts.index(previous)]
            last_response=json.loads((self.output/case_id/(reads[-1][2]+".json")).read_text())
            self.assertEqual(last_response["json"]["status"],"SUCCEEDED")

    def test_forbidden_prior_and_already_submitted_cases_cannot_use_observe_entrypoint(self):
        for case in frozen_dataset()["cases"][:2]:
            client=FakeClient()
            with self.assertRaisesRegex(ValueError,"only previously NOT_RUN"):
                cont.observe_case(ROOT,case,{"caseId":case["caseId"],"status":"NOT_RUN","uploadAttempts":0},
                                  client,"test",SHA,self.output,set())
            self.assertEqual(client.posts,[])
        case=frozen_dataset()["cases"][2]
        with self.assertRaisesRegex(ValueError,"only previously NOT_RUN"):
            cont.observe_case(ROOT,case,{"caseId":case["caseId"],"status":"ACCEPTED","uploadAttempts":1},
                              FakeClient(),"test",SHA,self.output,set())

    def test_prior_fixture_cannot_be_disguised_as_an_eligible_case(self):
        cases=frozen_dataset()["cases"]
        cases[2]["input"]=cases[0]["input"]
        client=FakeClient()
        with self.assertRaisesRegex(ValueError,"frozen truth/input"):
            cont.observe_case(ROOT,cases[2],{"caseId":cases[2]["caseId"],"status":"NOT_RUN","uploadAttempts":0},
                              client,"test",SHA,self.output,set())
        self.assertEqual(client.posts,[])

    def test_indeterminate_acceptance_or_authentication_never_submits_next_case(self):
        for mode in ["lost","malformed","auth"]:
            with self.subTest(mode=mode):
                self.output=Path(self.temp.name)/mode
                client=FakeClient(mode)
                self.assertFalse(self.run_fake(client))
                self.assertEqual(len(client.posts),1)
                self.assertFalse(self.result()["continuationComplete"])
                self.assertTrue(all(row["uploadAttempts"]==0 for row in self.result()["cases"][1:]))

    def test_recovery_identity_auth_and_readback_fail_closed_with_partial_ledger(self):
        for mode in ["prior-auth","prior-wrong-job","project-auth","wrong-project","wrong-result"]:
            with self.subTest(mode=mode):
                self.output=Path(self.temp.name)/mode
                client=FakeClient(mode)
                with self.assertRaises(ValueError): self.run_fake(client)
                self.assertEqual(client.posts,[])
                self.assertFalse(self.result()["continuationComplete"])
                self.assertFalse(self.result()["priorCase2Recovered"])
                self.assertTrue((self.output/"observation-error.json").exists())

    def test_new_job_identity_project_reuse_and_auth_fail_before_next_post(self):
        for mode in ["wrong-job","poll-auth","duplicate-project"]:
            with self.subTest(mode=mode):
                self.output=Path(self.temp.name)/mode
                client=FakeClient(mode)
                with self.assertRaises(ValueError): self.run_fake(client)
                self.assertEqual(len(client.posts),1)
                self.assertFalse(self.result()["continuationComplete"])

    def test_main_drift_between_cases_stops_before_next_post(self):
        client=FakeClient()
        with self.assertRaisesRegex(ValueError,"MAIN_DRIFT"):
            self.run_fake(client,read_main=lambda: SHA if not client.posts else "changed-main")
        self.assertEqual(len(client.posts),1)
        self.assertEqual(self.result()["cases"][1]["uploadAttempts"],0)

    def test_natural_failure_is_preserved_and_prior_censor_is_unchanged(self):
        before=prior_observations()
        client=FakeClient("natural-failure")
        self.assertTrue(self.run_fake(client))
        self.assertTrue(all(row["status"]=="FAILED" for row in self.result()["cases"]))
        recovery=json.loads((self.output/"prior-case2-recovery/recovery.json").read_text())
        self.assertEqual(recovery["originalCensoredObservationMs"],424846)
        self.assertEqual(recovery["originalMeasurementStatus"],"TERMINAL_NOT_OBSERVED")
        self.assertNotIn("acceptedToTerminalObservedMs",recovery)
        self.assertEqual(before,prior_observations())

    def test_admission_rejections_are_preserved_without_claiming_complete_baseline(self):
        client=FakeClient("rejected")
        self.assertFalse(self.run_fake(client))
        self.assertEqual(len(client.posts),8)
        self.assertTrue(all(row["status"]=="UPLOAD_REJECTED" for row in self.result()["cases"]))

    def test_recovery_only_polls_original_job_and_original_acceptance_bounds_it(self):
        client=FakeClient("prior-stuck")
        self.output.mkdir()
        prior=prior_observations()
        accepted=datetime.fromisoformat(prior["cases"][1]["acceptedResponseObservedAt"])
        self.assertFalse(cont.recover_prior_case2(
            client,self.output,prior,clock=lambda:client.time,wait=client.wait,drain_deadline=10,
            wall_clock=lambda:accepted+timedelta(seconds=6)))
        self.assertEqual(client.posts,[])
        self.assertTrue(all(call[0]=="GET" and call[1]=="/api/analysis/jobs/"+cont.CASE2_JOB_ID
                            for call in client.calls))
        self.assertLessEqual(client.time,4)


class PriorEvidenceGuardTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory=Path(self.temp.name)
        self.prior=self.directory/"prior"
        (self.prior/"cases").mkdir(parents=True)
        self.archive=self.directory/"prior.zip"
        self.run_meta=self.directory/"run.json"
        self.artifact_meta=self.directory/"artifact.json"
        self.history=self.directory/"history.json"
        self.procedure={
            "procedureVersion":cont.pt2.PROCEDURE,
            "protocolSha256":cont.pt2.digest(ROOT/cont.pt2.PROTOCOL),
            "executionBaseSha":cont.pt2.BASE,
            "runId":f"pt2-realistic-{cont.PRIOR_RUN_ID}",
            "dispatchSha":cont.PRIOR_DISPATCH_SHA,
            "candidateIdentitySha256":cont.pt2.IDENTITY,
        }
        (self.prior/"cases/procedure.json").write_text(json.dumps(self.procedure))
        (self.prior/"cases/observations.json").write_text(json.dumps(prior_observations()))
        self.run={"id":cont.PRIOR_RUN_ID,"run_attempt":1,"head_sha":cont.PRIOR_DISPATCH_SHA,
                  "status":"completed","conclusion":"failure","event":"workflow_dispatch",
                  "path":".github/workflows/gcp-target-evaluation-baseline.yml",
                  "repository":{"full_name":"siamese-lang/terraformers-platform"}}
        self.artifact={"id":cont.PRIOR_ARTIFACT_ID,"name":f"pt2-realistic-baseline-{cont.PRIOR_RUN_ID}",
                       "expired":False,"workflow_run":{"id":cont.PRIOR_RUN_ID,"head_sha":cont.PRIOR_DISPATCH_SHA}}
        self.seal()

    def seal(self):
        inventory=self.prior/"artifact-sha256.json"
        inventory.unlink(missing_ok=True)
        cont.pt2.inventory(self.prior)
        with zipfile.ZipFile(self.archive,"w") as archive:
            for path in self.prior.rglob("*"):
                if path.is_file(): archive.write(path,path.relative_to(self.prior))
        self.artifact["digest"]="sha256:"+cont.pt2.digest(self.archive)
        self.run_meta.write_text(json.dumps(self.run))
        self.artifact_meta.write_text(json.dumps(self.artifact))
        # Generated test evidence has its own expected identities. Real CLI verification uses
        # only the hard-pinned production identities; no flag can relax them.
        self.expected_inventory=cont.pt2.digest(inventory)

    def pinned(self):
        return patch.multiple(cont,PRIOR_INVENTORY_SHA256=self.expected_inventory,
                              PRIOR_ARTIFACT_DIGEST=self.artifact["digest"])

    def verify(self):
        return cont.verify(ROOT,self.prior,self.archive,self.run_meta,self.artifact_meta)

    def test_complete_prior_guard_accepts_only_the_bound_fixture(self):
        with self.pinned():
            dataset,observations=self.verify()
        self.assertEqual(len(dataset["cases"]),10)
        self.assertEqual(observations["cases"][1]["censoredObservationMs"],424846)

    def test_every_github_identity_field_is_fail_closed_before_any_http(self):
        fields=[("run","id",0),("run","run_attempt",2),("run","head_sha","wrong"),
                ("run","conclusion","success"),("run","event","push"),
                ("run","path","other.yml"),("run","status","in_progress"),
                ("run","repository",{"full_name":"other/repo"}),
                ("artifact","id",0),("artifact","name","other"),("artifact","expired",True),
                ("artifact","workflow_run",{"id":0,"head_sha":cont.PRIOR_DISPATCH_SHA}),
                ("artifact","workflow_run",{"id":cont.PRIOR_RUN_ID,"head_sha":"wrong"}),
                ("artifact","digest","sha256:wrong")]
        for category,key,value in fields:
            with self.subTest(category=category,key=key):
                target=self.run_meta if category=="run" else self.artifact_meta
                original=target.read_text()
                changed=json.loads(original);changed[key]=value
                target.write_text(json.dumps(changed))
                client=FakeClient()
                with self.pinned(),self.assertRaises(ValueError):
                    cont.run(ROOT,self.prior,self.directory/"unused","pt2-continuation-99",SHA,client,
                             archive=self.archive,run_metadata=self.run_meta,artifact_metadata=self.artifact_meta)
                self.assertEqual(client.calls,[])
                target.write_text(original)

    def test_archive_digest_cannot_be_replaced_by_matching_metadata_claim(self):
        with self.archive.open("ab") as f:f.write(b"altered archive")
        with self.pinned(),self.assertRaisesRegex(ValueError,"archive digest"):
            self.verify()

    def test_inventory_bytes_and_all_file_checksums_and_file_set_are_required(self):
        inventory=self.prior/"artifact-sha256.json"
        original=inventory.read_bytes()
        inventory.write_bytes(original+b" ")
        with self.pinned(),self.assertRaisesRegex(ValueError,"inventory identity"):self.verify()
        inventory.write_bytes(original)
        path=self.prior/"cases/procedure.json"
        content=path.read_bytes();path.write_bytes(content+b" ")
        with self.pinned(),self.assertRaisesRegex(ValueError,"file checksum"):self.verify()
        path.write_bytes(content)
        (self.prior/"unlisted").write_text("extra evidence")
        with self.pinned(),self.assertRaisesRegex(ValueError,"file set"):self.verify()

    def test_prior_case_boundaries_are_checked_independently_of_hash_authentication(self):
        for case_index,field,value in [(0,"analysisJobId","other"),(1,"censoredObservationMs",420000),
                                      (1,"status","SUCCEEDED"),(2,"uploadAttempts",1),
                                      (9,"status","SUCCEEDED")]:
            with self.subTest(case_index=case_index,field=field):
                observations=prior_observations();observations["cases"][case_index][field]=value
                (self.prior/"cases/observations.json").write_text(json.dumps(observations))
                self.seal()
                with self.pinned(),self.assertRaisesRegex(ValueError,"evidence mismatch|NOT_RUN"):
                    self.verify()

    def test_baseline_procedure_and_execution_base_cannot_be_rebound(self):
        for field in ["protocolSha256","executionBaseSha","dispatchSha","procedureVersion"]:
            changed={**self.procedure,field:"wrong"}
            (self.prior/"cases/procedure.json").write_text(json.dumps(changed));self.seal()
            with self.pinned(),self.assertRaisesRegex(ValueError,"procedure identity"):self.verify()

    def test_verified_archive_extraction_rejects_traversal_and_symlinks(self):
        for name,mode in [("../escape",0o100600),("absolute/link",0o120777)]:
            with self.subTest(name=name):
                with zipfile.ZipFile(self.archive,"w") as archive:
                    entry=zipfile.ZipInfo(name);entry.external_attr=mode<<16
                    archive.writestr(entry,"unsafe")
                self.artifact["digest"]="sha256:"+cont.pt2.digest(self.archive)
                self.artifact_meta.write_text(json.dumps(self.artifact))
                with self.pinned(),self.assertRaisesRegex(ValueError,"unsafe"):
                    cont.extract_prior_archive(self.archive,self.directory/"extracted",self.run_meta,self.artifact_meta)
                self.assertFalse((self.directory/"extracted").exists())
                self.assertFalse((self.directory/"escape").exists())

    def test_frozen_dataset_byte_change_cannot_reach_an_upload(self):
        clone=self.directory/"repo"
        shutil.copytree(ROOT/"evaluation/terraformers-realistic-v1",clone/"evaluation/terraformers-realistic-v1")
        fixture=clone/"evaluation/terraformers-realistic-v1/fixtures/pt1-03-parallel-lookup.png"
        fixture.write_bytes(b"altered")
        with self.assertRaisesRegex(ValueError,"pinned file"):
            cont._verify_frozen_inputs(clone)

    def test_durable_truth_approval_and_execution_base_remain_required(self):
        clone=self.directory/"repo"
        shutil.copytree(ROOT/"evaluation/terraformers-realistic-v1",clone/"evaluation/terraformers-realistic-v1")
        (clone/".agents/state").mkdir(parents=True)
        original=json.loads((ROOT/".agents/state/product-trust-v1.json").read_text())
        for section,key,value in [("candidate","frozenIdentitySha256","wrong"),
                                  ("liveBaselineApproval","decision","PENDING"),
                                  ("liveBaselineApproval","executionBaseSha","wrong")]:
            changed=json.loads(json.dumps(original));changed[section][key]=value
            (clone/".agents/state/product-trust-v1.json").write_text(json.dumps(changed))
            with self.subTest(section=section,key=key),self.assertRaisesRegex(ValueError,"frozen truth/live approval"):
                cont._verify_frozen_inputs(clone)
        changed=json.loads(json.dumps(original));changed["phaseExecution"]["PT-2"]["executionBaseSha"]="rebound"
        (clone/".agents/state/product-trust-v1.json").write_text(json.dumps(changed))
        with self.assertRaisesRegex(ValueError,"frozen truth/live approval"):cont._verify_frozen_inputs(clone)

    def test_existing_output_refuses_repeat_and_prior_bytes_stay_unchanged(self):
        before={str(path.relative_to(self.prior)):path.read_bytes() for path in self.prior.rglob("*") if path.is_file()}
        self.history.write_text(json.dumps(history_pages()))
        output=self.directory/"already-attempted";output.mkdir()
        client=FakeClient()
        with self.pinned(),patch.object(cont,"verify_zero_inference_exception"),self.assertRaises(FileExistsError):
            cont.run(ROOT,self.prior,output,f"pt2-continuation-{RECOVERY_RUN_ID}",SHA,client,
                     archive=self.archive,run_metadata=self.run_meta,artifact_metadata=self.artifact_meta,
                     history_path=self.history)
        self.assertEqual(client.calls,[])
        self.assertEqual(before,{str(path.relative_to(self.prior)):path.read_bytes() for path in self.prior.rglob("*") if path.is_file()})

    def test_dispatch_history_rejects_new_run_repeat_and_rerun_and_wrong_head(self):
        self.history.write_text(json.dumps(history_pages()))
        with patch.object(cont,"verify_zero_inference_exception"):
            cont.verify_dispatch_history(self.history,str(RECOVERY_RUN_ID),SHA)
        for field,value in [("run_attempt",2),("head_sha","wrong"),("head_branch","feature"),
                            ("workflow_id",0),("path","other.yml"),("event","push")]:
            with self.subTest(field=field):
                pages=history_pages();pages[0]["workflow_runs"][0][field]=value
                self.history.write_text(json.dumps(pages))
                with self.assertRaises(ValueError):cont.verify_dispatch_history(self.history,str(RECOVERY_RUN_ID),SHA)
        pages=history_pages();pages[0]["workflow_runs"]=pages[0]["workflow_runs"][:2];pages[0]["total_count"]=2
        self.history.write_text(json.dumps(pages))
        with self.assertRaisesRegex(ValueError,"history does not extend"):
            cont.verify_dispatch_history(self.history,str(RECOVERY_RUN_ID),SHA)

    def test_duplicate_archive_path_is_rejected_before_extraction(self):
        import warnings
        with warnings.catch_warnings():
            warnings.simplefilter("ignore",UserWarning)
            with zipfile.ZipFile(self.archive,"w") as archive:
                archive.writestr("duplicate", "first")
                archive.writestr("duplicate", "second")
        self.artifact["digest"]="sha256:"+cont.pt2.digest(self.archive)
        self.artifact_meta.write_text(json.dumps(self.artifact))
        with self.pinned(),self.assertRaisesRegex(ValueError,"unsafe"):
            cont.extract_prior_archive(self.archive,self.directory/"extracted",self.run_meta,self.artifact_meta)
        self.assertFalse((self.directory/"extracted").exists())


class ZeroInferenceRecoveryGuardTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.evidence = self.directory / "zero-inference"
        self.evidence.mkdir()
        for name in cont.ZERO_FILES:
            (self.evidence / name).write_text("{}")
        (self.evidence / "runtime-identity.json").write_text(json.dumps({
            "dispatchSha": cont.ZERO_DISPATCH_SHA, "executionBaseSha": cont.pt2.BASE,
            "protocolSha256": "1658b5c328fdfde9671542373309e68165d7aae6f4b973d0b781f03890b097ee",
            "productionSourceEquivalent": True, "configurationVerified": True,
        }))
        self.archive = self.directory / "source.zip"
        self.run_path = self.directory / "run.json"
        self.artifact_path = self.directory / "artifact.json"
        self.jobs_path = self.directory / "jobs.json"
        self.history = self.directory / "history.json"
        self.run_meta = history_run(cont.ZERO_RUN_ID, cont.ZERO_DISPATCH_SHA)
        self.artifact_meta = {"id": cont.ZERO_ARTIFACT_ID,
                              "name": f"pt2-realistic-continuation-{cont.ZERO_RUN_ID}",
                              "expired": False,
                              "workflow_run": {"id": cont.ZERO_RUN_ID, "head_sha": cont.ZERO_DISPATCH_SHA}}
        steps = [{"number": number, "name": name, "status": "completed", "conclusion": conclusion}
                 for number, (name, conclusion) in cont.ZERO_STEPS.items()]
        self.steps_sha = hashlib.sha256(json.dumps(steps, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
        self.jobs_meta = {"total_count": 1, "jobs": [{"id": cont.ZERO_JOB_ID,
                          "run_id": cont.ZERO_RUN_ID, "run_attempt": 1, "head_sha": cont.ZERO_DISPATCH_SHA,
                          "name": "baseline", "status": "completed", "conclusion": "failure", "steps": steps}]}
        self.seal()
        self.write_metadata()
        self.history.write_text(json.dumps(history_pages()))

    def seal(self):
        (self.evidence / "artifact-sha256.json").unlink(missing_ok=True)
        cont.pt2.inventory(self.evidence)
        self.inventory_sha = cont.pt2.digest(self.evidence / "artifact-sha256.json")
        with zipfile.ZipFile(self.archive, "w") as archive:
            for path in self.evidence.rglob("*"):
                if path.is_file(): archive.write(path, path.relative_to(self.evidence))
        self.archive_digest = "sha256:" + cont.pt2.digest(self.archive)
        self.artifact_meta["digest"] = self.archive_digest

    def write_metadata(self):
        self.run_path.write_text(json.dumps(self.run_meta))
        self.artifact_path.write_text(json.dumps(self.artifact_meta))
        self.jobs_path.write_text(json.dumps(self.jobs_meta))

    def pinned(self):
        # Only synthetic fixture checksums are patched; there is no production override input.
        return patch.multiple(cont, ZERO_ARTIFACT_DIGEST=self.archive_digest,
                              ZERO_INVENTORY_SHA256=self.inventory_sha, ZERO_JOB_STEPS_SHA256=self.steps_sha)

    def binding(self):
        return {"recovery_artifact": self.evidence, "recovery_archive": self.archive,
                "recovery_run_metadata": self.run_path, "recovery_artifact_metadata": self.artifact_path,
                "recovery_job_metadata": self.jobs_path}

    def assert_blocked_before_http(self):
        client = FakeClient()
        with self.pinned(), patch.object(cont, "verify", return_value=(frozen_dataset(), prior_observations())), \
                self.assertRaises(ValueError):
            cont.run(ROOT, self.directory / "prior", self.directory / "out",
                     f"pt2-continuation-{RECOVERY_RUN_ID}", SHA, client,
                     history_path=self.history, **self.binding())
        self.assertEqual(client.calls, [])
        self.assertEqual(client.posts, [])
        self.assertFalse((self.directory / "out").exists())

    def test_full_guard_allows_only_first_submissions_and_preserves_case02_censor(self):
        client = FakeClient()
        prior = prior_observations()
        before = json.dumps(prior, sort_keys=True)
        with self.pinned(), patch.object(cont, "verify", return_value=(frozen_dataset(), prior)):
            self.assertTrue(cont.run(ROOT, self.directory / "prior", self.directory / "out",
                f"pt2-continuation-{RECOVERY_RUN_ID}", SHA, client,
                read_main=lambda: SHA, logs=lambda job, since: {"jobId": job},
                clock=lambda: client.time, wait=client.wait, history_path=self.history, **self.binding()))
        self.assertEqual(client.posts, [case + ".png" for case in cont.CONTINUATION_CASE_IDS])
        self.assertEqual(json.dumps(prior, sort_keys=True), before)
        recovered=json.loads((self.directory / "out/prior-case2-recovery/recovery.json").read_text())
        self.assertEqual(recovered["originalCensoredObservationMs"], 424846)
        self.assertNotIn("acceptedToTerminalObservedMs", recovered)

    def test_run_and_artifact_metadata_tampering_stops_before_http(self):
        fields = [("run", "id", cont.ZERO_RUN_ID + 1), ("run", "run_attempt", 2), ("run", "run_attempt", True),
                  ("run", "head_sha", "wrong"), ("run", "head_branch", "other"),
                  ("run", "event", "push"), ("run", "path", "other.yml"),
                  ("run", "workflow_id", 0), ("run", "status", "in_progress"),
                  ("run", "conclusion", "success"), ("run", "display_title", "other"),
                  ("run", "repository", {"full_name": "other/repo"}),
                  ("artifact", "id", 0), ("artifact", "name", "other"),
                  ("artifact", "expired", True), ("artifact", "digest", "sha256:wrong"),
                  ("artifact", "workflow_run", {"id": 0, "head_sha": cont.ZERO_DISPATCH_SHA}),
                  ("artifact", "workflow_run", {"id": cont.ZERO_RUN_ID, "head_sha": "wrong"})]
        for category, field, value in fields:
            with self.subTest(category=category, field=field):
                target = self.run_path if category == "run" else self.artifact_path
                original = target.read_text(); changed = json.loads(original); changed[field] = value
                target.write_text(json.dumps(changed)); self.assert_blocked_before_http(); target.write_text(original)

    def test_job_identity_and_set_tampering_stops_before_http(self):
        original = self.jobs_path.read_text()
        for field, value in [("id", 0), ("run_id", 0), ("run_attempt", 2), ("run_attempt", True), ("head_sha", "wrong"),
                             ("name", "other"), ("status", "in_progress"), ("conclusion", "success")]:
            with self.subTest(field=field):
                changed=json.loads(original);changed["jobs"][0][field]=value
                self.jobs_path.write_text(json.dumps(changed));self.assert_blocked_before_http()
        for jobs in [[], self.jobs_meta["jobs"] * 2]:
            changed={"total_count":len(jobs),"jobs":jobs};self.jobs_path.write_text(json.dumps(changed))
            self.assert_blocked_before_http()
        self.jobs_path.write_text(original)

    def test_every_bound_job_step_and_extra_step_is_fail_closed(self):
        original=self.jobs_path.read_text()
        for index in range(len(self.jobs_meta["jobs"][0]["steps"])):
            for field,value in [("number",999),("name","other"),("status","in_progress"),("conclusion","success" if index in (1,2,3) else "skipped")]:
                with self.subTest(index=index,field=field):
                    changed=json.loads(original);changed["jobs"][0]["steps"][index][field]=value
                    self.jobs_path.write_text(json.dumps(changed));self.assert_blocked_before_http()
        for mode in ["missing", "duplicate", "extra"]:
            changed=json.loads(original);steps=changed["jobs"][0]["steps"]
            if mode=="missing":steps.pop(0)
            elif mode=="duplicate":steps.append(steps[0])
            else:steps.append({"number":42,"name":"unexpected upload","status":"completed","conclusion":"success"})
            self.jobs_path.write_text(json.dumps(changed));self.assert_blocked_before_http()
        self.jobs_path.write_text(original)

    def test_actual_archive_bytes_and_inventory_file_hashes_are_required(self):
        original=self.archive.read_bytes();self.archive.write_bytes(original+b"altered")
        self.assert_blocked_before_http();self.archive.write_bytes(original)
        inventory=self.evidence/"artifact-sha256.json";original=inventory.read_bytes()
        inventory.write_bytes(original+b" ");self.assert_blocked_before_http();inventory.write_bytes(original)
        path=self.evidence/"retained-configuration.txt";original=path.read_bytes()
        path.write_bytes(original+b"altered");self.assert_blocked_before_http();path.write_bytes(original)

    def test_case_recovery_or_submission_files_and_even_empty_case_directory_are_rejected(self):
        for name in ["cases/submission-started.json","recovery.json","accepted-job.json","model-result.json"]:
            with self.subTest(name=name):
                path=self.evidence/name;path.parent.mkdir(exist_ok=True);path.write_text("{}")
                self.seal();self.write_metadata();self.assert_blocked_before_http();path.unlink()
                if path.parent!=self.evidence:path.parent.rmdir()
        (self.evidence/"cases").mkdir();self.seal();self.write_metadata();self.assert_blocked_before_http()

    def test_extraction_rejects_paths_links_duplicates_and_unexpected_entries(self):
        import warnings
        for name,mode,duplicate in [("../escape",0o100600,False),("runtime-identity.json",0o120777,False),
                                    ("cases/",0o040755,False),("runtime-identity.json",0o100600,True)]:
            with self.subTest(name=name,mode=mode,duplicate=duplicate):
                with warnings.catch_warnings():
                    warnings.simplefilter("ignore",UserWarning)
                    with zipfile.ZipFile(self.archive,"w") as archive:
                        entry=zipfile.ZipInfo(name);entry.external_attr=mode<<16;archive.writestr(entry,"unsafe")
                        if duplicate:archive.writestr(entry,"again")
                self.archive_digest="sha256:"+cont.pt2.digest(self.archive)
                self.artifact_meta["digest"]=self.archive_digest;self.write_metadata()
                with self.pinned(),self.assertRaises(ValueError):
                    cont.extract_recovery_archive(self.archive,self.directory/"extracted",self.run_path,self.artifact_path,self.jobs_path)
                self.assertFalse((self.directory/"extracted").exists())
                self.assertFalse((self.directory/"escape").exists())

    def test_missing_proof_cannot_exempt_run(self):
        for key in self.binding():
            kwargs=self.binding();kwargs[key]=None
            with self.subTest(key=key),self.pinned(),self.assertRaises(ValueError):
                cont.verify_dispatch_history(self.history,str(RECOVERY_RUN_ID),SHA,**kwargs)

    def test_complete_repository_pagination_and_cross_workflow_runs_are_supported(self):
        pages=history_pages([history_run(1,"other","unrelated workflow")]);pages[0]["workflow_runs"][-1]["workflow_id"]=0
        runs=pages[0]["workflow_runs"];pages=[{"total_count":len(runs),"workflow_runs":runs[:2]},
                                         {"total_count":len(runs),"workflow_runs":runs[2:]}]
        self.history.write_text(json.dumps(pages))
        with self.pinned():cont.verify_dispatch_history(self.history,str(RECOVERY_RUN_ID),SHA,**self.binding())

    def test_incomplete_stale_duplicate_or_mixed_page_history_blocks(self):
        examples=[]
        pages=history_pages();pages[0]["total_count"]+=1;examples.append(pages)
        pages=history_pages();pages[0]["workflow_runs"].pop(0);pages[0]["total_count"]-=1;examples.append(pages)
        pages=history_pages();pages[0]["workflow_runs"].pop(1);pages[0]["total_count"]-=1;examples.append(pages)
        pages=history_pages();pages[0]["workflow_runs"].append(pages[0]["workflow_runs"][0]);pages[0]["total_count"]+=1;examples.append(pages)
        examples.append([{"total_count":3,"workflow_runs":history_pages()[0]["workflow_runs"][:1]},
                         {"total_count":4,"workflow_runs":history_pages()[0]["workflow_runs"][1:]}])
        examples.append([])
        examples.append([None])
        for pages in examples:
            self.history.write_text(json.dumps(pages));self.assert_blocked_before_http()

    def test_every_non_exempt_continuation_including_failed_or_other_workflow_blocks(self):
        for status,workflow in [("failure",cont.WORKFLOW_ID),("cancelled",cont.WORKFLOW_ID),
                                ("success",cont.WORKFLOW_ID),("failure",0)]:
            earlier=history_run(cont.ZERO_RUN_ID-1,"other");earlier.update(conclusion=status,workflow_id=workflow)
            self.history.write_text(json.dumps(history_pages([earlier])));self.assert_blocked_before_http()
        self.history.write_text(json.dumps(history_pages([history_run(RECOVERY_RUN_ID+1,"other")])));self.assert_blocked_before_http()

    def test_source_id_cannot_be_rerun_and_a_second_recovery_cannot_bypass_with_fresh_id(self):
        for run_id in [cont.ZERO_RUN_ID,cont.PRIOR_RUN_ID]:
            with self.subTest(run_id=run_id),self.assertRaisesRegex(ValueError,"cannot be rerun"):
                cont.verify_dispatch_history(self.history,str(run_id),cont.ZERO_DISPATCH_SHA,**self.binding())
        pages=history_pages([history_run(RECOVERY_RUN_ID+1,SHA)])
        self.history.write_text(json.dumps(pages))
        with self.pinned(),self.assertRaisesRegex(ValueError,"prior continuation dispatch"):
            cont.verify_dispatch_history(self.history,str(RECOVERY_RUN_ID+1),SHA,**self.binding())

    def test_exception_history_source_binding_is_required(self):
        for field,value in [("run_attempt",2),("head_sha","wrong"),("conclusion","success"),
                            ("display_title","other"),("repository",{"full_name":"other/repo"})]:
            pages=history_pages();pages[0]["workflow_runs"][1][field]=value
            self.history.write_text(json.dumps(pages));self.assert_blocked_before_http()


if __name__ == "__main__":
    unittest.main()
