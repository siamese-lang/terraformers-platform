"""Deterministic orchestration contracts; never call kubectl, GCP, or a model."""
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("pt2", Path(__file__).with_name("pt2-realistic-baseline.py"))
pt2 = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(pt2)
ROOT = Path(__file__).resolve().parents[2]
SHA = "3caae454661d21d84c3e469a7d76aff5633d5b26"


class FakePod:
    def __init__(self, timeout_case=None, lost_transport=False, mismatch_case=None):
        self.calls = []
        self.raw = {}
        self.timeout_case = timeout_case
        self.lost_transport = lost_transport
        self.mismatch_case = mismatch_case
        self.cases = {c["caseId"]: c for c in pt2.verify(ROOT)["cases"]}

    def __call__(self, command):
        if command[0] == "env":
            env = dict(value.split("=", 1) for value in command[1:1 + len(pt2.PROFILE) + 6])
            case_id = env["EVALUATION_CASE_ID"]
            self.calls.append(case_id)
            if self.lost_transport:
                raise subprocess.TimeoutExpired(command, 460)
            if case_id == self.timeout_case:
                return subprocess.CompletedProcess(command, 124, "", "deadline")
            case = self.cases[case_id]
            trace = {
                "caseId": case_id, "runId": env["EVALUATION_RUN_ID"], "input": case["input"],
                "configuration": {"configurationFingerprint": "fixed"},
                "factExtraction": {"status": "FAIL", "failures": [{"category": "PROVIDER_ERROR"}]},
                "retrieval": {"evidence": None}, "generation": {"evidence": None},
            }
            if case_id == self.mismatch_case:
                trace["input"] = {**case["input"], "sha256": "wrong"}
            self.raw[env["EVALUATION_OUTPUT"]] = json.dumps({
                "datasetVersion": pt2.DATASET, "runId": env["EVALUATION_RUN_ID"],
                "configuration": trace["configuration"], "traces": [trace],
            })
            return subprocess.CompletedProcess(command, 0, "completed", "")
        if command[0] == "cat":
            return subprocess.CompletedProcess(command, 0 if command[1] in self.raw else 1,
                                               self.raw.get(command[1], ""), "missing")
        raise AssertionError("unexpected command: " + command[0])


class BoundedBaselineTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.output = Path(self.temp.name) / "cases"

    def run_fake(self, pod):
        return pt2.run(ROOT, self.output, "test-run", SHA, execute=pod, read_main=lambda: SHA)

    def test_ten_single_calls_preserve_failed_traces_without_claiming_trust(self):
        pod = FakePod()
        self.assertTrue(self.run_fake(pod))
        self.assertEqual(pod.calls, list(pt2.CASE_IDS))
        raw = json.loads((self.output / "raw.json").read_text())
        self.assertEqual(len(raw["traces"]), 10)
        self.assertTrue(all(t["factExtraction"]["status"] == "FAIL" for t in raw["traces"]))
        self.assertEqual(json.loads((self.output / "procedure.json").read_text())["persistedTrustStatus"], "NOT_MEASURED")

    def test_timeout_is_preserved_and_not_replaced_with_success(self):
        pod = FakePod(timeout_case=pt2.CASE_IDS[1])
        self.assertFalse(self.run_fake(pod))
        self.assertEqual(pod.calls, list(pt2.CASE_IDS))
        attempts = json.loads((self.output / "attempts.json").read_text())
        self.assertFalse(attempts["allTenRawTraces"])
        self.assertTrue(attempts["cases"][1]["timedOut"])
        self.assertFalse(attempts["cases"][1]["rawAvailable"])
        self.assertEqual(len(json.loads((self.output / "raw.json").read_text())["traces"]), 9)

    def test_existing_attempt_directory_refuses_a_second_run(self):
        pod = FakePod()
        self.run_fake(pod)
        with self.assertRaises(FileExistsError):
            self.run_fake(pod)
        self.assertEqual(len(pod.calls), 10)

    def test_lost_transport_stops_without_retry_and_keeps_started_evidence(self):
        pod = FakePod(lost_transport=True)
        with self.assertRaises(subprocess.TimeoutExpired):
            self.run_fake(pod)
        self.assertEqual(pod.calls, [pt2.CASE_IDS[0]])
        attempt = json.loads((self.output / pt2.CASE_IDS[0] / "attempt.json").read_text())
        self.assertEqual(attempt["status"], "INDETERMINATE_TRANSPORT")
        self.assertFalse(attempt["rerunPermitted"])

    def test_main_drift_stops_before_next_inference(self):
        pod = FakePod()
        values = iter((SHA, "different-main"))
        with self.assertRaisesRegex(ValueError, "MAIN_DRIFT"):
            pt2.run(ROOT, self.output, "test-run", SHA, execute=pod, read_main=lambda: next(values))
        self.assertEqual(pod.calls, [pt2.CASE_IDS[0]])

    def test_wrong_raw_fixture_identity_stops_batch(self):
        pod = FakePod(mismatch_case=pt2.CASE_IDS[0])
        with self.assertRaisesRegex(ValueError, "trace identity mismatch"):
            self.run_fake(pod)
        self.assertEqual(pod.calls, [pt2.CASE_IDS[0]])
        self.assertTrue((self.output / pt2.CASE_IDS[0] / "raw.json").exists())

    def test_tampered_frozen_fixture_prevents_any_invocation(self):
        root = Path(self.temp.name) / "tampered"
        directory = root / "evaluation" / pt2.DATASET
        shutil.copytree(ROOT / "evaluation" / pt2.DATASET, directory)
        fixture = directory / "fixtures/pt1-01-serverless-portal.png"
        fixture.write_bytes(fixture.read_bytes() + b"tampered")
        pod = FakePod()
        with self.assertRaisesRegex(ValueError, "pinned file mismatch"):
            pt2.run(root, self.output, "test-run", SHA, execute=pod, read_main=lambda: SHA)
        self.assertEqual(pod.calls, [])

    def test_changed_procedure_prevents_inference_even_when_fixtures_match(self):
        root = Path(self.temp.name) / "changed-procedure"
        shutil.copytree(ROOT / "evaluation" / pt2.DATASET, root / "evaluation" / pt2.DATASET)
        for relative in (".agents/state/product-trust-v1.json", "docs/evaluation/product-trust-pt-2-measurement-protocol.md"):
            target = root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy(ROOT / relative, target)
        protocol = root / "docs/evaluation/product-trust-pt-2-measurement-protocol.md"
        protocol.write_text(protocol.read_text() + "changed after freeze\n")
        pod = FakePod()
        with self.assertRaisesRegex(ValueError, "procedure changed"):
            pt2.run(root, self.output, "test-run", SHA, execute=pod, read_main=lambda: SHA)
        self.assertEqual(pod.calls, [])


if __name__ == "__main__":
    unittest.main()
