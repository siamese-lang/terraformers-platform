#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
from typing import Any


FOUNDATION_ACTIONS = {
    "google_compute_network.target": ("google_compute_network", ["create"]),
    "google_compute_subnetwork.target": ("google_compute_subnetwork", ["create"]),
    "google_container_cluster.target": ("google_container_cluster", ["create"]),
    "google_container_node_pool.target": ("google_container_node_pool", ["create"]),
    "google_project_iam_member.backend_service_usage": ("google_project_iam_member", ["create"]),
    "google_project_iam_member.backend_vertex": ("google_project_iam_member", ["create"]),
    'google_project_iam_member.gke_node_roles["roles/container.defaultNodeServiceAccount"]': ("google_project_iam_member", ["create"]),
    'google_project_service.required["aiplatform.googleapis.com"]': ("google_project_service", ["create"]),
    'google_project_service.required["compute.googleapis.com"]': ("google_project_service", ["create"]),
    'google_project_service.required["container.googleapis.com"]': ("google_project_service", ["create"]),
    'google_project_service.required["iamcredentials.googleapis.com"]': ("google_project_service", ["create"]),
    "google_service_account.gke_nodes": ("google_service_account", ["create"]),
}

DELIVERY_FOUNDATION_ACTIONS = {
    "google_project_service.artifact_registry[0]": ("google_project_service", ["create"]),
    "google_artifact_registry_repository.backend[0]": ("google_artifact_registry_repository", ["create"]),
    "google_artifact_registry_repository_iam_member.publisher_writer[0]": (
        "google_artifact_registry_repository_iam_member", ["create"]
    ),
    "google_artifact_registry_repository_iam_member.gke_node_reader[0]": (
        "google_artifact_registry_repository_iam_member", ["create"]
    ),
    "google_artifact_registry_repository_iam_member.plan_reader[0]": (
        "google_artifact_registry_repository_iam_member", ["create"]
    ),
}


RUNTIME_SECRET_FOUNDATION_ACTIONS = {
    "google_project_service.secret_manager[0]": ("google_project_service", ["create"]),
    "google_secret_manager_secret.mariadb_root[0]": ("google_secret_manager_secret", ["create"]),
    "google_secret_manager_secret.mariadb_app[0]": ("google_secret_manager_secret", ["create"]),
}

RUNTIME_DEPENDENCY_ACTIONS = {
    "google_container_cluster.target": ("google_container_cluster", ["update"]),
    "google_storage_bucket.runtime_objects[0]": ("google_storage_bucket", ["create"]),
    "google_storage_bucket_iam_member.backend_object_user[0]": (
        "google_storage_bucket_iam_member", ["create"]
    ),
    "google_service_account.mariadb[0]": ("google_service_account", ["create"]),
    "google_secret_manager_secret_iam_member.mariadb_root_accessor[0]": (
        "google_secret_manager_secret_iam_member", ["create"]
    ),
    "google_secret_manager_secret_iam_member.mariadb_app_accessor[0]": (
        "google_secret_manager_secret_iam_member", ["create"]
    ),
    "google_secret_manager_secret_iam_member.secret_sync_app_accessor[0]": (
        "google_secret_manager_secret_iam_member", ["create"]
    ),
    "google_compute_disk.mariadb_data[0]": ("google_compute_disk", ["create"]),
    "google_compute_instance.mariadb[0]": ("google_compute_instance", ["create"]),
    "google_compute_firewall.mariadb_from_gke[0]": ("google_compute_firewall", ["create"]),
}

RUNTIME_HOST_FIREWALL_REPAIR_ACTIONS = {
    "google_compute_instance.mariadb[0]": ("google_compute_instance", ["update"]),
}

EXPECTED_SERVICES = {
    "aiplatform.googleapis.com",
    "compute.googleapis.com",
    "container.googleapis.com",
    "iamcredentials.googleapis.com",
}

