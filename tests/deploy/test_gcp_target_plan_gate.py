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
                "google_project_service.artifact_registry[0]",
                "google_project_service",
                ["create"],
                {"service": "artifactregistry.googleapis.com", "disable_on_destroy": False},
            ),
            resource(
                "google_artifact_registry_repository.backend[0]",
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
                "google_artifact_registry_repository_iam_member.publisher_writer[0]",
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
                "google_artifact_registry_repository_iam_member.gke_node_reader[0]",
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
                "google_artifact_registry_repository_iam_member.plan_reader[0]",
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


def runtime_secret_foundation_plan() -> dict:
    return {
        "resource_changes": [
            resource(
                "google_project_service.secret_manager[0]",
                "google_project_service",
                ["create"],
                {"service": "secretmanager.googleapis.com", "disable_on_destroy": False},
            ),
            resource(
                "google_secret_manager_secret.mariadb_root[0]",
                "google_secret_manager_secret",
                ["create"],
                {"project": "terraformers-platform", "secret_id": "terraformers-mariadb-root-password"},
            ),
            resource(
                "google_secret_manager_secret.mariadb_app[0]",
                "google_secret_manager_secret",
                ["create"],
                {"project": "terraformers-platform", "secret_id": "terraformers-mariadb-app-password"},
            ),
        ]
    }


def runtime_dependencies_plan() -> dict:
    pod_cidr = "10.44.0.0/14"
    cluster_before = {
        "name": "terraformers-target",
        "location": "asia-northeast3-a",
        "network": "terraformers-target",
        "subnetwork": "terraformers-target",
        "workload_identity_config": [{"workload_pool": "terraformers-platform.svc.id.goog"}],
        "addons_config": [{"gce_persistent_disk_csi_driver_config": [{"enabled": True}]}],
        "release_channel": [{"channel": "REGULAR"}],
        "ip_allocation_policy": [{"cluster_ipv4_cidr_block": pod_cidr}],
        "secret_sync_config": [],
    }
    cluster_after = dict(cluster_before)
    cluster_after["secret_sync_config"] = [{"enabled": True, "rotation_config": []}]
    digest = "a" * 64
    startup = (
        '#!/bin/bash\n'
        f'MARIADB_IMAGE="mariadb:11.4@sha256:{digest}"\n'
        'echo /var/lib/mysql >/dev/null\n'
        'echo versions/latest:access >/dev/null\n'
    )
    return {
        "resource_changes": [
            resource(
                "google_container_cluster.target",
                "google_container_cluster",
                ["update"],
                cluster_after,
                cluster_before,
            ),
            resource(
                "google_storage_bucket.runtime_objects[0]",
                "google_storage_bucket",
                ["create"],
                {
                    "name": "terraformers-runtime-objects-21647422237",
                    "project": "terraformers-platform",
                    "location": "ASIA-NORTHEAST3",
                    "uniform_bucket_level_access": True,
                    "public_access_prevention": "enforced",
                    "force_destroy": False,
                },
            ),
            resource(
                "google_storage_bucket_iam_member.backend_object_user[0]",
                "google_storage_bucket_iam_member",
                ["create"],
                {
                    "bucket": "terraformers-runtime-objects-21647422237",
                    "role": "roles/storage.objectUser",
                    "member": BACKEND_PRINCIPAL,
                },
            ),
            resource(
                "google_service_account.mariadb[0]",
                "google_service_account",
                ["create"],
                {"account_id": "terraformers-mariadb"},
            ),
            resource(
                "google_secret_manager_secret_iam_member.mariadb_root_accessor[0]",
                "google_secret_manager_secret_iam_member",
                ["create"],
                {
                    "project": "terraformers-platform",
                    "secret_id": "terraformers-mariadb-root-password",
                    "role": "roles/secretmanager.secretAccessor",
                    "member": "serviceAccount:terraformers-mariadb@terraformers-platform.iam.gserviceaccount.com",
                },
            ),
            resource(
                "google_secret_manager_secret_iam_member.mariadb_app_accessor[0]",
                "google_secret_manager_secret_iam_member",
                ["create"],
                {
                    "project": "terraformers-platform",
                    "secret_id": "terraformers-mariadb-app-password",
                    "role": "roles/secretmanager.secretAccessor",
                    "member": "serviceAccount:terraformers-mariadb@terraformers-platform.iam.gserviceaccount.com",
                },
            ),
            resource(
                "google_secret_manager_secret_iam_member.secret_sync_app_accessor[0]",
                "google_secret_manager_secret_iam_member",
                ["create"],
                {
                    "project": "terraformers-platform",
                    "secret_id": "terraformers-mariadb-app-password",
                    "role": "roles/secretmanager.secretAccessor",
                    "member": (
                        "principal://iam.googleapis.com/projects/21647422237/locations/global/"
                        "workloadIdentityPools/terraformers-platform.svc.id.goog/subject/"
                        "ns/terraformers-target/sa/terraformers-secret-sync"
                    ),
                },
            ),
            resource(
                "google_compute_disk.mariadb_data[0]",
                "google_compute_disk",
                ["create"],
                {
                    "name": "terraformers-mariadb-data",
                    "type": "pd-balanced",
                    "zone": "asia-northeast3-a",
                    "size": 20,
                },
            ),
            resource(
                "google_compute_instance.mariadb[0]",
                "google_compute_instance",
                ["create"],
                {
                    "name": "terraformers-mariadb",
                    "machine_type": "e2-medium",
                    "zone": "asia-northeast3-a",
                    "attached_disk": [{"device_name": "terraformers-mariadb-data"}],
                    "network_interface": [{
                        "subnetwork": "https://www.googleapis.com/compute/v1/projects/terraformers-platform/"
                                      "regions/asia-northeast3/subnetworks/terraformers-target",
                        "access_config": [{}],
                    }],
                    "service_account": [{
                        "email": "terraformers-mariadb@terraformers-platform.iam.gserviceaccount.com",
                        "scopes": ["https://www.googleapis.com/auth/cloud-platform"],
                    }],
                    "metadata_startup_script": startup,
                },
            ),
            resource(
                "google_compute_firewall.mariadb_from_gke[0]",
                "google_compute_firewall",
                ["create"],
                {
                    "name": "terraformers-mariadb-from-gke",
                    "direction": "INGRESS",
                    "allow": [{"protocol": "tcp", "ports": ["3306"]}],
                    "source_ranges": ["10.40.0.0/20", pod_cidr],
                    "target_service_accounts": [
                        "terraformers-mariadb@terraformers-platform.iam.gserviceaccount.com"
                    ],
                },
            ),
        ]
    }


def runtime_host_firewall_repair_plan() -> dict:
    digest = "a" * 64
    common = {
        "name": "terraformers-mariadb",
        "machine_type": "e2-medium",
        "zone": "asia-northeast3-a",
        "allow_stopping_for_update": True,
        "attached_disk": [{"device_name": "terraformers-mariadb-data"}],
        "network_interface": [{
            "subnetwork": "https://www.googleapis.com/compute/v1/projects/terraformers-platform/"
                          "regions/asia-northeast3/subnetworks/terraformers-target",
            "access_config": [{}],
        }],
        "service_account": [{
            "email": "terraformers-mariadb@terraformers-platform.iam.gserviceaccount.com",
            "scopes": ["https://www.googleapis.com/auth/cloud-platform"],
        }],
    }
    before = dict(common)
    before["metadata"] = {}
    before["metadata_startup_script"] = (
        f'MARIADB_IMAGE="mariadb:11.4@sha256:{digest}"\n'
        'docker run --network host mariadb\n'
    )
    after = dict(common)
    after["metadata_startup_script"] = None
    after["metadata"] = {
        "startup-script": (
            f'MARIADB_IMAGE="mariadb:11.4@sha256:{digest}"\n'
            'MARIADB_ALLOWED_SOURCE_RANGES=("10.40.0.0/20" "10.136.0.0/14")\n'
            'iptables -w 5 -C INPUT -p tcp -s "$source_range" --dport 3306 -j ACCEPT\n'
            'iptables -w 5 -I INPUT 1 -p tcp -s "$source_range" --dport 3306 -j ACCEPT\n'
            'docker run --network host mariadb\n'
        )
    }
    return {
        "resource_changes": [
            resource(
                "google_compute_instance.mariadb[0]",
                "google_compute_instance",
                ["update"],
                after,
                before,
            )
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
            if item["address"] == "google_artifact_registry_repository.backend[0]"
        )
        repository["change"]["actions"] = ["update"]
        with self.assertRaises(gate.ContractError):
            gate.validate_plan(plan, "delivery-foundation")

    def test_delivery_foundation_rejects_broad_publisher_role(self) -> None:
        plan = delivery_foundation_plan()
        writer = next(
            item for item in plan["resource_changes"]
            if item["address"] == "google_artifact_registry_repository_iam_member.publisher_writer[0]"
        )
        writer["change"]["after"]["role"] = "roles/artifactregistry.admin"
        with self.assertRaises(gate.ContractError):
            gate.validate_plan(plan, "delivery-foundation")


    def test_runtime_secret_foundation_accepts_exact_three_create_contract(self) -> None:
        result = gate.validate_plan(runtime_secret_foundation_plan(), "runtime-secret-foundation")
        self.assertEqual(result["resource_change_count"], 3)

    def test_runtime_secret_foundation_rejects_secret_version_payload_resource(self) -> None:
        plan = runtime_secret_foundation_plan()
        plan["resource_changes"].append(
            resource(
                "google_secret_manager_secret_version.mariadb_app",
                "google_secret_manager_secret_version",
                ["create"],
                {"secret": "terraformers-mariadb-app-password"},
            )
        )
        with self.assertRaises(gate.ContractError):
            gate.validate_plan(plan, "runtime-secret-foundation")

    def test_runtime_host_firewall_repair_accepts_exact_vm_update(self) -> None:
        result = gate.validate_plan(runtime_host_firewall_repair_plan(), "runtime-host-firewall-repair")
        self.assertEqual(result["resource_change_count"], 1)

    def test_runtime_host_firewall_repair_rejects_public_source(self) -> None:
        plan = runtime_host_firewall_repair_plan()
        instance = plan["resource_changes"][0]
        instance["change"]["after"]["metadata_startup_script"] += "0.0.0.0/0\n"
        with self.assertRaises(gate.ContractError):
            gate.validate_plan(plan, "runtime-host-firewall-repair")

    def test_runtime_host_firewall_repair_rejects_extra_metadata(self) -> None:
        plan = runtime_host_firewall_repair_plan()
        instance = plan["resource_changes"][0]
        instance["change"]["after"]["metadata"]["unexpected"] = "value"
        with self.assertRaises(gate.ContractError):
            gate.validate_plan(plan, "runtime-host-firewall-repair")

    def test_runtime_host_firewall_repair_rejects_unreviewed_vm_field_change(self) -> None:
        plan = runtime_host_firewall_repair_plan()
        instance = plan["resource_changes"][0]
        instance["change"]["before"]["labels"] = {"runtime": "mariadb"}
        instance["change"]["after"]["labels"] = {"runtime": "changed"}
        with self.assertRaises(gate.ContractError):
            gate.validate_plan(plan, "runtime-host-firewall-repair")

    def test_runtime_host_firewall_repair_rejects_extra_change(self) -> None:
        plan = runtime_host_firewall_repair_plan()
        plan["resource_changes"].append(
            resource(
                "google_compute_firewall.unreviewed",
                "google_compute_firewall",
                ["update"],
                {"name": "unexpected"},
                {"name": "unexpected"},
            )
        )
        with self.assertRaises(gate.ContractError):
            gate.validate_plan(plan, "runtime-host-firewall-repair")

    def test_runtime_dependencies_accepts_exact_ten_change_contract(self) -> None:
        result = gate.validate_plan(runtime_dependencies_plan(), "runtime-dependencies")
        self.assertEqual(result["resource_change_count"], 10)

    def test_runtime_dependencies_rejects_extra_resource(self) -> None:
        plan = runtime_dependencies_plan()
        plan["resource_changes"].append(
            resource("google_compute_address.unreviewed", "google_compute_address", ["create"], {"name": "unexpected"})
        )
        with self.assertRaises(gate.ContractError):
            gate.validate_plan(plan, "runtime-dependencies")

    def test_runtime_dependencies_rejects_broad_bucket_role(self) -> None:
        plan = runtime_dependencies_plan()
        binding = next(
            item for item in plan["resource_changes"]
            if item["address"] == "google_storage_bucket_iam_member.backend_object_user[0]"
        )
        binding["change"]["after"]["role"] = "roles/storage.admin"
        with self.assertRaises(gate.ContractError):
            gate.validate_plan(plan, "runtime-dependencies")

    def test_runtime_dependencies_rejects_public_database_firewall(self) -> None:
        plan = runtime_dependencies_plan()
        firewall = next(
            item for item in plan["resource_changes"]
            if item["address"] == "google_compute_firewall.mariadb_from_gke[0]"
        )
        firewall["change"]["after"]["source_ranges"] = ["0.0.0.0/0"]
        with self.assertRaises(gate.ContractError):
            gate.validate_plan(plan, "runtime-dependencies")

    def test_runtime_dependencies_rejects_mutable_mariadb_image(self) -> None:
        plan = runtime_dependencies_plan()
        instance = next(
            item for item in plan["resource_changes"]
            if item["address"] == "google_compute_instance.mariadb[0]"
        )
        instance["change"]["after"]["metadata_startup_script"] = (
            'MARIADB_IMAGE="mariadb:11.4"\n/var/lib/mysql\nversions/latest:access\n'
        )
        with self.assertRaises(gate.ContractError):
            gate.validate_plan(plan, "runtime-dependencies")

    def test_runtime_dependencies_rejects_secret_sync_rotation(self) -> None:
        plan = runtime_dependencies_plan()
        cluster = next(
            item for item in plan["resource_changes"]
            if item["address"] == "google_container_cluster.target"
        )
        cluster["change"]["after"]["secret_sync_config"][0]["rotation_config"] = [{"enabled": True}]
        with self.assertRaises(gate.ContractError):
            gate.validate_plan(plan, "runtime-dependencies")


if __name__ == "__main__":
    unittest.main()
