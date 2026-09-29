# Case A A2 Repeated Current Baseline Evidence

## Status and scope

**A2 repeated current baseline — COMPLETE**

This evidence closure advances the **AI/RAG evaluation and targeted improvement** axis by deepening
Case A's reproducible before-state. It records three live evaluations of the unchanged current
baseline; it changes no production retrieval behavior and does not select a root cause or retrieval
alternative. Case A remains **OPEN**. A3 requires separate user approval.

All three evaluations used source commit
`96c6442026f6c57a30a9b248a8af115df1e7f2e4`. The bounded live session reused the single target
runtime and ended at the required idle boundary.

## Runtime boundary evidence

| Boundary | Workflow run | Operation | Plan JSON SHA-256 | Exact change | Destructive change |
|---|---:|---|---|---|---|
| Activate | `36574320749` | `activate` | `a5a90c08dee592cf4002c8f5ba5650c03f33aa559e8c0b8d79caf6fd38a31aad` | only `google_container_node_pool.target`, `node_count` `0 -> 1` | delete/replacement `0` |
| Idle | `36579032096` | `idle` | `fb62708f2f332091346ab062ef04abea04b17952e509300bda1e07a431df8439` | only `google_container_node_pool.target`, `node_count` `1 -> 0` | delete/replacement `0` |

Both boundary runs used the same source commit as the evaluations. No second environment was created.

## Evaluation artifact identity

| Run | Workflow run | Evaluation run | Raw artifact ID / digest | Grounding report artifact ID / digest |
|---:|---:|---|---|---|
| 1 | `36574906125` | `case-a-baseline-36574906125-1` | `11036234949` / `sha256:b7a1dc3251558817a6af5d737ffb73b629e682d3261d980838acdb5d3b0eead8` | `11037560428` / `sha256:152c3f6f01635674f279ec70e84890a10c02d1dd876460801e7f5ced7544673f` |
| 2 | `36576165161` | `case-a-baseline-36576165161-1` | `11037282409` / `sha256:71457de40d03e97a8649e3ae3497414f3882e9d8d8cb635b6985ab05ae079ed2` | `11037282413` / `sha256:98083b1a8af6a736c1edaa19bbb80f6a1cea5d892e33e7be85678b1eee9fad94` |
| 3 | `36577747809` | `case-a-baseline-36577747809-1` | `11037679900` / `sha256:26756267fb92b89ecbc0eac7398c9d8f649ea5331e85b2365ca163051003ec25` | `11038234273` / `sha256:b80dd2a06ad90adced8e4051195c9a3dc116b73adfb0b1467d19fb71a4971a2e` |

## Frozen configuration

The following identity was constant across all three runs:

| Field | Value |
|---|---|
| dataset | `terraformers-eval-v1` |
| corpus | `terraformers-reference-v3` |
| provider version | `5.100.0` |
| analysis / embedding provider | `vertex` / `vertex` |
| retrieval mode / top-K | `REQUIRED` / `8` |
| generation model | `gemini-3.8-flash` |
| embedding model | `gemini-embedding-001` |
| configuration fingerprint | `sha256:d10c56e4f124ee67d1cbc69457249ad0cf1243bf682305f32e65817e32b6ae66` |
| fact-extraction thinking / max output tokens | `LOW` / `800` |
| generation max output tokens | `8192` |

## Aggregate execution and grounding

Across the three six-case runs:

- fact extraction passed `18 / 18` and retrieval passed `18 / 18`;
- architecture validation passed `12 / 12`;
- negative controls were correct `6 / 6`;
- first-divergence failures were `0`;
- the 12 applicable positive observations contained `4 / 12` grounding gaps, and all four had valid
  generated output (`4 / 12` grounding gaps with valid output).

Gap frequency by positive case was:

| Case | Grounding gaps |
|---|---:|
| `arch-vpc-three-tier` | `3 / 3` |
| `arch-cloudfront-private-alb` | `0 / 3` |
| `arch-private-aoss` | `0 / 3` |
| `arch-s3-metadata-split` | `1 / 3` |

This reproduces the VPC grounding gap in every scheduled run while preserving successful execution
and validation. It does not establish that the generated Terraform is incorrect.

## VPC repeated observations

Required project-decision coverage was `[1, 0, 0]` matched out of `[1, 1, 1]`. Required exact
resource-type coverage was `[3, 2, 3]` matched out of `[4, 4, 4]`.

| Run | `tfref-v2-sg-relations` | `aws_security_group` | `aws_db_instance` |
|---:|---|---|---|
| 1 | rank `8` | rank `6` | missing |
| 2 | missing | missing | missing |
| 3 | missing | rank `7` | missing |

Every VPC run had non-empty filters containing `aws_vpc`, `aws_subnet`, `aws_lb`,
`aws_security_group`, and `aws_db_instance`. Therefore absent resource filters are not the
established root cause for A2, and the earlier CloudFormation-shaped vocabulary/filter-loss path is
insufficient to explain these observations.

The evidence supports a persistent VPC grounding gap, variable retrieval coverage/ranking, and
variable upstream fact/query wording. It does **not** prove vector ranking itself as the root cause.
The next diagnostic candidate is an A3 fixed-facts retrieval probe that holds upstream facts constant
while comparing retrieval alternatives; neither that comparison nor any alternative is authorized by
this closure.

## Secondary observations

### S3 retrieval variability

`arch-s3-metadata-split` exact required resource coverage was `[1 / 2, 2 / 2, 2 / 2]`. Run 1 missed
`aws_db_instance`; runs 2 and 3 included it. This is retained as retrieval-variability evidence, not a
second selected primary problem.

### AOSS historical latency observation

AOSS fact-extraction latencies were `3053`, `2832`, and `4443 ms`: minimum `2832 ms`, median
`3053 ms`, maximum `4443 ms`. The historical `130489 ms` outlier did **not** reproduce in `N = 3`.
This bounded result does not authorize latency, model, timeout, or topology work.

### Applicable-positive latency summary

Across the 12 applicable positive observations:

| Stage | Minimum | Median | Maximum |
|---|---:|---:|---:|
| fact extraction | `2587 ms` | `4239 ms` | `14323 ms` |
| retrieval | `383 ms` | `1301 ms` | `2072 ms` |
| generation | `11620 ms` | `19306 ms` | `57711 ms` |
| observed stage-sum | `18936 ms` | `26305 ms` | `63810 ms` |

These are small-sample minimum/median/maximum observations. No p95 or p99 is calculated or claimed.

## Closure decision

- A2 repeated current baseline is **COMPLETE**.
- The VPC grounding gap is reproduced `3 / 3`.
- Production retrieval behavior is **UNCHANGED**.
- The AOSS historical latency outlier is **NOT reproduced in N=3**.
- Root cause is **NOT YET SELECTED**.
- **A3 fixed-facts retrieval alternative comparison / decision is NEXT** and requires separate user
  approval.
- Case A is **not complete**.
