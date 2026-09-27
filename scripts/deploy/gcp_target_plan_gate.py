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


def validate_idle(changes: dict[str, dict[str, Any]]) -> None:
    require(set(changes) == {"google_container_node_pool.target"}, "idle plan may only change the target node pool")
    resource = changes["google_container_node_pool.target"]
    require(resource.get("type") == "google_container_node_pool", "idle resource type changed")
    change = resource.get("change", {})
    require(list(change.get("actions", [])) == ["update"], "idle plan must be an in-place node-pool update")
    before = change.get("before") or {}
    after = change.get("after") or {}
    require(before.get("node_count") == 1, "idle transition must start from node_count=1")
    require(after.get("node_count") == 0, "idle transition must end at node_count=0")
    require(after.get("name") == "terraformers-target-primary", "idle plan targets an unexpected node pool")
    require(after.get("location") == "asia-northeast3-a", "idle node-pool zone changed")
    node_config = single_block(after, "node_config")
    require(node_config.get("machine_type") == "e2-standard-2", "idle plan changes node machine type")
    require(node_config.get("disk_type") == "pd-standard", "idle plan changes node disk type")
    require(node_config.get("disk_size_gb") == 30, "idle plan changes node disk size")
    require(
        node_config.get("service_account")
        == "terraformers-gke-nodes@terraformers-platform.iam.gserviceaccount.com",
        "idle plan changes node service account",
    )


def validate_plan(plan: dict[str, Any], operation: str) -> dict[str, Any]:
    changes = managed_changes(plan)
    for address, resource in changes.items():
        actions = list(resource.get("change", {}).get("actions", []))
        require("delete" not in actions, f"destructive action is forbidden: {address}: {actions}")
        require(actions not in (["delete", "create"], ["create", "delete"]), f"replacement is forbidden: {address}")

    if operation == "foundation":
        validate_foundation(changes)
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
    parser.add_argument("--operation", required=True, choices=("foundation", "idle"))
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