EXPECTED_IAM = {
    (
        "roles/serviceusage.serviceUsageConsumer",
        "principal://iam.googleapis.com/projects/21647422237/locations/global/workloadIdentityPools/"
        "terraformers-platform.svc.id.goog/subject/ns/terraformers-target/sa/terraformers-backend",
    ),
    (
        "roles/aiplatform.user",
        "principal://iam.googleapis.com/projects/21647422237/locations/global/workloadIdentityPools/"
        "terraformers-platform.svc.id.goog/subject/ns/terraformers-target/sa/terraformers-backend",
    ),
    (
        "roles/container.defaultNodeServiceAccount",
        "serviceAccount:terraformers-gke-nodes@terraformers-platform.iam.gserviceaccount.com",
    ),
}


class ContractError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ContractError(message)


def managed_changes(plan: dict[str, Any]) -> dict[str, dict[str, Any]]:
    result: dict[str, dict[str, Any]] = {}
    for resource in plan.get("resource_changes", []):
        actions = list(resource.get("change", {}).get("actions", []))
        mode = resource.get("mode", "managed")
        if actions == ["no-op"]:
            continue
        if mode == "data":
            require(actions == ["read"], f"data source has non-read action: {resource.get('address')}: {actions}")
            continue
        address = str(resource.get("address", ""))
        require(address, "managed resource change is missing address")
        result[address] = resource
    return result


def single_block(after: dict[str, Any], key: str) -> dict[str, Any]:
    value = after.get(key)
    require(isinstance(value, list) and len(value) == 1 and isinstance(value[0], dict), f"{key} must have one block")
    return value[0]


def validate_foundation(changes: dict[str, dict[str, Any]]) -> None:
    require(set(changes) == set(FOUNDATION_ACTIONS), "foundation plan does not match the reviewed 12-resource address set")

    for address, (expected_type, expected_actions) in FOUNDATION_ACTIONS.items():
        resource = changes[address]
        require(resource.get("type") == expected_type, f"{address}: unexpected type {resource.get('type')}")
        actions = list(resource.get("change", {}).get("actions", []))
        require(actions == expected_actions, f"{address}: expected {expected_actions}, got {actions}")

    network = changes["google_compute_network.target"]["change"]["after"]
    require(network.get("name") == "terraformers-target", "unexpected VPC name")
    require(network.get("auto_create_subnetworks") is False, "VPC must remain custom-mode")

    subnet = changes["google_compute_subnetwork.target"]["change"]["after"]
    require(subnet.get("name") == "terraformers-target", "unexpected subnet name")
    require(subnet.get("ip_cidr_range") == "10.40.0.0/20", "unexpected subnet CIDR")
    require(subnet.get("region") == "asia-northeast3", "unexpected subnet region")

    cluster = changes["google_container_cluster.target"]["change"]["after"]
    require(cluster.get("name") == "terraformers-target", "unexpected GKE cluster name")
    require(cluster.get("location") == "asia-northeast3-a", "unexpected GKE zone")
    require(cluster.get("remove_default_node_pool") is True, "default GKE node pool must be removed")
    require(cluster.get("initial_node_count") == 1, "bootstrap default node count must remain 1")
    require(cluster.get("deletion_protection") is False, "unexpected deletion_protection value")
    bootstrap_node_config = single_block(cluster, "node_config")
    require(
        bootstrap_node_config.get("service_account")
        == "terraformers-gke-nodes@terraformers-platform.iam.gserviceaccount.com",
        "temporary default node pool uses an unexpected service account",
    )
    require(
        bootstrap_node_config.get("oauth_scopes")
        == ["https://www.googleapis.com/auth/cloud-platform"],
        "temporary default node pool OAuth scopes changed",
    )
    workload_identity = single_block(cluster, "workload_identity_config")
    require(workload_identity.get("workload_pool") == "terraformers-platform.svc.id.goog", "unexpected workload identity pool")
    addons = single_block(cluster, "addons_config")
    csi = single_block(addons, "gce_persistent_disk_csi_driver_config")
    require(csi.get("enabled") is True, "GCE PD CSI driver must be enabled")
    release = single_block(cluster, "release_channel")
    require(release.get("channel") == "REGULAR", "GKE release channel must remain REGULAR")

    node_pool = changes["google_container_node_pool.target"]["change"]["after"]
    require(node_pool.get("name") == "terraformers-target-primary", "unexpected node pool name")
    require(node_pool.get("location") == "asia-northeast3-a", "unexpected node pool zone")
    require(node_pool.get("node_count") == 1, "foundation node_count must be 1")
    node_config = single_block(node_pool, "node_config")
    require(node_config.get("machine_type") == "e2-standard-2", "unexpected node machine type")
    require(node_config.get("disk_type") == "pd-standard", "unexpected node disk type")
    require(node_config.get("disk_size_gb") == 30, "unexpected node disk size")
    require(
        node_config.get("service_account")
        == "terraformers-gke-nodes@terraformers-platform.iam.gserviceaccount.com",
        "unexpected GKE node service account",
    )
    require(node_config.get("labels", {}).get("terraformers-runtime") == "target", "target node label missing")
    linux = single_block(node_config, "linux_node_config")
    require(linux.get("sysctls", {}).get("vm.max_map_count") == "262144", "OpenSearch vm.max_map_count contract changed")
    metadata = single_block(node_config, "workload_metadata_config")
    require(metadata.get("mode") == "GKE_METADATA", "GKE workload metadata mode changed")

    service_values = {
        resource["change"]["after"].get("service")
        for resource in changes.values()
        if resource.get("type") == "google_project_service"
    }
    require(service_values == EXPECTED_SERVICES, "required API set changed")

    iam_values = {
        (resource["change"]["after"].get("role"), resource["change"]["after"].get("member"))
        for resource in changes.values()
        if resource.get("type") == "google_project_iam_member"
    }
    require(iam_values == EXPECTED_IAM, "project IAM bindings changed")

    node_sa = changes["google_service_account.gke_nodes"]["change"]["after"]
    require(node_sa.get("account_id") == "terraformers-gke-nodes", "unexpected node service account id")


