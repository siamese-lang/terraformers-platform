#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


PROJECT_ID = "terraformers-platform"
PROJECT_NUMBER = "21647422237"
REGION = "asia-northeast3"
ZONE = "asia-northeast3-a"
TARGET_NAME = "terraformers-target"
TARGET_NODE_SA = "terraformers-gke-nodes@terraformers-platform.iam.gserviceaccount.com"

REQUIRED_APIS = {
    "aiplatform.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "compute.googleapis.com",
    "container.googleapis.com",
    "iamcredentials.googleapis.com",
}

GLOBAL_QUOTA_MINIMUMS = {
    "CPUS_ALL_REGIONS": 2,
    "IN_USE_ADDRESSES": 1,
}

REGIONAL_QUOTA_MINIMUMS = {
    "CPUS": 2,
    "E2_CPUS": 2,
    "INSTANCES": 1,
    "DISKS_TOTAL_GB": 45,
    "IN_USE_ADDRESSES": 1,
}


class PreflightError(RuntimeError):
    pass


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def require(condition: bool, message: str) -> None:
    if not condition:
        raise PreflightError(message)


def quota_map(payload: dict[str, Any]) -> dict[str, tuple[float, float, float]]:
    result: dict[str, tuple[float, float, float]] = {}
    for quota in payload.get("quotas", []):
        metric = str(quota.get("metric", ""))
        if not metric:
            continue
        limit = float(quota.get("limit", 0) or 0)
        usage = float(quota.get("usage", 0) or 0)
        result[metric] = (limit, usage, limit - usage)
    return result


def validate_quota(
    label: str,
    quotas: dict[str, tuple[float, float, float]],
    minimums: dict[str, float],
) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for metric, required_available in minimums.items():
        require(metric in quotas, f"{label}: required quota metric missing: {metric}")
        limit, usage, available = quotas[metric]
        require(
            available >= required_available,
            f"{label}: {metric} available={available:g}, required>={required_available:g}",
        )
        rows.append(
            {
                "scope": label,
                "metric": metric,
                "limit": limit,
                "usage": usage,
                "available": available,
                "required_available": required_available,
            }
        )
    return rows


def exact_list(payload: Any) -> list[dict[str, Any]]:
    require(isinstance(payload, list), "expected a JSON list")
    return [item for item in payload if isinstance(item, dict)]


def service_names(payload: Any) -> set[str]:
    names: set[str] = set()
    for item in exact_list(payload):
        config = item.get("config") or {}
        if item.get("state") == "ENABLED" and isinstance(config, dict):
            name = config.get("name")
            if isinstance(name, str):
                names.add(name)
    return names


