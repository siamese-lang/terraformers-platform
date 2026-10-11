# GCP Backend + React browser preview (development only)

This is an **existing-service** development preview, not an internet-facing GCP frontend deployment or an official PT-8A evaluation run. It uses the actual React Generate/Projects screens, existing `/api/upload` and result/status APIs, the already-deployed private GKE backend, and `scripts/smoke/ephemeral-jwks-fixture.sh`. It does **not** create a new evaluator, cloud load balancer, Cloud Run service, public Ingress, or identity provider.

## What is already deployed / what is not

- The GCP target contains the Backend and its private `ClusterIP` service, MariaDB, OpenSearch, GCS adapters and Vertex AI connections. Previously observed B–E jobs ran against that backend.
- GCP has **no confirmed publicly hosted React frontend**. The existing frontend CloudFront/S3 delivery targets historical AWS infrastructure and its normal browser authentication uses Cognito, not the temporary GCP fixture.
- PR #277 merge `435ca32d4ef194f512c3019925acaac19507b7af` changed backend generation/repair. **A merge is not a deployment.** First read the running Backend `BUILD_SOURCE_REVISION`; it must equal the explicitly expected merged SHA for this preview. If stale, STOP. Use the existing independently reviewed `gcp-backend-image-publish.yml` and `backend-revision-rollout` sequence with separate release approval. The preview script does not publish or roll out images.
- The GCP fixture identity provider is a **placeholder JWKS** (`{"keys":[]}`). Browser authentication is impossible until a temporary, valid, signed test token and matching JWKS have been authorized and prepared. This is *not* a permanent user-login solution.

## Before running (read-only)

Use Google Cloud Shell and this repository at the commit containing the browser-preview change. Do not use a GitHub Actions runner or a public third-party workstation.

```bash
gcloud config set project terraformers-platform
gcloud container clusters get-credentials terraformers-target \
  --zone asia-northeast3-a --project terraformers-platform
kubectl -n terraformers-target get deployment terraformers-backend
kubectl -n terraformers-target get service terraformers-backend
kubectl -n terraformers-target exec deployment/terraformers-backend -c backend -- \
  printenv BUILD_SOURCE_REVISION
kubectl -n terraformers-target get configmap terraformers-jwks \
  -o jsonpath='{.data.jwks\.json}'
```

The deployment must have one Ready Backend, the expected source revision, and the JWKS must be exactly the empty fixture placeholder. Any mismatch is **STOP**, not permission to redeploy or alter authentication.

## Authorized local browser test (temporary JWKS mutation)

**Requires separate explicit operator approval** to replace the placeholder JWKS ConfigMap temporarily and restart only the existing test JWKS Deployment. Do not approve or execute if another test is concurrently using the fixture. No real production users/IdP are involved. The Backend's existing JWT signature/issuer/audience checks remain enabled.

After the #277 Backend image is actually deployed and independently verified, and the operator explicitly authorizes the temporary fixture change:

```bash
GCP_BROWSER_PREVIEW_CONFIRMATION=APPROVE_TEMPORARY_GCP_JWKS_BROWSER_PREVIEW \
  bash scripts/dev/gcp-browser-preview.sh
```

The script uses the existing GKE cluster, checks the expected running Backend SHA (default `435ca32d4ef194f512c3019925acaac19507b7af`), installs existing React dependencies locally, reuses the existing signed short-lived JWKS fixture, port-forwards the private Backend to `localhost:8080`, and starts React's development server on port 3000. **No new public Backend endpoint is created.**

Open Cloud Shell **Web Preview → Preview on port 3000**; go to `/login`. This development-only screen asks for the fixture's **access token**, not a password. Its private path is printed by the script. In a second authenticated Cloud Shell terminal, read that file locally and paste its value into the field. Never post the token to GitHub, a chat, screenshots, public logs or environment variables. Only the existing Backend verifies the JWT signature. The UI's preliminary payload decoding is not a signature check.

Use `/generate` to upload an architecture PNG/JPEG and review `/projects/<id>` after polling. Check Backend logs and the owner-scoped diagnostics by job ID if it fails. Each actual image upload invokes the paid Vertex service, so operator approval for model usage is separate from opening the preview.

**Close the preview command to restore JWKS.** It is automatically bounded to 13 minutes (the token expires after 15 minutes) and attempts to restore the exact previous placeholder and remove private keys/tokens on every normal termination. Confirm cleanup:

```bash
kubectl -n terraformers-target get configmap terraformers-jwks \
  -o jsonpath='{.data.jwks\.json}' | jq -e '.keys == []'
```

If restoration reports failure, STOP further tests; inspect the ConfigMap and JWKS Deployment before proceeding. Never leave a temporary signer enabled unattended.

## Production boundary / remaining work

This development preview does **not** mean the React site is deployed to GCP. A durable public website still needs a separate approved HTTPS frontend distribution/API origin, a real supported external identity provider, browser-to-Backend JWT compatibility, DNS/security configuration, and cost review. Do not adapt the temporary test signer into public authentication. All existing Cognito production-mode frontend behavior remains untouched.

For repeated engineering tests, use this direct browser/API/log flow rather than creating another per-case GitHub Actions measurement episode. Official PT-8A acceptance, paid model observations and the old A–E consumed results remain separate.