def validate_delivery_foundation(changes: dict[str, dict[str, Any]]) -> None:
    require(
        set(changes) == set(DELIVERY_FOUNDATION_ACTIONS),
        "delivery-foundation plan does not match the reviewed five-resource address set",
    )

    for address, (expected_type, expected_actions) in DELIVERY_FOUNDATION_ACTIONS.items():
        resource = changes[address]
        require(resource.get("type") == expected_type, f"{address}: unexpected type {resource.get('type')}")
        actions = list(resource.get("change", {}).get("actions", []))
        require(actions == expected_actions, f"{address}: expected {expected_actions}, got {actions}")

    service = changes["google_project_service.artifact_registry[0]"]["change"]["after"]
    require(service.get("service") == "artifactregistry.googleapis.com", "unexpected delivery API")
    require(service.get("disable_on_destroy") is False, "Artifact Registry API must not be disabled on destroy")

    repository = changes["google_artifact_registry_repository.backend[0]"]["change"]["after"]
    require(repository.get("project") == "terraformers-platform", "unexpected Artifact Registry project")
    require(repository.get("location") == "asia-northeast3", "unexpected Artifact Registry location")
    require(repository.get("repository_id") == "terraformers-backend", "unexpected Artifact Registry repository id")
    require(repository.get("format") == "DOCKER", "Artifact Registry repository must remain Docker format")

    expected_members = {
        "google_artifact_registry_repository_iam_member.publisher_writer[0]": (
            "roles/artifactregistry.writer",
            "serviceAccount:terraformers-image-publish@terraformers-platform.iam.gserviceaccount.com",
        ),
        "google_artifact_registry_repository_iam_member.gke_node_reader[0]": (
            "roles/artifactregistry.reader",
            "serviceAccount:terraformers-gke-nodes@terraformers-platform.iam.gserviceaccount.com",
        ),
        "google_artifact_registry_repository_iam_member.plan_reader[0]": (
            "roles/artifactregistry.reader",
            "serviceAccount:terraformers-plan@terraformers-platform.iam.gserviceaccount.com",
        ),
    }

    for address, (role, member) in expected_members.items():
        after = changes[address]["change"]["after"]
        require(after.get("project") == "terraformers-platform", f"{address}: unexpected project")
        require(after.get("location") == "asia-northeast3", f"{address}: unexpected location")
        require(after.get("repository") == "terraformers-backend", f"{address}: unexpected repository")
        require(after.get("role") == role, f"{address}: unexpected role")
        require(after.get("member") == member, f"{address}: unexpected member")


