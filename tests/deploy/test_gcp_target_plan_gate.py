from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parents[2] / "scripts" / "deploy" / "gcp_target_plan_gate.py"
SPEC = importlib.util.spec_from_file_location("gcp_target_plan_gate", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
gate = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = gate
SPEC.loader.exec_module(gate)


BACKEND_PRINCIPAL = (
    "principal://iam.googleapis.com/projects/21647422237/locations/global/workloadIdentityPools/"
    "terraformers-platform.svc.id.goog/subject/ns/terraformers-target/sa/terraformers-backend"
)


def resource(address: str, resource_type: str, actions: list[str], after: dict, before: dict | None = None) -> dict:
    return {
        "address": address,
        "mode": "managed",
        "type": resource_type,
        "change": {
            "actions": actions,
            "before": before,
            "after": after,
        },
    }


def node_config() -> dict:
    return {
        "machine_type": "e2-standard-2",
        "disk_type": "pd-standard",
        "disk_size_gb": 30,
        "service_account": "terraformers-gke-nodes@terraformers-platform.iam.gserviceaccount.com",
        "labels": {"terraformers-runtime": "target"},
        "linux_node_config": [{"sysctls": {"vm.max_map_count": "262144"}}],
        "workload_metadata_config": [{"mode": "GKE_METADATA"}],
    }


def foundation_plan() -> dict:
    changes = [
        resource(
            "google_compute_network.target",
            "google_compute_network",
            ["create"],
            {"name": "terraformers-target", "auto_create_subnetworks": False},
        ),
        resource(
            "google_compute_subnetwork.target",
            "google_compute_subnetwork",
            ["create"],
            {"name": "terraformers-target", "ip_cidr_range": "10.40.0.0/20", "region": "asia-northeast3"},
        ),
        resource(
            "google_container_cluster.target",
            "google_container_cluster",
            ["create"],
            {
                "name": "terraformers-target",
                "location": "asia-northeast3-a",
                "remove_default_node_pool": True,
                "initial_node_count": 1,
                "deletion_protection": False,
                "node_config": [
                    {
                        "service_account": "terraformers-gke-nodes@terraformers-platform.iam.gserviceaccount.com",
                        "oauth_scopes": ["https://www.googleapis.com/auth/cloud-platform"],
                    }
                ],
                "workload_identity_config": [{"workload_pool": "terraformers-platform.svc.id.goog"}],
                "addons_config": [{"gce_persistent_disk_csi_driver_config": [{"enabled": True}]}],
                "release_channel": [{"channel": "REGULAR"}],
            },
        ),
        resource(
            "google_container_node_pool.target",
            "google_container_node_pool",
            ["create"],
            {
                "name": "terraformers-target-primary",
                "location": "asia-northeast3-a",
                "node_count": 1,
                "node_config": [node_config()],
            },
        ),
        resource(
            "google_project_iam_member.backend_service_usage",
            "google_project_iam_member",
            ["create"],
            {"role": "roles/serviceusage.serviceUsageConsumer", "member": BACKEND_PRINCIPAL},
        ),
        resource(
            "google_project_iam_member.backend_vertex",
            "google_project_iam_member",
            ["create"],
            {"role": "roles/aiplatform.user", "member": BACKEND_PRINCIPAL},
        ),
        resource(
            'google_project_iam_member.gke_node_roles["roles/container.defaultNodeServiceAccount"]',
            "google_project_iam_member",
            ["create"],
            {
                "role": "roles/container.defaultNodeServiceAccount",
                "member": "serviceAccount:terraformers-gke-nodes@terraformers-platform.iam.gserviceaccount.com",
            },
        ),
        resource(
            'google_project_service.required["aiplatform.googleapis.com"]',
            "google_project_service",
            ["create"],
            {"service": "aiplatform.googleapis.com"},
        ),
        resource(
            'google_project_service.required["compute.googleapis.com"]',
            "google_project_service",
            ["create"],
            {"service": "compute.googleapis.com"},
        ),
        resource(
            'google_project_service.required["container.googleapis.com"]',
            "google_project_service",
            ["create"],
            {"service": "container.googleapis.com"},
        ),
        resource(
            'google_project_service.required["iamcredentials.googleapis.com"]',
            "google_project_service",
            ["create"],
            {"service": "iamcredentials.googleapis.com"},
        ),
        resource(
            "google_service_account.gke_nodes",
            "google_service_account",
            ["create"],
            {"account_id": "terraformers-gke-nodes"},
        ),
    ]
    return {"resource_changes": changes}


def delivery_foundation_plan() -> dict:
    return {
        "resource_changes": [
            resource(
                "google_project_service.artifact_registry",
                "google_project_service",
                ["create"],
                {"service": "artifactregistry.googleapis.com", "disable_on_destroy": False},
            ),
            resource(
                "google_artifact_registry_repository.backend",
                "google_artifact_registry_repository",
                ["create"],
                {
                    "project": "terraformers-platform",
                    "location": "asia-northeast3",
                    "repository_id": "terraformers-backend",
                    "format": "DOCKER",
                },
            ),
            resource(
                "google_artifact_registry_repository_iam_member.publisher_writer",
                "google_artifact_registry_repository_iam_member",
                ["create"],
                {
                    "project": "terraformers-platform",
                    "location": "asia-northeast3",
                    "repository": "terraformers-backend",
                    "role": "roles/artifactregistry.writer",
                    "member": "serviceAccount:terraformers-image-publish@terraformers-platform.iam.gserviceaccount.com",
                },
            ),
            resource(
                "google_artifact_registry_repository_iam_member.gke_node_reader",
                "google_artifact_registry_repository_iam_member",
                ["create"],
                {
                    "project": "terraformers-platform",
                    "location": "asia-northeast3",
                    "repository": "terraformers-backend",
                    "role": "roles/artifactregistry.reader",
                    "member": "serviceAccount:terraformers-gke-nodes@terraformers-platform.iam.gserviceaccount.com",
                },
            ),
            resource(
                "google_artifact_registry_repository_iam_member.plan_reader",
                "google_artifact_registry_repository_iam_member",
                ["create"],
                {
                    "project": "terraformers-platform",
                    "location": "asia-northeast3",
                    "repository": "terraformers-backend",
                    "role": "roles/artifactregistry.reader",
                    "member": "serviceAccount:terraformers-plan@terraformers-platform.iam.gserviceaccount.com",
                },
            ),
        ]
    }


class GcpTargetPlanGateTest(unittest.TestCase):
    def test_foundation_accepts_reviewed_contract(self) -> None:
        result = gate.validate_plan(foundation_plan(), "foundation")
        self.assertEqual(result["resource_change_count"], 12)

    def test_foundation_rejects_unreviewed_resource(self) -> None:
        plan = foundation_plan()
        plan["resource_changes"].append(
            resource("google_compute_firewall.unreviewed", "google_compute_firewall", ["create"], {"name": "unexpected"})
        )
        with self.assertRaises(gate.ContractError):
            gate.validate_plan(plan, "foundation")

    def test_foundation_rejects_destructive_action(self) -> None:
        plan = foundation_plan()
        plan["resource_changes"][0]["change"]["actions"] = ["delete", "create"]
        with self.assertRaises(gate.ContractError):
            gate.validate_plan(plan, "foundation")

    def test_foundation_rejects_default_compute_service_account_for_temporary_pool(self) -> None:
        plan = foundation_plan()
        cluster = next(
            item for item in plan["resource_changes"]
            if item["address"] == "google_container_cluster.target"
        )
        cluster["change"]["after"]["node_config"][0]["service_account"] = "default"
        with self.assertRaises(gate.ContractError):
            gate.validate_plan(plan, "foundation")

    def test_activate_accepts_only_zero_to_one_node_pool_update(self) -> None:
        before = {
            "name": "terraformers-target-primary",
            "location": "asia-northeast3-a",
            "node_count": 0,
            "node_config": [node_config()],
        }
        after = dict(before)
        after["node_count"] = 1
        plan = {
            "resource_changes": [
                resource(
                    "google_container_node_pool.target",
                    "google_container_node_pool",
                    ["update"],
                    after,
                    before,
                )
            ]
        }
        result = gate.validate_plan(plan, "activate")
        self.assertEqual(result["resource_change_count"], 1)

    def test_activate_rejects_transition_that_does_not_start_at_zero(self) -> None:
        before = {
            "name": "terraformers-target-primary",
            "location": "asia-northeast3-a",
            "node_count": 1,
            "node_config": [node_config()],
        }
        after = dict(before)
        plan = {
            "resource_changes": [
                resource(
                    "google_container_node_pool.target",
                    "google_container_node_pool",
                    ["update"],
                    after,
                    before,
                )
            ]
        }
        with self.assertRaises(gate.ContractError):
            gate.validate_plan(plan, "activate")

    def test_activate_rejects_other_managed_change(self) -> None:
        before = {
            "name": "terraformers-target-primary",
            "location": "asia-northeast3-a",
            "node_count": 0,
            "node_config": [node_config()],
        }
        after = dict(before)
        after["node_count"] = 1
        plan = {
            "resource_changes": [
                resource(
                    "google_container_node_pool.target",
                    "google_container_node_pool",
                    ["update"],
                    after,
                    before,
                ),
                resource(
                    "google_compute_network.target",
                    "google_compute_network",
                    ["update"],
                    {"name": "terraformers-target"},
                    {"name": "terraformers-target"},
                ),
            ]
        }
        with self.assertRaises(gate.ContractError):
            gate.validate_plan(plan, "activate")

    def test_idle_accepts_only_one_to_zero_node_pool_update(self) -> None:
        before = {
            "name": "terraformers-target-primary",
            "location": "asia-northeast3-a",
            "node_count": 1,
            "node_config": [node_config()],
        }
        after = dict(before)
        after["node_count"] = 0
        plan = {
            "resource_changes": [
                resource(
                    "google_container_node_pool.target",
                    "google_container_node_pool",
                    ["update"],
                    after,
                    before,
                )
            ]
        }
        result = gate.validate_plan(plan, "idle")
        self.assertEqual(result["resource_change_count"], 1)

    def test_idle_rejects_other_managed_change(self) -> None:
        before = {
            "name": "terraformers-target-primary",
            "location": "asia-northeast3-a",
            "node_count": 1,
            "node_config": [node_config()],
        }
        after = dict(before)
        after["node_count"] = 0
        plan = {
            "resource_changes": [
                resource(
                    "google_container_node_pool.target",
                    "google_container_node_pool",
                    ["update"],
                    after,
                    before,
                ),
                resource(
                    "google_compute_network.target",
                    "google_compute_network",
                    ["update"],
                    {"name": "terraformers-target"},
                    {"name": "terraformers-target"},
                ),
            ]
        }
        with self.assertRaises(gate.ContractError):
            gate.validate_plan(plan, "idle")

    def test_delivery_foundation_accepts_exact_five_create_contract(self) -> None:
        result = gate.validate_plan(delivery_foundation_plan(), "delivery-foundation")
        self.assertEqual(result["resource_change_count"], 5)

    def test_delivery_foundation_rejects_unreviewed_resource(self) -> None:
        plan = delivery_foundation_plan()
        plan["resource_changes"].append(
            resource(
                "google_artifact_registry_repository.unreviewed",
                "google_artifact_registry_repository",
                ["create"],
                {
                    "project": "terraformers-platform",
                    "location": "asia-northeast3",
                    "repository_id": "unexpected",
                    "format": "DOCKER",
                },
            )
        )
        with self.assertRaises(gate.ContractError):
            gate.validate_plan(plan, "delivery-foundation")

    def test_delivery_foundation_rejects_repository_update(self) -> None:
        plan = delivery_foundation_plan()
        repository = next(
            item for item in plan["resource_changes"]
            if item["address"] == "google_artifact_registry_repository.backend"
        )
        repository["change"]["actions"] = ["update"]
        with self.assertRaises(gate.ContractError):
            gate.validate_plan(plan, "delivery-foundation")

    def test_delivery_foundation_rejects_broad_publisher_role(self) -> None:
        plan = delivery_foundation_plan()
        writer = next(
            item for item in plan["resource_changes"]
            if item["address"] == "google_artifact_registry_repository_iam_member.publisher_writer"
        )
        writer["change"]["after"]["role"] = "roles/artifactregistry.admin"
        with self.assertRaises(gate.ContractError):
            gate.validate_plan(plan, "delivery-foundation")


if __name__ == "__main__":
    unittest.main()
