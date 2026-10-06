"""Deterministic PT-2 continuation contracts; no live network/model/runtime calls."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

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
        elif path.endswith("/terraform/main.tf"):
            project_id = int(path.split("/")[3])
            latest = cont.CASE2_JOB_ID if project_id == cont.CASE2_PROJECT_ID else f"job-{project_id}"
            body = {
                "projectId": project_id,
                "latestAnalysisJobId": latest,
                "latestResultObjectKey": "prior-result" if project_id == cont.CASE2_PROJECT_ID else "result-" + latest,
                "content": 'resource "aws_s3_bucket" "example" {}\n',
            }
        elif path.startswith("/api/projects/"):
            project_id = int(path.split("/")[3])
            latest = cont.CASE2_JOB_ID if project_id == cont.CASE2_PROJECT_ID else f"job-{project_id}"
            body = {
                "projectId": project_id,
                "latestAnalysisJobId": latest,
                "analysisStatus": "SUCCEEDED",
            }
        elif path == "/actuator/prometheus":
            body = "metrics"

        response = {
            "method": method,
            "path": path,
            "httpStatus": status,
            "transportExitCode": transport,
            "observedAt": f"2026-10-06T00:00:{self.time:02}Z",
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

    def run_fake(self, client, measurement=20, drain=40):
        with patch.object(cont, "verify", return_value=(frozen_dataset(), prior_observations())):
            return cont.run(
                ROOT,
                self.prior,
                self.output,
                "continuation-test",
                SHA,
                client,
                read_main=lambda: SHA,
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
        with patch.object(cont, "verify", return_value=(frozen_dataset(), prior_observations())):
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


if __name__ == "__main__":
    unittest.main()