def validate_runtime_secret_foundation(changes: dict[str, dict[str, Any]]) -> None:
    require(
        set(changes) == set(RUNTIME_SECRET_FOUNDATION_ACTIONS),
        "runtime-secret-foundation plan does not match the reviewed three-resource address set",
    )
    for address, (expected_type, expected_actions) in RUNTIME_SECRET_FOUNDATION_ACTIONS.items():
        resource = changes[address]
        require(resource.get("type") == expected_type, f"{address}: unexpected type {resource.get('type')}")
        actions = list(resource.get("change", {}).get("actions", []))
        require(actions == expected_actions, f"{address}: expected {expected_actions}, got {actions}")

    service = changes["google_project_service.secret_manager[0]"]["change"]["after"]
    require(service.get("service") == "secretmanager.googleapis.com", "unexpected Secret Manager API")
    require(service.get("disable_on_destroy") is False, "Secret Manager API must not be disabled on destroy")

    expected_ids = {
        "google_secret_manager_secret.mariadb_root[0]": "terraformers-mariadb-root-password",
        "google_secret_manager_secret.mariadb_app[0]": "terraformers-mariadb-app-password",
    }
    for address, secret_id in expected_ids.items():
        after = changes[address]["change"]["after"]
        require(after.get("project") == "terraformers-platform", f"{address}: unexpected project")
        require(after.get("secret_id") == secret_id, f"{address}: unexpected secret id")


