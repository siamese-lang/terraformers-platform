#!/usr/bin/env python3
"""Verify M2 closure from accepted evidence and lightweight current-source invariants."""

from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "artifacts/m2-runtime-parity-closure"

EVIDENCE = {
    "m2-runtime-parity-baseline.md": ("2623303347e15e580d83f583262c51d2573edbbd",),
    "m2-portable-persistent-runtime.md": ("02a00efec167a92c8170e8787b68e28ce6dc2339",),
    "m2-authenticated-identity-parity.md": ("eda4b9469f258b71aae8e28a0b3ec1a226c4412a",),
    "m2-upload-analysis-baseline.md": ("Status: PASS", "first_confirmed_gap=source_binary_not_persisted"),
    "m2-object-byte-storage-parity.md": ("99fa2864ae3ef86536332172c4a3e50be612e686",),
    "m2-user-experience-baseline.md": (
        "a756c46efe88124528b5c0467c5e8c1e4ac461fb",
        "7d8cdf357cb16a9073c86c6eb5140e617219180d",
        "known_provider_specific_visible_copy=0",
    ),
}

LIMITATIONS = [
    "active production LLM quality", "active production retrieval quality",
    "fixed AI evaluation dataset", "model quality improvement", "production IdP selection",
    "production object storage selection", "GCP runtime deployment",
    "filesystem persistence across pod recreation", "shared multi-replica object storage",
    "process restart recovery", "duplicate execution handling", "executor saturation",
    "transactional failure recovery", "load/performance targets", "observability root-cause analysis",
    "production HA/backup", "browser-specific rendering", "cross-browser", "accessibility E2E",
]

CRITERION_NAMES = [
    "Portable runtime startup is repeatable",
    "MariaDB + Flyway persistence contract is verified in an actual runtime",
    "Authenticated identity to internal user to ownership semantics are preserved",
    "Architecture/image upload leads to the project/source-file contract",
    "Analysis job lifecycle produces a terminal result",
    "Terraform validation, result registration, and read-back are verified",
    "Core user/project/comment contracts are preserved",
    "Required frontend user-flow evidence is repeatable",
    "Runtime configuration and identity evidence is preserved",
    "Provider-neutral application boundary remains intact",
    "Historical AWS implementation is not restored as the active target",
    "No arbitrary GCP product or topology is selected",
    "Residual limitations are explicit",
    "Consolidated M2 closure evidence exists",
]


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        raise FileNotFoundError(relative)
    return path.read_text(encoding="utf-8")


def run_check(relative: str) -> tuple[int, str]:
    completed = subprocess.run(
        [sys.executable, str(ROOT / relative)], cwd=ROOT, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False,
    )
    return completed.returncode, completed.stdout


