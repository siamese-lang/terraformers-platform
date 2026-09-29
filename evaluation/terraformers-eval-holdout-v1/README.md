# Terraformers evaluation holdout v1

Frozen Case A holdout using the unchanged `m3-evaluation-v1` schema. It contains two synthetic positive architecture diagrams (`holdout-eks-irsa`, `holdout-workload-rds-sg`), one ambiguous cropped control (`holdout-ambiguous-obscured-flow`), and one non-architecture operational control (`holdout-non-architecture-status-board`).

Fixture bytes are repository-owned synthetic WebP images and their SHA-256 identities are pinned in `dataset.json`. This dataset is an unseen regression boundary for later approved Case A alternatives; it does not replace or modify `terraformers-eval-v1` and must not be tuned after results are observed.