def validate_runtime_dependencies(changes: dict[str, dict[str, Any]]) -> None:
    require(
        set(changes) == set(RUNTIME_DEPENDENCY_ACTIONS),
        "runtime-dependencies plan does not match the reviewed ten-resource change set",
    )
    for address, (expected_type, expected_actions) in RUNTIME_DEPENDENCY_ACTIONS.items():
        resource = changes[address]
        require(resource.get("type") == expected_type, f"{address}: unexpected type {resource.get('type')}")
        actions = list(resource.get("change", {}).get("actions", []))
        require(actions == expected_actions, f"{address}: expected {expected_actions}, got {actions}")

    cluster_change = changes["google_container_cluster.target"]["change"]
    before_cluster = cluster_change.get("before") or {}
    after_cluster = cluster_change.get("after") or {}
    for key in ("name", "location", "network", "subnetwork"):
        require(before_cluster.get(key) == after_cluster.get(key), f"GKE {key} changed outside Secret Sync enablement")
    for key in ("workload_identity_config", "addons_config", "release_channel", "ip_allocation_policy"):
        require(before_cluster.get(key) == after_cluster.get(key), f"GKE {key} changed outside Secret Sync enablement")
    before_sync = before_cluster.get("secret_sync_config") or []
    require(
        before_sync == [] or (
            len(before_sync) == 1
            and isinstance(before_sync[0], dict)
            and before_sync[0].get("enabled") is False
        ),
        "GKE Secret Sync was already enabled or had an unexpected before-state",
    )
    after_sync = single_block(after_cluster, "secret_sync_config")
    require(after_sync.get("enabled") is True, "GKE Secret Sync must be enabled")
    rotation = after_sync.get("rotation_config") or []
    require(rotation == [], "automatic Secret Sync rotation is not selected")

    bucket = changes["google_storage_bucket.runtime_objects[0]"]["change"]["after"]
    require(bucket.get("name") == "terraformers-runtime-objects-21647422237", "unexpected runtime bucket name")
    require(bucket.get("project") == "terraformers-platform", "unexpected runtime bucket project")
    require(bucket.get("location") == "ASIA-NORTHEAST3" or bucket.get("location") == "asia-northeast3",
            "runtime bucket must stay in asia-northeast3")
    require(bucket.get("uniform_bucket_level_access") is True, "runtime bucket must use uniform bucket-level access")
    require(bucket.get("public_access_prevention") == "enforced", "runtime bucket public access prevention must be enforced")
    require(bucket.get("force_destroy") is False, "runtime bucket force_destroy must remain false")

    bucket_iam = changes["google_storage_bucket_iam_member.backend_object_user[0]"]["change"]["after"]
    require(bucket_iam.get("bucket") == "terraformers-runtime-objects-21647422237", "unexpected runtime bucket IAM target")
    require(bucket_iam.get("role") == "roles/storage.objectUser", "backend runtime bucket role changed")
    require(
        bucket_iam.get("member")
        == "principal://iam.googleapis.com/projects/21647422237/locations/global/workloadIdentityPools/"
           "terraformers-platform.svc.id.goog/subject/ns/terraformers-target/sa/terraformers-backend",
        "unexpected backend runtime bucket principal",
    )

    mariadb_sa = changes["google_service_account.mariadb[0]"]["change"]["after"]
    require(mariadb_sa.get("account_id") == "terraformers-mariadb", "unexpected MariaDB service account id")

    expected_secret_members = {
        "google_secret_manager_secret_iam_member.mariadb_root_accessor[0]": (
            "terraformers-mariadb-root-password",
            "serviceAccount:terraformers-mariadb@terraformers-platform.iam.gserviceaccount.com",
        ),
        "google_secret_manager_secret_iam_member.mariadb_app_accessor[0]": (
            "terraformers-mariadb-app-password",
            "serviceAccount:terraformers-mariadb@terraformers-platform.iam.gserviceaccount.com",
        ),
        "google_secret_manager_secret_iam_member.secret_sync_app_accessor[0]": (
            "terraformers-mariadb-app-password",
            "principal://iam.googleapis.com/projects/21647422237/locations/global/workloadIdentityPools/"
            "terraformers-platform.svc.id.goog/subject/ns/terraformers-target/sa/terraformers-secret-sync",
        ),
    }
    for address, (secret_id, member) in expected_secret_members.items():
        after = changes[address]["change"]["after"]
        require(after.get("project") == "terraformers-platform", f"{address}: unexpected project")
        require(after.get("secret_id") == secret_id, f"{address}: unexpected secret id")
        require(after.get("role") == "roles/secretmanager.secretAccessor", f"{address}: unexpected secret role")
        require(after.get("member") == member, f"{address}: unexpected secret principal")

    disk = changes["google_compute_disk.mariadb_data[0]"]["change"]["after"]
    require(disk.get("name") == "terraformers-mariadb-data", "unexpected MariaDB data disk name")
    require(disk.get("type") == "pd-balanced", "MariaDB data disk must remain pd-balanced")
    require(disk.get("zone") == "asia-northeast3-a", "unexpected MariaDB data disk zone")
    require(disk.get("size") == 20, "MariaDB data disk must remain 20 GiB")

    instance = changes["google_compute_instance.mariadb[0]"]["change"]["after"]
    require(instance.get("name") == "terraformers-mariadb", "unexpected MariaDB VM name")
    require(instance.get("machine_type") == "e2-medium", "MariaDB VM must remain e2-medium")
    require(instance.get("zone") == "asia-northeast3-a", "unexpected MariaDB VM zone")
    attached = single_block(instance, "attached_disk")
    require(attached.get("device_name") == "terraformers-mariadb-data", "unexpected MariaDB attached disk")
    interface = single_block(instance, "network_interface")
    subnetwork = str(interface.get("subnetwork") or "")
    require(subnetwork.endswith("/subnetworks/terraformers-target") or subnetwork == "terraformers-target",
            "MariaDB VM must use the canonical target subnet")
    access_config = interface.get("access_config")
    require(isinstance(access_config, list) and len(access_config) == 1, "MariaDB VM requires one outbound bootstrap IPv4 access config")
    service_account = single_block(instance, "service_account")
    require(
        service_account.get("email") == "terraformers-mariadb@terraformers-platform.iam.gserviceaccount.com",
        "unexpected MariaDB VM service account",
    )
    require(
        service_account.get("scopes") == ["https://www.googleapis.com/auth/cloud-platform"],
        "MariaDB VM OAuth scope changed",
    )
    startup = str(instance.get("metadata_startup_script") or "")
    require(
        __import__("re").search(r'MARIADB_IMAGE="mariadb:11\.4@sha256:[0-9a-f]{64}"', startup) is not None,
        "MariaDB startup script must pin an exact 11.4 image digest",
    )
    require("/var/lib/mysql" in startup, "MariaDB startup script must mount the dedicated data path")
    require("versions/latest:access" in startup, "MariaDB startup script must retrieve secrets from Secret Manager")

    firewall = changes["google_compute_firewall.mariadb_from_gke[0]"]["change"]["after"]
    require(firewall.get("name") == "terraformers-mariadb-from-gke", "unexpected MariaDB firewall name")
    require(firewall.get("direction") == "INGRESS", "MariaDB firewall must remain ingress-only")
    allow = single_block(firewall, "allow")
    require(allow.get("protocol") == "tcp" and allow.get("ports") == ["3306"], "MariaDB firewall must allow TCP/3306 only")
    targets = firewall.get("target_service_accounts") or []
    require(
        targets == ["terraformers-mariadb@terraformers-platform.iam.gserviceaccount.com"],
        "MariaDB firewall target service account changed",
    )
    ranges = set(firewall.get("source_ranges") or [])
    ip_alloc = single_block(after_cluster, "ip_allocation_policy")
    pod_cidr = ip_alloc.get("cluster_ipv4_cidr_block")
    require(isinstance(pod_cidr, str) and pod_cidr, "GKE Pod CIDR is unavailable for MariaDB firewall validation")
    require(ranges == {"10.40.0.0/20", pod_cidr}, "MariaDB firewall source ranges changed")


