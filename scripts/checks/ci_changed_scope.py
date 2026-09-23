#!/usr/bin/env python3
"""Classify changed files for expensive milestone verification jobs."""

from __future__ import annotations

import argparse
import subprocess
from pathlib import Path


WORKFLOW_OUTPUTS = {
    "m1": ("boundary_contract", "backend_regression", "mariadb_regression", "frontend_regression", "runtime_contract"),
    "m2-baseline": ("kind_local_stub_baseline",),
    "m2-persistent": ("portable_persistent_runtime",),
    "m2-authenticated": ("authenticated_identity_parity",),
    "terraform-static": ("terraform_static_verification",),
    "aws-runtime-package": ("aws_runtime_deployment_package",),
}


def under(path: str, prefix: str) -> bool:
    return path.startswith(prefix.rstrip("/") + "/")


def changed_paths(base: str, head: str, cwd: Path | None = None) -> list[str]:
    """Return paths changed by the PR branch since its merge base."""
    return subprocess.run(
        ["git", "diff", "--name-only", "--no-renames", f"{base}...{head}", "--"],
        check=True,
        cwd=cwd,
        text=True,
        stdout=subprocess.PIPE,
    ).stdout.splitlines()


def classify(paths: list[str]) -> dict[str, dict[str, bool]]:
    result = {workflow: {name: False for name in outputs} for workflow, outputs in WORKFLOW_OUTPUTS.items()}
    for path in paths:
        backend_main = under(path, "backend/src/main")
        frontend = under(path, "frontend")
        base = under(path, "infra/kubernetes/base")
        aws_overlay = under(path, "infra/kubernetes/overlays/aws-runtime-template")

        m1 = result["m1"]
        m1["boundary_contract"] |= (
            backend_main
            or under(path, "frontend/src")
            or under(path, "frontend/public")
            or path == "frontend/.env.example"
            or path in {"backend/pom.xml", "backend/Dockerfile", "frontend/package.json", "frontend/package-lock.json"}
            or path in {"scripts/checks/m1-cloud-decoupling-closure-verification.py", ".github/workflows/m1-cloud-decoupling-closure-verification.yml"}
        )
        m1["backend_regression"] |= under(path, "backend") or path in {
            "scripts/checks/backend-local-verification.sh",
            ".github/workflows/m1-cloud-decoupling-closure-verification.yml",
        }
        filename = Path(path).name
        m1["mariadb_regression"] |= (
            under(path, "backend/src/main/resources/db/migration")
            or (backend_main and (filename.endswith("Entity.java") or filename.endswith("Repository.java")))
            or path in {
                "backend/pom.xml",
                "backend/src/main/resources/application-prod.yml",
                "scripts/checks/mariadb-schema-validation.sh",
                ".github/workflows/m1-cloud-decoupling-closure-verification.yml",
            }
        )
        m1["frontend_regression"] |= frontend or path == ".github/workflows/m1-cloud-decoupling-closure-verification.yml"
        m1["runtime_contract"] |= (
            path in {
                "backend/src/main/resources/application-prod.yml",
                "backend/src/main/resources/application-aws-compat.yml",
                "scripts/checks/runtime-contract-verification.sh",
                ".github/workflows/m1-cloud-decoupling-closure-verification.yml",
            }
            or base
            or aws_overlay
        )

        result["m2-baseline"]["kind_local_stub_baseline"] |= (
            base
            or under(path, "infra/kubernetes/overlays/local-stub")
            or path in {"scripts/checks/kind-local-stub-smoke.sh", ".github/workflows/m2-runtime-parity-baseline.yml"}
        )
        result["m2-persistent"]["portable_persistent_runtime"] |= (
            backend_main
            or path in {"backend/pom.xml", "backend/Dockerfile", "backend/src/test/java/com/terraformers/modernization/verification/MariaDbRepositorySmokeTest.java"}
            or base
            or under(path, "infra/kubernetes/overlays/portable-persistent")
            or path in {"scripts/checks/kind-portable-persistent-smoke.sh", ".github/workflows/m2-portable-persistent-runtime-verification.yml"}
        )
        result["m2-authenticated"]["authenticated_identity_parity"] |= (
            backend_main
            or path in {"backend/pom.xml", "backend/Dockerfile"}
            or base
            or under(path, "infra/kubernetes/overlays/portable-persistent")
            or under(path, "infra/kubernetes/overlays/portable-authenticated")
            or path in {
                "scripts/checks/kind-portable-authenticated-smoke.sh",
                ".github/workflows/m2-authenticated-identity-parity-verification.yml",
            }
        )
        result["terraform-static"]["terraform_static_verification"] |= (
            under(path, "infra/terraform")
            or under(path, "corpus/terraformers-reference")
            or under(path, "scripts/rag")
            or under(path, "scripts/teardown")
            or under(path, "tests/rag")
            or path in {
                "config/runtime-teardown-stages.json",
                "scripts/checks/rag-corpus-contract-verification.py",
                "scripts/checks/terraform-static-verification.sh",
                "scripts/checks/approved-terraform-apply-contract-verification.py",
                "scripts/deploy/verify-approved-terraform-apply-contract.py",
                "scripts/deploy/summarize-terraform-plan.py",
                ".github/workflows/aws-live-terraform-apply.yml",
                ".github/workflows/aws-terraform-destroy-plan.yml",
                ".github/workflows/aws-runtime-teardown.yml",
                ".github/workflows/terraform-static-verification.yml",
            }
        )
        result["aws-runtime-package"]["aws_runtime_deployment_package"] |= (
            base
            or aws_overlay
            or path in {
                ".github/workflows/aws-runtime-deployment-package-verification.yml",
                "scripts/checks/aws-runtime-deployment-package-verification.sh",
                "scripts/deploy/build-aws-runtime-deployment-package.sh",
                "scripts/deploy/build-aws-runtime-input-bundle.py",
                "scripts/deploy/render-backend-runtime-secret.sh",
                "scripts/deploy/render-aws-runtime-manifest.sh",
                "scripts/deploy/aws-runtime-deploy-preflight.sh",
                "docs/aws-runtime-deployment-package.md",
            }
        )
        if path == "scripts/checks/ci_changed_scope.py":
            for workflow in result.values():
                workflow.update(dict.fromkeys(workflow, True))
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--workflow", choices=WORKFLOW_OUTPUTS, required=True)
    parser.add_argument("--base")
    parser.add_argument("--head")
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--github-output", type=Path)
    args = parser.parse_args()
    if args.all:
        selected = {name: True for name in WORKFLOW_OUTPUTS[args.workflow]}
    else:
        if not args.base or not args.head:
            parser.error("--base and --head are required unless --all is used")
        changed = changed_paths(args.base, args.head)
        selected = classify(changed)[args.workflow]
    output = "".join(f"{name}={'true' if run else 'false'}\n" for name, run in selected.items())
    if args.github_output:
        with args.github_output.open("a", encoding="utf-8") as stream:
            stream.write(output)
    else:
        print(output, end="")


if __name__ == "__main__":
    main()
