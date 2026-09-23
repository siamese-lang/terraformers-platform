#!/usr/bin/env python3
"""Classify the three known provider-specific strings in active generic UI files."""

from __future__ import annotations

import argparse
from pathlib import Path


CHECKS = (
    ("public_identity_copy_provider_neutral", Path("frontend/src/components/PublicProjectsReadOnly.js"), "Cognito 로그인 사용자"),
    ("analysis_waiting_copy_provider_neutral", Path("frontend/src/pages/ProjectDetailPage.js"), "Bedrock 모델의 응답을 기다리고 있습니다."),
    ("project_tree_locator_provider_neutral", Path("frontend/src/components/ProjectTreeReadOnly.js"), "s3://${node.sourceBucket}/${node.sourceKey}"),
)


def classify(root: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for name, relative_path, provider_copy in CHECKS:
        source = (root / relative_path).read_text(encoding="utf-8")
        result[name] = "FAIL" if provider_copy in source else "PASS"
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--self-check", action="store_true")
    args = parser.parse_args()
    if args.self_check:
        assert len({name for name, _, _ in CHECKS}) == 3
        assert all(path.parts[:2] == ("frontend", "src") for _, path, _ in CHECKS)
        print("classifier_self_check=PASS")
        return 0
    result = classify(args.root)
    for name, status in result.items():
        print(f"{name}={status}")
    count = sum(value == "FAIL" for value in result.values())
    print(f"known_provider_specific_visible_copy={count}")
    print(f"first_confirmed_gap={'frontend_provider_specific_visible_copy' if count else 'none'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