def validate_runtime_host_firewall_repair(changes: dict[str, dict[str, Any]]) -> None:
    require(
        set(changes) == set(RUNTIME_HOST_FIREWALL_REPAIR_ACTIONS),
        "runtime-host-firewall-repair may only update the MariaDB VM",
    )
    resource = changes["google_compute_instance.mariadb[0]"]
    require(resource.get("type") == "google_compute_instance", "runtime host-firewall repair changed resource type")
    change = resource.get("change", {})
    require(
        list(change.get("actions", [])) == ["update"],
        "runtime host-firewall repair must be an in-place VM update",
    )
    before = change.get("before") or {}
    after = change.get("after") or {}

    before_without_startup = dict(before)
    after_without_startup = dict(after)
    before_startup = str(before_without_startup.pop("metadata_startup_script", "") or "")
    after_startup = str(after_without_startup.pop("metadata_startup_script", "") or "")
    require(
        before_without_startup == after_without_startup,
        "runtime-host-firewall-repair may change only metadata_startup_script",
    )

    require(before_startup != after_startup, "runtime host-firewall repair must change only startup-script behavior")
    require("--network host" in after_startup, "MariaDB host-network contract changed")
    require("10.40.0.0/20" in after_startup, "MariaDB guest firewall is missing the target subnet")
    require("10.136.0.0/14" in after_startup, "MariaDB guest firewall is missing the current GKE Pod CIDR")
    require("0.0.0.0/0" not in after_startup, "MariaDB guest firewall must not allow public ingress")
    require(
        'iptables -w 5 -C INPUT -p tcp -s "$source_range" --dport 3306 -j ACCEPT' in after_startup,
        "MariaDB guest firewall idempotence check is missing",
    )
    require(
        'iptables -w 5 -I INPUT 1 -p tcp -s "$source_range" --dport 3306 -j ACCEPT' in after_startup,
        "MariaDB guest firewall allow rule is missing",
    )


def validate_node_pool_transition(
    changes: dict[str, dict[str, Any]],
    operation: str,
    before_count: int,
    after_count: int,
) -> None:
    require(
        set(changes) == {"google_container_node_pool.target"},
        f"{operation} plan may only change the target node pool",
    )
    resource = changes["google_container_node_pool.target"]
    require(resource.get("type") == "google_container_node_pool", f"{operation} resource type changed")
    change = resource.get("change", {})
    require(
        list(change.get("actions", [])) == ["update"],
        f"{operation} plan must be an in-place node-pool update",
    )
    before = change.get("before") or {}
    after = change.get("after") or {}
    require(
        before.get("node_count") == before_count,
        f"{operation} transition must start from node_count={before_count}",
    )
    require(
        after.get("node_count") == after_count,
        f"{operation} transition must end at node_count={after_count}",
    )
    require(
        after.get("name") == "terraformers-target-primary",
        f"{operation} plan targets an unexpected node pool",
    )
    require(after.get("location") == "asia-northeast3-a", f"{operation} node-pool zone changed")
    node_config = single_block(after, "node_config")
    require(node_config.get("machine_type") == "e2-standard-2", f"{operation} plan changes node machine type")
    require(node_config.get("disk_type") == "pd-standard", f"{operation} plan changes node disk type")
    require(node_config.get("disk_size_gb") == 30, f"{operation} plan changes node disk size")
    require(
        node_config.get("service_account")
        == "terraformers-gke-nodes@terraformers-platform.iam.gserviceaccount.com",
        f"{operation} plan changes node service account",
    )


