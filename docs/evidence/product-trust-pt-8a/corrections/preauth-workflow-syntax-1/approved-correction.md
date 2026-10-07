USER explicitly approves:

APPROVE_PT8A_CLEAN_V4_PREAUTH_WORKFLOW_SYNTAX_CORRECTION_ONLY

This is a human-authorized repository-only correction after the preserved natural failure:

run:
37662854852

job:
112934503049

attempt:
1

source/main:
dff2995aa1047d76f3917e4c7015aebb0faf2212

failure:
PRE_AUTH_WORKFLOW_SHELL_SYNTAX_FAILURE

Observed primary error:

Validate trusted ingestion request:
here-document at line 21 delimited by end-of-file (wanted `PY`)
line 58: syntax error: unexpected end of file

Observed evidence-step error:

Prepare bounded ingestion evidence:
here-document at line 10 delimited by end-of-file (wanted `PY`)
line 21: syntax error: unexpected end of file

Consequences already independently verified:

- google-github-actions/auth was skipped
- GKE/OpenSearch access: 0
- embedding requests: 0
- vector overwrites: 0
- partial vector mutation: none
- index rebuild/delete/upsert: none
- receipt: unavailable
- artifact count: 0
- retry/rerun: 0
- readiness/cases: not executed

Proceed with exactly one bounded repository correction PR.

Do NOT execute any live workflow.

## 1. Fix only the two defective heredoc shell blocks

File:

.github/workflows/gcp-target-corpus-ingestion.yml

Affected steps only:

- Validate trusted ingestion request
- Prepare bounded ingestion evidence

Root cause:

The YAML block scalar base indentation is 10 spaces, while the nested heredoc Python body and terminating `PY` are currently stored with 12+ spaces.

After YAML scalar indentation removal, the runner receives leading spaces before the heredoc terminator, so Bash never recognizes `PY` at column 0 and consumes input until EOF.

Correct the heredocs so that the shell receives:

```bash
python3 ... <<'PY'
<python at valid top-level indentation>
PY
```

with the terminating `PY` at shell column 0.

Do not replace this with a materially different mechanism or refactor unrelated workflow logic.

The Python content/behavior itself must remain semantically unchanged.

## 2. Correct the deterministic test gap that falsely accepted syntax failure

File:

tests/rag/test_ingest_gcp_target_corpus.py

The existing test:

test_existing_workflow_rejects_old_token_and_v3_clean_before_cloud_auth

currently accepts any non-zero return code, so Bash syntax exit 2 incorrectly satisfies the intended invalid-input rejection.

Strengthen the existing test, rather than adding a new verifier/workflow.

At minimum:

- syntax-check the affected extracted workflow `run` blocks with `bash -n`;
- require syntax validation to return 0;
- for the existing invalid-input executions, prove they fail for the intended pre-auth contract rather than shell parsing;
- reject stderr containing `syntax error`;
- reject stderr containing heredoc EOF/wanted-`PY` warnings;
- preferably assert the expected semantic failure exit is 1 where stable.

Cover both affected run blocks:

- Validate trusted ingestion request
- Prepare bounded ingestion evidence

Do not execute cloud/auth/model commands during tests.

## 3. Preserve the failed live evidence durably

Under the existing allowed path:

docs/evidence/product-trust-pt-8a/**

record the failure disposition for run 37662854852.

It must preserve at least:

- run ID: 37662854852
- job ID: 112934503049
- run attempt: 1
- source SHA: dff2995aa1047d76f3917e4c7015aebb0faf2212
- workflow conclusion: failure
- failing steps and exact failure class
- auth not reached
- GCP/OpenSearch not reached
- embedding/vector mutation count 0
- receipt unavailable
- artifact count 0
- rerun/retry 0
- clean-v4 lineage remains MODEL_PROVENANCE_UNPROVEN

Do not invent UUID/content identity/receipt values that were never observed.

## 4. Governance bookkeeping may record this human correction only

You may update the existing PT-8A Work Package/state/evidence bookkeeping only as needed to record:

- USER authority:
  APPROVE_PT8A_CLEAN_V4_PREAUTH_WORKFLOW_SYNTAX_CORRECTION_ONLY
- this is a human-authorized correction, not an autonomous repair;
- historical autonomous repair counters remain unchanged;
- run 37662854852 remains immutable failed evidence;
- no new live execution is authorized by this PR;
- clean-v4 remains incomplete.

Do not alter program semantics or broaden scope.

## 5. Frozen material that MUST NOT change

Do not modify:

- evaluation/terraformers-aws-official-v1 candidate bytes
- candidate identity
- candidate order
- official truth
- clean-v4 ingestion semantics
- scripts/rag/ingest-gcp-target-corpus.py behavior unless absolutely required to fix this shell syntax defect — expected answer is that it is not required
- embedding model
- vector dimension
- corpus checksum
- provider/project-decision provenance
- PT-8A measurement procedure contents

The procedure must remain byte-identical:

docs/evaluation/product-trust-pt-8a-official-acceptance-procedure.md

expected SHA256:

d087508c99a267b36e2c7a2ce70bb69ca9b62d161a95de79ff1fa30e9d3b4823

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

## 6. Validation

Run deterministic repository-only validation.

Required evidence:

- YAML parses successfully.
- `bash -n` passes for both affected workflow run blocks.
- strengthened workflow regression test passes.
- existing relevant clean-v4 unit tests pass.
- `git diff --check` passes.
- procedure SHA256 remains exactly:
  d087508c99a267b36e2c7a2ce70bb69ca9b62d161a95de79ff1fa30e9d3b4823

Inspect the final diff and confirm no unintended clean-v4/model/corpus/runtime semantic change.

Normal PR CI is allowed.

Do not manually rerun failed CI jobs.

## 7. PR boundary

Create exactly one repository-only correction PR.

No live GCP/OpenSearch/model/image publication/rollout actions.

Specifically prohibited in this task:

- workflow_dispatch
- clean-v4 dispatch
- environment deployment approval
- backend image publication
- backend rollout
- Vertex/Gemini calls
- OpenSearch mutation
- AWS input fetch
- PT-8A readiness
- cases A-E
- PT-8B
- teardown
- IAM/ruleset changes
- self-acceptance or merge

Return:

- branch
- commit SHA
- PR number/URL
- exact files changed
- concise root-cause explanation
- validation commands/results
- procedure hash read-back
- confirmation that run 37662854852 remains preserved and no live action occurred

Then STOP for independent review.