def main() -> int:
    failures: list[str] = []
    plan = read("docs/plans/active/M2-runtime-parity.md")
    report = read("docs/verification/m2-runtime-parity-closure.md")

    for task in range(1, 6):
        pattern = rf"### M2-{task}\b.*?\*\*Status: DONE\*\*"
        if not re.search(pattern, plan, re.DOTALL):
            failures.append(f"active plan does not mark M2-{task} DONE")
    if not re.search(r"### M2-6\b.*?\*\*Status: TODO\*\*", plan, re.DOTALL):
        failures.append("active plan does not preserve M2-6 TODO")

    evidence_ok = True
    for filename, markers in EVIDENCE.items():
        try:
            content = read(f"docs/verification/{filename}")
        except FileNotFoundError:
            failures.append(f"missing evidence: {filename}")
            evidence_ok = False
            continue
        if not re.search(r"(?:\*\*)?Status(?:\*\*)?:?[\s\n*]{0,24}PASS", content, re.IGNORECASE):
            failures.append(f"accepted PASS status missing: {filename}")
            evidence_ok = False
        for marker in markers:
            if marker not in content:
                failures.append(f"evidence marker missing from {filename}: {marker}")
                evidence_ok = False

    m1_code, m1_output = run_check("scripts/checks/m1-cloud-decoupling-closure-verification.py")
    m1_required = {
        "m1_cloud_decoupling_boundary=passed",
        "historical_aws_adapters_preserved=true",
        "gcp_provider_selected=false",
    }
    boundary_ok = m1_code == 0 and all(marker in m1_output for marker in m1_required)
    if not boundary_ok:
        failures.append("current M1 provider-neutral boundary verification failed")

    frontend_code, frontend_output = run_check("scripts/checks/m2-frontend-experience-baseline.py")
    frontend_required = {
        "public_identity_copy_provider_neutral=PASS",
        "analysis_waiting_copy_provider_neutral=PASS",
        "project_tree_locator_provider_neutral=PASS",
        "known_provider_specific_visible_copy=0",
        "first_confirmed_gap=none",
    }
    frontend_ok = frontend_code == 0 and all(marker in frontend_output for marker in frontend_required)
    if not frontend_ok:
        failures.append("current frontend provider-neutral classifier failed")

    overlay = read("infra/kubernetes/overlays/portable-object-store/backend-configmap-patch.yaml")
    prod = read("backend/src/main/resources/application-prod.yml")
    overlay_filesystem = all(
        re.search(rf"{key}:\s*[\"']?filesystem[\"']?", overlay)
        for key in ("OBJECT_READER_PROVIDER", "OBJECT_WRITER_PROVIDER")
    )
    production_neutral = (
        "${OBJECT_READER_PROVIDER:disabled}" in prod
        and "${OBJECT_WRITER_PROVIDER:metadata-only}" in prod
        and not re.search(r"OBJECT_(?:READER|WRITER)_PROVIDER:filesystem", prod)
    )
    storage_ok = overlay_filesystem and production_neutral
    if not storage_ok:
        failures.append("filesystem storage is not constrained to the explicit test overlay")

    headings = [int(value) for value in re.findall(r"^### (\d+)\.", report, re.MULTILINE)]
    limitations_ok = all(term in report for term in LIMITATIONS)
    report_ok = headings == list(range(1, 15)) and "**Status: PENDING**" in report and limitations_ok
    if not report_ok:
        failures.append("closure report lacks PENDING status, ordered 14 criteria, or required limitations")

    common_evidence = [f"docs/verification/{name}" for name in EVIDENCE]
    criterion_ok = [
        evidence_ok, evidence_ok, evidence_ok, evidence_ok, evidence_ok, evidence_ok, evidence_ok and frontend_ok,
        frontend_ok, evidence_ok, boundary_ok, boundary_ok, boundary_ok, limitations_ok, report_ok,
    ]
    criteria = [
        {"id": index, "name": name, "result": "PASS" if criterion_ok[index - 1] else "FAIL",
         "evidence": common_evidence if index <= 9 else ["docs/verification/m2-runtime-parity-closure.md"]}
        for index, name in enumerate(CRITERION_NAMES, 1)
    ]
    passed = sum(item["result"] == "PASS" for item in criteria)
    result = "PASS" if not failures and passed == 14 else "FAIL"

    OUTPUT.mkdir(parents=True, exist_ok=True)
    summary = [
        f"m2_runtime_parity_closure={result}", f"exit_criteria_passed={passed}",
        "exit_criteria_total=14", f"m2_1_through_m2_5_complete={str(not any('active plan' in f for f in failures)).lower()}",
        f"portable_runtime_evidence={str(evidence_ok).lower()}",
        f"mariadb_flyway_evidence={str(evidence_ok).lower()}",
        f"authenticated_identity_evidence={str(evidence_ok).lower()}",
        f"upload_analysis_result_evidence={str(evidence_ok).lower()}",
        f"object_byte_persistence_evidence={str(evidence_ok).lower()}",
        f"user_project_comment_evidence={str(evidence_ok).lower()}",
        f"frontend_contract_evidence={str(frontend_ok).lower()}",
        f"provider_neutral_boundary={str(boundary_ok).lower()}",
        f"filesystem_test_runtime_only={str(storage_ok).lower()}",
        f"historical_aws_active_target={str(not boundary_ok).lower()}",
        f"gcp_runtime_product_selected={str(not boundary_ok).lower()}",
        "browser_e2e_required_for_m2=false", "cloud_credentials_required=false",
        "known_provider_specific_visible_copy=0" if frontend_ok else "known_provider_specific_visible_copy=unknown",
        "first_confirmed_gap=none" if result == "PASS" else f"first_confirmed_gap={failures[0]}",
    ]
    (OUTPUT / "summary.txt").write_text("\n".join(summary) + "\n", encoding="utf-8")
    payload = {"milestone": "M2", "result": result, "criteria": criteria,
               "residualLimitations": LIMITATIONS, "failures": failures}
    (OUTPUT / "exit-criteria.json").write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    table = ["# M2 exit criteria", "", f"Result: **{result}**", "", "| ID | Criterion | Result |", "|---:|---|---|"]
    table.extend(f"| {item['id']} | {item['name']} | {item['result']} |" for item in criteria)
    (OUTPUT / "exit-criteria.md").write_text("\n".join(table) + "\n", encoding="utf-8")
    boundary_lines = ["[m1-provider-neutral-boundary]", m1_output.rstrip(), "", "[frontend-provider-neutral-classifier]", frontend_output.rstrip(), "", f"filesystem_test_runtime_only={str(storage_ok).lower()}"]
    (OUTPUT / "boundary-summary.txt").write_text("\n".join(boundary_lines) + "\n", encoding="utf-8")

    print("\n".join(summary))
    for failure in failures:
        print(f"failure={failure}", file=sys.stderr)
    return 0 if result == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
