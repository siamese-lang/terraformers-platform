import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).parents[2] / "scripts/checks/ci_changed_scope.py"
SPEC = importlib.util.spec_from_file_location("ci_changed_scope", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(MODULE)


class ChangedScopeTest(unittest.TestCase):
    def flags(self, path):
        return MODULE.classify([path])

    def test_docs_state_only_skips_expensive_workflows(self):
        flags = self.flags("docs/AI_PROJECT_STATE.md")
        self.assertFalse(any(flags["m1"].values()))
        self.assertFalse(any(flags["m2-baseline"].values()))
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

    def test_base_manifest_runs_all_runtime_dependents(self):
        flags = self.flags("infra/kubernetes/base/backend-deployment.yaml")
        self.assertTrue(flags["m2-persistent"]["portable_persistent_runtime"])
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


if __name__ == "__main__":
    unittest.main()