def validate(args: argparse.Namespace) -> dict[str, Any]:
    project = load_json(args.project_json)
    billing = load_json(args.billing_json)
    global_quota_payload = load_json(args.global_quota_json)
    region_payload = load_json(args.region_json)
    machine = load_json(args.machine_json)
    gke_server = load_json(args.gke_server_json)
    services = load_json(args.services_json)
    clusters = exact_list(load_json(args.clusters_json))
    networks = exact_list(load_json(args.networks_json))
    subnets = exact_list(load_json(args.subnets_json))
    service_accounts = exact_list(load_json(args.service_accounts_json))
    instances = exact_list(load_json(args.instances_json))

    require(project.get("projectId") == PROJECT_ID, "unexpected projectId")
    require(str(project.get("projectNumber")) == PROJECT_NUMBER, "unexpected projectNumber")
    require(project.get("lifecycleState") == "ACTIVE", "project lifecycleState is not ACTIVE")

    require(billing.get("projectId") == PROJECT_ID, "billing response projectId mismatch")
    billing_verification = billing.get("verificationStatus", "verified")
    require(
        billing_verification in {"verified", "unavailable"},
        "unexpected billing verification status",
    )
    if billing_verification == "verified":
        require(billing.get("billingEnabled") is True, "billing is not enabled")

    quota_rows = []
    quota_rows.extend(
        validate_quota("global", quota_map(global_quota_payload), GLOBAL_QUOTA_MINIMUMS)
    )
    quota_rows.extend(
        validate_quota("region", quota_map(region_payload), REGIONAL_QUOTA_MINIMUMS)
    )

    require(machine.get("name") == "e2-standard-2", "e2-standard-2 machine type is unavailable")
    require(int(machine.get("guestCpus", 0)) == 2, "unexpected e2-standard-2 vCPU count")
    require(int(machine.get("memoryMb", 0)) == 8192, "unexpected e2-standard-2 memory")
    require(str(machine.get("zone", "")).endswith(f"/zones/{ZONE}") or machine.get("zone") == ZONE, "machine type zone mismatch")

    default_version = gke_server.get("defaultClusterVersion")
    require(isinstance(default_version, str) and default_version, "GKE defaultClusterVersion missing")

    enabled = service_names(services)
    missing_apis = sorted(REQUIRED_APIS - enabled)
    require(not missing_apis, f"required APIs are not enabled: {', '.join(missing_apis)}")

    duplicate_findings: list[str] = []

    for item in clusters:
        if item.get("name") == TARGET_NAME:
            duplicate_findings.append(f"GKE cluster:{TARGET_NAME}")

    for item in networks:
        if item.get("name") == TARGET_NAME:
            duplicate_findings.append(f"VPC network:{TARGET_NAME}")

    for item in subnets:
        region = str(item.get("region", ""))
        if item.get("name") == TARGET_NAME and (region.endswith(f"/regions/{REGION}") or region == REGION):
            duplicate_findings.append(f"subnetwork:{TARGET_NAME}")

    for item in service_accounts:
        if item.get("email") == TARGET_NODE_SA:
            duplicate_findings.append(f"service account:{TARGET_NODE_SA}")

    for item in instances:
        labels = item.get("labels") or {}
        if isinstance(labels, dict) and labels.get("terraformers-runtime") == "target":
            duplicate_findings.append(f"target VM:{item.get('name', 'unknown')}")

    require(not duplicate_findings, "unmanaged target runtime already exists: " + ", ".join(duplicate_findings))

    return {
        "project_id": PROJECT_ID,
        "project_number": PROJECT_NUMBER,
        "project_active": True,
        "billing_verified": billing_verification == "verified",
        "billing_enabled": billing.get("billingEnabled") if billing_verification == "verified" else None,
        "billing_note": (
            "billingEnabled=true was verified automatically."
            if billing_verification == "verified"
            else "Cloud Billing status could not be queried with the project-scoped read identity; "
            "confirm billing status together with remaining Free Trial credit at foundation approval."
        ),
        "free_trial_credit_balance_checked": False,
        "free_trial_credit_note": (
            "Promotional credit balance is not exposed by this project-scoped preflight. "
            "The operator confirms remaining Free Trial credit as part of the single foundation approval."
        ),
        "quota": quota_rows,
        "machine_type": {
            "name": machine.get("name"),
            "guest_cpus": machine.get("guestCpus"),
            "memory_mb": machine.get("memoryMb"),
            "zone": ZONE,
        },
        "gke_default_cluster_version": default_version,
        "required_apis": sorted(REQUIRED_APIS),
        "duplicate_runtime_findings": [],
        "status": "passed",
    }


def render_markdown(result: dict[str, Any]) -> str:
    lines = [
        "## GCP target foundation preflight",
        "",
        f"- Project: `{result['project_id']}`",
        "- Project lifecycle: `ACTIVE`",
        (
            "- Billing enabled: `true` (automatically verified)"
            if result["billing_verified"]
            else "- Billing status: `manual confirmation required`"
        ),
        "- Duplicate target runtime: `none`",
        f"- Machine type: `e2-standard-2` in `{ZONE}`",
        f"- GKE default cluster version: `{result['gke_default_cluster_version']}`",
        "- Required APIs: `enabled`",
        "- Preflight status: `PASS`",
        "",
        "> Cloud Billing status is checked automatically only when the existing project-scoped identity "
        "can query it without enabling a new API or gaining billing-account roles. Otherwise billing "
        "status and remaining Free Trial credit are confirmed together at the single protected "
        "foundation approval.",
        "",
        "| Scope | Quota | Limit | Usage | Available | Required available |",
        "| --- | --- | ---: | ---: | ---: | ---: |",
    ]
    for row in result["quota"]:
        lines.append(
            f"| {row['scope']} | `{row['metric']}` | {row['limit']:g} | "
            f"{row['usage']:g} | {row['available']:g} | {row['required_available']:g} |"
        )
    return "\n".join(lines) + "\n"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate mutable GCP foundation preflight evidence.")
    parser.add_argument("--project-json", type=Path, required=True)
    parser.add_argument("--billing-json", type=Path, required=True)
    parser.add_argument("--global-quota-json", type=Path, required=True)
    parser.add_argument("--region-json", type=Path, required=True)
    parser.add_argument("--machine-json", type=Path, required=True)
    parser.add_argument("--gke-server-json", type=Path, required=True)
    parser.add_argument("--services-json", type=Path, required=True)
    parser.add_argument("--clusters-json", type=Path, required=True)
    parser.add_argument("--networks-json", type=Path, required=True)
    parser.add_argument("--subnets-json", type=Path, required=True)
    parser.add_argument("--service-accounts-json", type=Path, required=True)
    parser.add_argument("--instances-json", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    parser.add_argument("--output-md", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        result = validate(args)
    except (PreflightError, ValueError, TypeError, json.JSONDecodeError) as exc:
        print(f"gcp_foundation_preflight=failed\nreason={exc}")
        return 1

    args.output_json.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    args.output_md.write_text(render_markdown(result), encoding="utf-8")
    print("gcp_foundation_preflight=passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
