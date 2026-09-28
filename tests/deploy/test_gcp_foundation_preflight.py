from __future__ import annotations

import argparse
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parents[2] / "scripts" / "deploy" / "gcp_foundation_preflight.py"
SPEC = importlib.util.spec_from_file_location("gcp_foundation_preflight", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
preflight = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = preflight
SPEC.loader.exec_module(preflight)


def quotas(values: dict[str, tuple[float, float]]) -> dict:
    return {
        "quotas": [
            {"metric": metric, "limit": limit, "usage": usage}
            for metric, (limit, usage) in values.items()
        ]
    }


class GcpFoundationPreflightTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write(self, name: str, value) -> Path:
        path = self.root / name
        path.write_text(json.dumps(value), encoding="utf-8")
        return path

    def args(self, **overrides) -> argparse.Namespace:
        payloads = {
            "project_json": self.write(
                "project.json",
                {
                    "projectId": "terraformers-platform",
                    "projectNumber": "21647422237",
                    "lifecycleState": "ACTIVE",
                },
            ),
            "billing_json": self.write(
                "billing.json",
                {"projectId": "terraformers-platform", "billingEnabled": True},
            ),
            "global_quota_json": self.write(
                "global.json",
                quotas(
                    {
                        "CPUS_ALL_REGIONS": (12, 0),
                        "IN_USE_ADDRESSES": (4, 0),
                    }
                ),
            ),
            "region_json": self.write(
                "region.json",
                quotas(
                    {
                        "CPUS": (32, 0),
                        "E2_CPUS": (8, 0),
                        "INSTANCES": (8, 0),
                        "DISKS_TOTAL_GB": (2048, 0),
                        "IN_USE_ADDRESSES": (4, 0),
                    }
                ),
            ),
            "machine_json": self.write(
                "machine.json",
                {
                    "name": "e2-standard-2",
                    "guestCpus": 2,
                    "memoryMb": 8192,
                    "zone": "https://www.googleapis.com/compute/v1/projects/terraformers-platform/zones/asia-northeast3-a",
                },
            ),
            "gke_server_json": self.write(
                "gke.json",
                {"defaultClusterVersion": "1.35.8-gke.1225000"},
            ),
            "services_json": self.write(
                "services.json",
                [
                    {"config": {"name": name}, "state": "ENABLED"}
                    for name in sorted(preflight.REQUIRED_APIS)
                ],
            ),
            "clusters_json": self.write("clusters.json", []),
            "networks_json": self.write("networks.json", [{"name": "default"}]),
            "subnets_json": self.write(
                "subnets.json",
                [{"name": "default", "region": "https://www.googleapis.com/compute/v1/projects/terraformers-platform/regions/asia-northeast3"}],
            ),
            "service_accounts_json": self.write(
                "service-accounts.json",
                [{"email": "terraformers-plan@terraformers-platform.iam.gserviceaccount.com"}],
            ),
            "instances_json": self.write("instances.json", []),
            "output_json": self.root / "result.json",
            "output_md": self.root / "result.md",
        }
        payloads.update(overrides)
        return argparse.Namespace(**payloads)

    def test_accepts_expected_mutable_preflight(self) -> None:
        result = preflight.validate(self.args())
        self.assertEqual(result["status"], "passed")
        self.assertTrue(result["billing_enabled"])
        self.assertFalse(result["free_trial_credit_balance_checked"])
        self.assertEqual(result["duplicate_runtime_findings"], [])

    def test_rejects_insufficient_regional_e2_cpu(self) -> None:
        region = self.write(
            "low-region.json",
            quotas(
                {
                    "CPUS": (32, 0),
                    "E2_CPUS": (8, 7),
                    "INSTANCES": (8, 0),
                    "DISKS_TOTAL_GB": (2048, 0),
                    "IN_USE_ADDRESSES": (4, 0),
                }
            ),
        )
        with self.assertRaises(preflight.PreflightError):
            preflight.validate(self.args(region_json=region))

    def test_rejects_disabled_billing(self) -> None:
        billing = self.write(
            "billing-off.json",
            {"projectId": "terraformers-platform", "billingEnabled": False},
        )
        with self.assertRaises(preflight.PreflightError):
            preflight.validate(self.args(billing_json=billing))

    def test_rejects_duplicate_target_cluster(self) -> None:
        clusters = self.write(
            "duplicate-cluster.json",
            [{"name": "terraformers-target", "location": "asia-northeast3-a"}],
        )
        with self.assertRaises(preflight.PreflightError):
            preflight.validate(self.args(clusters_json=clusters))

    def test_rejects_missing_required_api(self) -> None:
        enabled = sorted(preflight.REQUIRED_APIS - {"container.googleapis.com"})
        services = self.write(
            "missing-api.json",
            [{"config": {"name": name}, "state": "ENABLED"} for name in enabled],
        )
        with self.assertRaises(preflight.PreflightError):
            preflight.validate(self.args(services_json=services))


if __name__ == "__main__":
    unittest.main()
