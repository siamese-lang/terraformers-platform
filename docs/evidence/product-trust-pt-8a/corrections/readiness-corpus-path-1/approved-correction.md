# USER-authorized readiness pre-observation correction

Uploaded authority SHA-256 (original bytes): `20cdb58d29f8bb81bc71170e60a3dc7ff1c3f4e735297229594770ca94d3f8c3`.

The following request is preserved with newline normalization only. This repository correction grants no new live execution or merge authority.

USER explicitly approves:

APPROVE_PT8A_READINESS_PREOBSERVATION_CORPUS_PATH_CORRECTION_ONLY

This is a human-authorized repository-only correction after the preserved natural readiness failure:

run:
37704835680

job:
113076606857

attempt:
1

source/main:
a0d1d5e82dd872c20b92b46130a331a390efef6a

failure:
PRE_OBSERVATION_READINESS_CORPUS_DIRECTORY_CONTRACT_MISMATCH

Independent failure review comment:
6049279548

Clean-v4 provenance that had already completed successfully before this readiness failure:

clean run:
37669558088

clean artifact:
11504143498

clean artifact digest:
sha256:ff35aa09327926a01c6f72cf69e1215c0fc1eb83214cfa9d978abe2f2c55a72f

Do not perform any live action in this task.

## Start gate

Use remote `main` repository evidence as authoritative execution base:

a0d1d5e82dd872c20b92b46130a331a390efef6a

Codex isolated/local checkout SHA may differ because of synthetic checkout behavior.

Do NOT require local synthetic SHA equality with remote main.

Do NOT fail the task merely because local checkout SHA is synthetic.

Bind the correction to the authoritative remote main and repository provenance.

If remote main has moved from the SHA above, STOP with MAIN_DRIFT evidence.

## 1. Preserve the exact natural failure

Run 37704835680 must remain immutable failed evidence.

Observed execution:

- workflow: GCP Target Runtime Dependencies
- operation: pt8a-official-acceptance
- mode: readiness
- run attempt: 1
- head SHA: a0d1d5e82dd872c20b92b46130a331a390efef6a
- preflight: PASS
- WIF/GCP auth: PASS
- exact deployed release/runtime identity: PASS
- failing step:
  Rebuild expected corpus from pinned authority without embedding
- observed build output:
  corpusVersion=terraformers-reference-v4
  documentCount=5395
  selectedResourceCount=1514
- observed failure:
  RAG corpus contract failure: corpus directory name must match manifest corpusVersion
- validation pod creation: NOT_RUN
- product observation: NOT_RUN
- JWKS fixture prepare: NOT_RUN
- product request/upload: 0
- official AWS candidate fetch/upload: 0
- model inference: 0
- official cases A-E: NOT_RUN
- artifact count: 0
- rerun/retry: 0

This failure is repository workflow orchestration, not product/model/RAG quality failure.

## 2. Correct only the readiness corpus directory contract

Primary file:

.github/workflows/gcp-target-runtime-dependencies.yml

Current defective PT-8A readiness path uses:

$RUNNER_TEMP/expected-v4

for:

- build-corpus-v4.py --output-dir
- rag-corpus-contract-verification.py --corpus-dir
- coverage-report.json readback
- pt8a-official-acceptance.py --corpus

The committed contract verifier explicitly requires:

manifest corpusVersion:
terraformers-reference-v4

directory basename:
v4

because scripts/checks/rag-corpus-contract-verification.py validates:

corpus.name == "v4"

Minimal correction:

Use one canonical variable/path for the rebuilt readiness corpus, with basename `v4`.

Preferred shape:

```bash
expected_corpus_dir="$RUNNER_TEMP/v4"
```

and use that exact variable consistently for:

- `build-corpus-v4.py --output-dir`
- `rag-corpus-contract-verification.py --corpus-dir`
- `coverage-report.json`
- observer input to `pt8a-official-acceptance.py run --corpus`

Avoid scattered literal replacements if a single env/output variable makes drift less likely.