def validate_activate(changes: dict[str, dict[str, Any]]) -> None:
    validate_node_pool_transition(changes, "activate", 0, 1)


def validate_idle(changes: dict[str, dict[str, Any]]) -> None:
    validate_node_pool_transition(changes, "idle", 1, 0)

def validate_plan(plan: dict[str, Any], operation: str) -> dict[str, Any]:
    changes = managed_changes(plan)
    for address, resource in changes.items():
        actions = list(resource.get("change", {}).get("actions", []))
        require("delete" not in actions, f"destructive action is forbidden: {address}: {actions}")
        require(actions not in (["delete", "create"], ["create", "delete"]), f"replacement is forbidden: {address}")

    if operation == "foundation":
        validate_foundation(changes)
    elif operation == "delivery-foundation":
        validate_delivery_foundation(changes)
    elif operation == "runtime-secret-foundation":
        validate_runtime_secret_foundation(changes)
    elif operation == "runtime-dependencies":
        validate_runtime_dependencies(changes)
    elif operation == "runtime-host-firewall-repair":
        validate_runtime_host_firewall_repair(changes)
    elif operation == "activate":
        validate_activate(changes)
    elif operation == "idle":
        validate_idle(changes)
    else:
        raise ContractError(f"unsupported operation: {operation}")

    return {
        "operation": operation,
        "resource_change_count": len(changes),
        "resources": [
            {
                "address": address,
                "type": changes[address].get("type"),
                "actions": list(changes[address].get("change", {}).get("actions", [])),
            }
            for address in sorted(changes)
        ],
    }


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def append_summary(result: dict[str, Any], plan_hash: str) -> None:
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not summary_path:
        return
    lines = [
        "## GCP target apply contract",
        "",
        f"- Operation: `{result['operation']}`",
        f"- Commit: `{os.environ.get('GITHUB_SHA', 'unknown')}`",
        f"- Changed managed resources: `{result['resource_change_count']}`",
        f"- Plan JSON SHA-256: `{plan_hash}`",
        "- Contract gate: `PASS`",
        "- Delete/replacement actions: `0`",
        "- Raw plan, state and changed values are not uploaded.",
        "",
        "| Address | Type | Actions |",
        "| --- | --- | --- |",
    ]
    for resource in result["resources"]:
        lines.append(
            f"| `{resource['address']}` | `{resource['type']}` | "
            f"`{'+'.join(resource['actions'])}` |"
        )
    with open(summary_path, "a", encoding="utf-8") as stream:
        stream.write("\n".join(lines) + "\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Fail-closed contract gate for the GCP target Terraform apply plan.")
    parser.add_argument("--plan-json", required=True, type=Path)
    parser.add_argument(
        "--operation",
        required=True,
        choices=("foundation", "delivery-foundation", "runtime-secret-foundation", "runtime-dependencies", "runtime-host-firewall-repair", "activate", "idle"),
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    plan = json.loads(args.plan_json.read_text(encoding="utf-8"))
    try:
        result = validate_plan(plan, args.operation)
    except ContractError as exc:
        print(f"gcp_target_plan_gate=failed\nreason={exc}")
        return 1
    plan_hash = sha256(args.plan_json)
    append_summary(result, plan_hash)
    print("gcp_target_plan_gate=passed")
    print(f"operation={result['operation']}")
    print(f"resource_change_count={result['resource_change_count']}")
    print(f"plan_json_sha256={plan_hash}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
