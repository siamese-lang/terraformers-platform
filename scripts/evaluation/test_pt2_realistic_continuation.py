"""Deterministic PT-2 continuation contracts; no live network/model/runtime calls."""
import importlib.util
from datetime import datetime, timedelta, timezone
import json
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
        history={"id":99,"run_attempt":1,"head_sha":SHA,"head_branch":"main","event":"workflow_dispatch",
                 "display_title":cont.CONTINUATION_RUN_TITLE}
        self.history.write_text(json.dumps([{"workflow_runs":[history,{"id":cont.PRIOR_RUN_ID,"head_sha":cont.PRIOR_DISPATCH_SHA}]}]))
        output=self.directory/"already-attempted";output.mkdir()
        client=FakeClient()
        with self.pinned(),self.assertRaises(FileExistsError):
            cont.run(ROOT,self.prior,output,"pt2-continuation-99",SHA,client,
                     archive=self.archive,run_metadata=self.run_meta,artifact_metadata=self.artifact_meta,
                     history_path=self.history)
        self.assertEqual(client.calls,[])
        self.assertEqual(before,{str(path.relative_to(self.prior)):path.read_bytes() for path in self.prior.rglob("*") if path.is_file()})

    def test_dispatch_history_rejects_new_run_repeat_and_rerun_and_wrong_head(self):
        current={"id":99,"run_attempt":1,"head_sha":SHA,"head_branch":"main", "event":"workflow_dispatch",
                 "display_title":cont.CONTINUATION_RUN_TITLE}
        prior={"id":cont.PRIOR_RUN_ID,"head_sha":cont.PRIOR_DISPATCH_SHA}
        self.history.write_text(json.dumps([{"workflow_runs":[current,prior]}]))
        cont.verify_dispatch_history(self.history,"99",SHA)
        for runs in [[{**current,"run_attempt":2}], [{**current,"head_sha":"wrong"}],
                     [{**current,"head_branch":"feature"}], [],
                     [{**current,"id":98},current]]:
            with self.subTest(runs=runs):
                self.history.write_text(json.dumps([{"workflow_runs":runs+[prior]}]))
                with self.assertRaises(ValueError):cont.verify_dispatch_history(self.history,"99",SHA)
        self.history.write_text(json.dumps([{"workflow_runs":[current]}]))
        with self.assertRaisesRegex(ValueError,"history does not extend"):
            cont.verify_dispatch_history(self.history,"99",SHA)

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


if __name__ == "__main__":
    unittest.main()