Do NOT weaken or remove the checker directory-name contract.

Do NOT change `rag-corpus-contract-verification.py` merely to permit `expected-v4`.

The workflow is wrong; the checker is not.

## 3. Add deterministic regression for the real readiness path

Existing tests should be extended proportionally. Do not introduce a new test framework or another workflow/verifier service.

Add deterministic repository-only coverage that proves the PT-8A readiness workflow:

- rebuilds into a path whose basename is `v4`;
- passes that same path to `rag-corpus-contract-verification.py`;
- reads coverage from that same path;
- passes that same path to `pt8a-official-acceptance.py --corpus`;
- cannot regress back to `$RUNNER_TEMP/expected-v4`.

The regression must exercise the actual workflow contract rather than merely assert a text token if a stronger lightweight deterministic check is practical.

At minimum, parse the workflow and inspect/extract the relevant `run` blocks.

Also add a local deterministic corpus-path validation using a temporary directory named `v4` if existing test fixtures make this practical.

Do NOT fetch AWS/provider sources from the network in unit tests.

Do NOT invoke GCP/OpenSearch/model APIs.

## 4. Correct the PT-8A history semantics for this exact pre-observation failure

This is required.

Current `scripts/evaluation/pt8a-official-acceptance.py::ensure_latest_dispatch()` treats any prior non-skipped PT-8A run with no artifact as:

`prior PT8A evidence unavailable; no upload or resubmission`

That rule is correct for an execution that could have consumed product observation or whose acceptance state is ambiguous.

However run 37704835680 failed deterministically before:

- validation pod creation,
- JWKS preparation,
- product upload/request,
- accepted job creation,
- model inference,
- readiness evidence directory creation.

Therefore simply fixing the path while leaving `ensure_latest_dispatch()` unchanged would make the corrected readiness permanently unable to run.

Implement the smallest fail-closed exception necessary for this exact class:

A prior PT-8A readiness run may be ignored for once-only product-consumption history ONLY when repository/GitHub evidence proves all of the following:

- same exact reviewed source lineage relevant to that historical run;
- event workflow_dispatch;
- run attempt exactly 1;
- run completed;
- exactly one `pt8a-official-acceptance` job;
- that job conclusion is failure;
- no PT-8A artifact exists;
- `Start owned validation pod with the already deployed immutable image` was skipped;
- `Observe read-only exact readiness and at most one accepted product job` was skipped;
- failure occurred in the deterministic corpus rebuild/contract step before observation;
- no product observation may have occurred.

Do not create a generic “failed run may retry” bypass.

Do not permit a prior run with:

- observation step started,
- validation pod started if product-observation ambiguity exists,
- artifact missing after observation,
- accepted product request ambiguity,
- rerun attempt >1,
- unknown job history,
- incomplete job list,

to be ignored.

Those must remain fail-closed.

Prefer a narrowly named helper such as classification of a pre-observation non-consuming readiness failure, with deterministic unit tests.

## 5. Add deterministic history tests

Add unit tests around `ensure_latest_dispatch()` proving at least:

PASS / ignorable history case:

- prior run equivalent to 37704835680
- attempt 1
- completed failure
- PT8A job failure
- rebuild step failure
- validation-pod step skipped
- observe step skipped
- zero PT8A artifact

=> a replacement readiness dispatch is allowed to reach normal preflight/history evaluation.

FAIL-CLOSED cases:

- observe step success/failure/started rather than skipped + artifact missing
- validation pod/observe history ambiguous
- run attempt 2
- run incomplete
- PT8A job count != 1
- missing or >100 job history
- artifact history inconsistent
- case-mode request trying to bypass prior readiness artifact requirements

Do not weaken once-only official case semantics.

## 6. Preserve frozen PT-8A material

The following MUST remain byte-identical:

docs/evaluation/product-trust-pt-8a-official-acceptance-procedure.md

Expected SHA256:

d087508c99a267b36e2c7a2ce70bb69ca9b62d161a95de79ff1fa30e9d3b4823

Do not modify:

- evaluation/terraformers-aws-official-v1 candidate bytes
- candidate identity/order
- official truth
- clean-v4 ingestion algorithm
- scripts/rag/ingest-gcp-target-corpus.py semantics
- embedding model
- vector dimension
- frozen corpus checksum
- provider source
- project-decision source
- scoring dimensions
- product observation semantics
- once-only A-E case ordering
- backend runtime behavior

Frozen values remain:

candidate_identity:
3e105771401051e0b199d8f79b3b993b161f27a03beeb0fb2d223f460d9a0757

corpus_checksum:
da410626b80d8624e93c8a8da81206a2ed8da1b06086a75279d5646b068bd66a

provider_source:
f7a3b98da589ab1d52756b0dcee0dbf2de83d635

project_decision_source:
1ae69d589ac3965733818819792d98c5638e0ae5

embedding_model:
gemini-embedding-2

vector_dimension:
1536

document_count:
5395

## 7. Preserve failure evidence durably

Under:

docs/evidence/product-trust-pt-8a/**

record run 37704835680 as a pre-observation, non-consuming readiness failure.

Include at least:

- run ID 37704835680
- job ID 113076606857
- source SHA
- attempt 1
- exact failing step
- exact observed contract error
- preflight PASS
- runtime identity PASS
- validation pod NOT_RUN
- observe step NOT_RUN
- official input fetch/upload 0
- product accepted job count 0
- model inference 0
- artifact 0
- rerun 0
- cases A-E NOT_RUN
- correction authority:
  APPROVE_PT8A_READINESS_PREOBSERVATION_CORPUS_PATH_CORRECTION_ONLY

Do not fabricate an artifact ID or readiness classification for this failed run.

## 8. Governance bookkeeping

Update existing PT-8A state/Work Package only as necessary to record:

- this is USER-authorized human corrective iteration;
- failure class:
  PRE_OBSERVATION_READINESS_CORPUS_DIRECTORY_CONTRACT_MISMATCH
- autonomous repair counters remain unchanged;
- run 37704835680 remains immutable;
- official cases remain NOT_RUN;
- clean-v4 success on the historical source is preserved as historical evidence;
- no new live execution is authorized by this repository PR;
- independent review + USER merge required.

Do not reset repair counters.

Do not claim readiness PASS.

Do not claim PT-8A complete.

## 9. Deterministic repository-only validation

Run proportional checks before opening PR.

Required:

- YAML parse PASS.
- Relevant workflow shell blocks `bash -n` PASS.
- PT-8A workflow regression for canonical `v4` path PASS.
- `rag-corpus-contract-verification.py` path contract regression PASS.
- `ensure_latest_dispatch()` pre-observation failure classification tests PASS.
- fail-closed ambiguous-history tests PASS.
- existing PT-8A/RAG relevant tests PASS.
- `git diff --check` PASS.
- procedure SHA256 remains exactly:
  d087508c99a267b36e2c7a2ce70bb69ca9b62d161a95de79ff1fa30e9d3b4823
- candidate/truth frozen blobs unchanged.

Inspect the final diff for any unrelated semantic changes.

Normal PR CI is allowed.

Do not manually rerun failed CI.

## 10. PR boundary

Create exactly ONE repository-only correction PR.

No live actions.

Prohibited:

- workflow_dispatch
- rerun of 37704835680
- replacement readiness dispatch
- image publication
- backend rollout
- clean-v4 execution
- Vertex/Gemini calls
- OpenSearch mutation
- official AWS input fetch
- Case A-E
- PT-8B
- teardown
- Terraform apply
- IAM/ruleset mutation
- self-review acceptance
- merge

Return:

- branch
- commit SHA
- PR number / URL
- exact changed files
- root cause
- exact `v4` path correction
- exact history exception semantics
- deterministic validation results
- procedure hash read-back
- confirmation candidate/truth/clean-v4 semantics unchanged
- confirmation run 37704835680 remains preserved
- confirmation no live action occurred

Then STOP for independent acceptance review.