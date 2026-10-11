# F3 — Opt-in hosted Firebase frontend (repository proposal)

Issue [#285](https://github.com/siamese-lang/terraformers-platform/issues/285) binds this repository-only change to remote main `752a4ec4fda26ed13409299daefb1465bf260b28`. F1 and F2 are merged. F3 adds an opt-in GKE hosted manifest and a static frontend image; it does not activate a public endpoint or authorize F4.

The canonical `infra/kubernetes/overlays/gcp-target` remains private, including its historical fixture identity configuration. Only `infra/kubernetes/overlays/gcp-target-hosted` is the future public deployment input. It replaces that configuration with Firebase-only issuer/JWKS/project and an empty billable UID list. `prod+test` now fails backend startup validation.

F4 must provide an owned hostname, reserved global IP name, authorized Firebase web project, immutable frontend/backend image digests, public build-input provenance, and measured GKE capacity. F4 must separately authorize any image publication, DNS/IP/certificate, ingress/LB billing, IAM/firewall capability, rollout and real-user validation. No F4 live step is authorized by this PR.

Review acceptance: deterministic hosted Kustomize render and route/security assertions; local frontend test/build and Nginx/container checks where executable; focused backend JWT profile test. A rendered placeholder is intentionally nondeployable. Real GKE NEG/TLS behavior and runtime capacity remain unknown until an approved rollout.
