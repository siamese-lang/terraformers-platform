import importlib.util
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).parents[2] / "scripts/checks/ci_changed_scope.py"
SPEC = importlib.util.spec_from_file_location("ci_changed_scope", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(MODULE)


class ChangedScopeTest(unittest.TestCase):
    def write_workflow(self, root, relative_path, automatic=True):
        path = root / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        trigger = "  workflow_dispatch:\n"
        if automatic:
            trigger += "  pull_request:\n"
        path.write_text(
            "name: test\n\non:\n" + trigger + "\njobs:\n  noop:\n    runs-on: ubuntu-latest\n    steps:\n      - run: true\n",
            encoding="utf-8",
        )

    def flags(self, path):
        return MODULE.classify([path])

    def test_current_repository_has_only_allowlisted_automatic_pr_workflows(self):
        root = Path(__file__).parents[2]
        self.assertEqual(MODULE.verify_workflow_policy(root), MODULE.AUTO_PR_WORKFLOW_ALLOWLIST)

    def test_policy_rejects_unapproved_automatic_pr_workflow(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for workflow in MODULE.AUTO_PR_WORKFLOW_ALLOWLIST:
                self.write_workflow(root, workflow, automatic=True)
            self.write_workflow(root, ".github/workflows/new-milestone-verifier.yml", automatic=True)

            with self.assertRaisesRegex(RuntimeError, "unapproved automatic PR workflows"):
                MODULE.verify_workflow_policy(root)

    def test_policy_requires_explicit_allowlist_change_to_remove_core_pr_ci(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for workflow in MODULE.AUTO_PR_WORKFLOW_ALLOWLIST:
                self.write_workflow(root, workflow, automatic=not workflow.endswith("frontend-ci.yml"))

            with self.assertRaisesRegex(RuntimeError, "allowlisted workflows no longer automatic"):
                MODULE.verify_workflow_policy(root)

    def test_docs_state_only_skips_expensive_workflows(self):
        flags = self.flags("docs/AI_PROJECT_STATE.md")
        self.assertFalse(any(flags["m1"].values()))
        self.assertFalse(any(flags["m2-baseline"].values()))
        self.assertFalse(any(flags["m2-authenticated"].values()))
        self.assertFalse(any(flags["terraform-static"].values()))
        self.assertFalse(any(flags["aws-runtime-package"].values()))

    def test_frontend_only_runs_m1_frontend_and_boundary(self):
        flags = self.flags("frontend/src/App.js")["m1"]
        self.assertTrue(flags["frontend_regression"])
        self.assertTrue(flags["boundary_contract"])
        self.assertFalse(flags["backend_regression"])
        self.assertFalse(flags["mariadb_regression"])

    def test_portable_overlay_only_runs_persistent_runtime(self):
        flags = self.flags("infra/kubernetes/overlays/portable-persistent/mariadb.yaml")
        self.assertTrue(flags["m2-persistent"]["portable_persistent_runtime"])
        self.assertFalse(flags["aws-runtime-package"]["aws_runtime_deployment_package"])

    def test_authenticated_overlay_only_runs_authenticated_runtime(self):
        flags = self.flags("infra/kubernetes/overlays/portable-authenticated/jwks-server.yaml")
        self.assertTrue(flags["m2-authenticated"]["authenticated_identity_parity"])
        self.assertFalse(flags["m2-persistent"]["portable_persistent_runtime"])
        self.assertFalse(flags["m2-baseline"]["kind_local_stub_baseline"])
        self.assertFalse(flags["aws-runtime-package"]["aws_runtime_deployment_package"])

    def test_authenticated_evidence_doc_skips_authenticated_runtime(self):
        flags = self.flags("docs/verification/m2-authenticated-identity-parity.md")
        self.assertFalse(flags["m2-authenticated"]["authenticated_identity_parity"])

    def test_authenticated_http_helper_runs_authenticated_runtime(self):
        flags = self.flags("scripts/checks/lib/http-status.sh")
        self.assertTrue(flags["m2-authenticated"]["authenticated_identity_parity"])

    def test_base_manifest_runs_all_runtime_dependents(self):
        flags = self.flags("infra/kubernetes/base/backend-deployment.yaml")
        self.assertTrue(flags["m2-persistent"]["portable_persistent_runtime"])
        self.assertTrue(flags["m2-authenticated"]["authenticated_identity_parity"])
        self.assertTrue(flags["m1"]["runtime_contract"])
        self.assertTrue(flags["aws-runtime-package"]["aws_runtime_deployment_package"])

    def test_aws_overlay_only_runs_aws_package_and_m1_runtime(self):
        flags = self.flags("infra/kubernetes/overlays/aws-runtime-template/kustomization.yaml")
        self.assertTrue(flags["aws-runtime-package"]["aws_runtime_deployment_package"])
        self.assertTrue(flags["m1"]["runtime_contract"])
        self.assertFalse(flags["m2-persistent"]["portable_persistent_runtime"])

    def test_unrelated_backend_test_skips_persistent_runtime(self):
        flags = self.flags("backend/src/test/java/example/UnrelatedTest.java")
        self.assertFalse(flags["m2-persistent"]["portable_persistent_runtime"])

    def test_backend_pom_runs_backend_mariadb_and_persistent_runtime(self):
        flags = self.flags("backend/pom.xml")
        self.assertTrue(flags["m1"]["backend_regression"])
        self.assertTrue(flags["m1"]["mariadb_regression"])
        self.assertTrue(flags["m2-persistent"]["portable_persistent_runtime"])
        self.assertTrue(flags["m2-authenticated"]["authenticated_identity_parity"])

    def test_gcp_apply_workflow_runs_terraform_static_verification(self):
        flags = self.flags(".github/workflows/gcp-target-terraform-apply.yml")
        self.assertTrue(flags["terraform-static"]["terraform_static_verification"])

    def test_gcp_opensearch_workflow_runs_terraform_static_verification(self):
        flags = self.flags(".github/workflows/gcp-target-opensearch-readiness.yml")
        self.assertTrue(flags["terraform-static"]["terraform_static_verification"])

    def test_gcp_corpus_ingestion_workflow_runs_terraform_static_verification(self):
        flags = self.flags(".github/workflows/gcp-target-corpus-ingestion.yml")
        self.assertTrue(flags["terraform-static"]["terraform_static_verification"])

    def test_gcp_serving_smoke_workflow_runs_terraform_static_verification(self):
        flags = self.flags(".github/workflows/gcp-target-serving-path-smoke.yml")
        self.assertTrue(flags["terraform-static"]["terraform_static_verification"])

    def test_gcp_evaluation_baseline_workflow_runs_terraform_static_verification(self):
        flags = self.flags(".github/workflows/gcp-target-evaluation-baseline.yml")
        self.assertTrue(flags["terraform-static"]["terraform_static_verification"])

    def test_gcp_delivery_gate_runs_terraform_static_verification(self):
        flags = self.flags("scripts/deploy/gcp_target_plan_gate.py")
        self.assertTrue(flags["terraform-static"]["terraform_static_verification"])

    def test_deleted_base_manifest_is_collected_and_classified(self):
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            subprocess.run(["git", "init", "-q"], cwd=repository, check=True)
            subprocess.run(["git", "config", "user.name", "CI Scope Test"], cwd=repository, check=True)
            subprocess.run(["git", "config", "user.email", "ci-scope@example.test"], cwd=repository, check=True)
            manifest = repository / "infra/kubernetes/base/backend-deployment.yaml"
            manifest.parent.mkdir(parents=True)
            manifest.write_text("kind: Deployment\n", encoding="utf-8")
            subprocess.run(["git", "add", "."], cwd=repository, check=True)
            subprocess.run(["git", "commit", "-qm", "add base manifest"], cwd=repository, check=True)
            base = subprocess.run(
                ["git", "rev-parse", "HEAD"], cwd=repository, check=True, text=True, stdout=subprocess.PIPE
            ).stdout.strip()
            manifest.unlink()
            subprocess.run(["git", "add", "-u"], cwd=repository, check=True)
            subprocess.run(["git", "commit", "-qm", "delete base manifest"], cwd=repository, check=True)
            head = subprocess.run(
                ["git", "rev-parse", "HEAD"], cwd=repository, check=True, text=True, stdout=subprocess.PIPE
            ).stdout.strip()

            changed = MODULE.changed_paths(base, head, cwd=repository)
            self.assertEqual(changed, ["infra/kubernetes/base/backend-deployment.yaml"])
            flags = MODULE.classify(changed)
            self.assertTrue(flags["m1"]["runtime_contract"])
            self.assertTrue(flags["m2-persistent"]["portable_persistent_runtime"])
            self.assertTrue(flags["aws-runtime-package"]["aws_runtime_deployment_package"])


if __name__ == "__main__":
    unittest.main()
