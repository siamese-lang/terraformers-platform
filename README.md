# Terraformers Platform

## Project status

Terraformers is a portfolio-oriented modernization of the original 5-person AWS Cloud School team
project. The repository now closes on three evidence-backed engineering cases rather than on a list
of technologies or CI milestones.

| Case | Portfolio role | Final status |
|---|---|---|
| Case A | AI/RAG retrieval grounding and evaluation | PORTFOLIO-CLOSED / PASS |
| Case B | Backend durable asynchronous processing | PORTFOLIO-CLOSED / PASS |
| Case C | GCP production-representative runtime and immutable delivery | PORTFOLIO-CLOSED / RESIDUAL RISKS ACCEPTED |

Observability is supporting evidence across A/B/C, not a fourth independent case.

The representative GCP runtime has been torn down. Final inventory confirmed no remaining
Terraformers GKE clusters, Compute Engine VMs, Persistent Disks, static addresses, GCS buckets, or
Artifact Registry repositories. The Terraform state bucket and GitHub-to-GCP bootstrap identities
were also removed. The repository therefore describes a verified historical runtime and retained
source/evidence, not a currently online service.

Canonical current-state checkpoint:
[docs/AI_PROJECT_STATE.md](docs/AI_PROJECT_STATE.md)

## 1. Service scenario

A user uploads a cloud architecture image. The Spring Boot backend accepts an AnalysisJob and runs
the following provider-neutral lifecycle:

~~~text
authenticated upload
  -> durable AnalysisJob acceptance
  -> architecture fact extraction
  -> semantic reference retrieval
  -> Terraform draft generation
  -> executable Terraform validation
  -> durable result persistence
  -> result read-back
~~~

The generated Terraform is an editable/reference draft. This project does not claim unattended
production terraform apply of generated output.

## 2. Why the project was modernized

The original team project proved a service concept, but it did not provide enough evidence for
three questions that matter in a technical portfolio:

1. Does the AI/RAG path use retrieved evidence reliably, or does it merely call an LLM?
2. What happens to accepted asynchronous work when the process crashes, retries, duplicates, or
   partially persists a result?
3. Can the application run on a real cloud target with durable state, immutable delivery, and
   evidence-backed operational decisions after AWS is no longer available?

The modernization work therefore focused on measurable engineering problems rather than adding
frameworks for their own sake.

## 3. Case A — AI/RAG retrieval grounding and evaluation

### Problem

A successful generation/validation result did not prove that the retrieval stage had actually
provided the required repository/provider evidence. The baseline also exposed fact-extraction
failures and the risk of overfitting known evaluation fixtures.

### Selected mechanism

- Vertex AI multimodal fact extraction and generation
- gemini-embedding-001 query embeddings
- OpenSearch semantic retrieval
- evidence-role-aware bounded selection
- adaptive evidence capacity for architectures requiring more than eight independent references
- frozen canonical and holdout datasets
- negative controls and first-divergence tracking

The project did not introduce LangChain, LangGraph, or LangSmith merely to add framework names.
The active analysis path is a bounded, explicit pipeline; evaluation and observability needs are
covered by repository-owned contracts and evidence.

### Final evidence

Canonical post-correction N=3 runs:

- 36803174654
- 36803781744
- 36804600570

Result:

- fact extraction: 18/18 PASS
- retrieval: 18/18 PASS
- VPC project-decision coverage: 3/3 PASS
- VPC required-resource coverage: 4/4 x 3 PASS
- grounding gaps: 0/12
- positive Terraform validation: 12/12 PASS
- negative controls: 6/6 correct
- first divergence: 0

Frozen holdout run 36805478708 also passed with complete grounding for both positive cases and
correct classification of both negative controls.

See:
[Case A Final Closure](docs/evaluation/case-a-final-closure.md)

## 4. Case B — durable asynchronous AnalysisJob processing

### Before-state

The earlier in-process execution model could strand accepted PENDING/RUNNING work after process
loss, permit duplicate terminal execution, lacked durable bounded retry, and could leave an object
residue after relational finalization or cleanup failure.

### Architecture decision

ADR-007 selected MariaDB as the durable AnalysisJob ownership/work source with:

- durable eligibility
- atomic claim/reclaim
- time-bounded leases
- generation fencing
- bounded local executor
- timeout-only bounded retry
- deterministic result identity
- durable pre-write result intent
- fenced relational finalization
- durable cleanup accountability and recovery

RabbitMQ without an outbox was rejected for the current atomic-acceptance requirement because it
would introduce a DB-commit-to-publish gap. Transactional Outbox plus RabbitMQ remains a deferred
alternative if measured scale, fan-out, or service boundaries justify the added operational layer.

### Final evidence

Run 36555800770 passed the backend suite and real MariaDB 11.4 schema/repository validation.

The integrated 12-row matrix covers:

- accepted work surviving process loss
- reclaim after lease expiry
- duplicate delivery
- provider timeout and retry
- retry exhaustion
- object-write then DB-finalization failure
- failed compensation and later cleanup recovery
- concurrent claim on real MariaDB
- stale-worker fencing
- normal success/read-back

Case B guarantees durable ownership/fencing and accountable side effects. It does not claim
exactly-once external provider invocation.

See:
[Case B Integrated Closure](docs/evaluation/case-b-integrated-closure.md)

## 5. Case C — GCP production-representative runtime and immutable delivery

### Selected runtime

~~~text
GitHub Actions / OIDC-WIF
  -> Artifact Registry immutable backend image
  -> GKE Standard
       |-- Spring Boot backend
       |-- OpenSearch
  -> Vertex AI
  -> dedicated Compute Engine MariaDB 11.4 VM
       -> dedicated Persistent Disk
  -> GCS source/result object persistence
  -> Secret Manager + GKE Secret Sync
~~~

Cloud SQL/MySQL was evaluated but not treated as a drop-in replacement after MySQL 8.4 rejected
the retained Flyway migration contract. MariaDB inside GKE was also rejected for the first
representative runtime because database pressure would share the same worker boundary as
backend/OpenSearch and weaken later bottleneck attribution.

### Live evidence

- 36851221194 — runtime dependencies applied
- 36854218346 — Kubernetes prerequisites verified
- 36889896239 — authenticated integrated path PASS
- 36891629279 — backend replacement durability PASS
- 37015159218 — exact source image publication
- 37015932695 — exact digest rollout and embedded source-revision verification

The integrated live path proved:

~~~text
authenticated upload
  -> durable AnalysisJob
  -> real Vertex fact extraction/generation
  -> Vertex embedding
  -> OpenSearch retrieval
  -> GCS source/result persistence
  -> terminal success
  -> Terraform read-back
~~~

### Capacity stopping decision

A capacity run was deliberately stopped before tuning because executable-Terraform correctness was
not stable enough to provide a trustworthy workload prerequisite. The project does not claim a
production saturation limit, HPA tuning result, or capacity improvement curve.

See:
[Case C Final Portfolio Closure](docs/evaluation/case-c-portfolio-closure.md)

## 6. Observability as supporting evidence

The repository uses observability to explain concrete failure/runtime behavior, not as a decorative
dashboard requirement.

Current repository-owned signals include:

- Spring Boot Actuator and Prometheus export
- analysis started/succeeded/failed counters
- bounded failure categories
- total analysis duration
- provider-neutral stage duration and stage failure category
- queue wait
- claim/dispatch/lease/retry/recovery/cleanup signals
- executor rejection
- analysisJobId MDC correlation
- build/source revision in logs

A relational-finalization failure was used to show why coarse job-level failure signals were
insufficient. The minimal telemetry change made analysis execution, result finalization failure, and
compensation independently machine-identifiable without introducing a tracing backend.

The project does not claim a completed Grafana, OpenTelemetry Collector, distributed tracing,
Cloud Trace, or SLO/alerting platform.

See:
[M7 Observability Closure](docs/plans/active/M7-observability.md)

## 7. Final cloud teardown

The GCP runtime was destroyed after evidence collection.

The initial 29-resource Terraform destroy partially completed and then encountered a GCE 409 while
the MariaDB VM deletion and data-disk detach overlapped. A reviewed subset recovery deleted the one
remaining Terraform-managed MariaDB data disk and left canonical Terraform state empty.

After bootstrap cleanup, an independent project inventory found one state-external GKE CSI disk:

- pvc-17c2bb7e-eb1e-4474-9531-41418c77c7df
- 15 GiB
- pd-standard

It matched the OpenSearch PVC contract, had no remaining workload owner, and was deleted manually.
A final gcloud compute disks list returned zero items. GKE, VM, static IP, GCS, Artifact Registry,
and disk inventories were all empty for the Terraformers target.

This is retained as an operations lesson: Terraform state zero is not sufficient proof that
controller/CSI-created cloud resources are absent.

See:
[GCP Final Teardown Runbook](docs/runbooks/gcp-target-final-teardown.md)

## 8. Technology boundary

Primary current portfolio stack:

| Area | Technologies |
|---|---|
| Backend | Java 17, Spring Boot 3.3.2, Spring Data JPA, Flyway |
| Durable state | MariaDB 11.4 |
| AI | Vertex AI Gemini generation/fact extraction, gemini-embedding-001 |
| Retrieval | OpenSearch |
| Object storage | GCS provider-neutral object boundary |
| Runtime | GKE Standard + dedicated MariaDB Compute Engine VM |
| Delivery | Artifact Registry, immutable digest, GitHub Actions |
| Identity | GitHub OIDC / GCP Workload Identity Federation |
| Secrets | Secret Manager + GKE Secret Sync |
| Observability | Actuator, Micrometer, Prometheus-format metrics, bounded logs/MDC |
| Infrastructure | Terraform |

AWS-specific implementations remain in the repository as historical compatibility/reference assets.
They are not the current live runtime.

## 9. Scope and non-claims

The repository intentionally does not claim:

- every generated Terraform draft is deployment-correct
- generated Terraform is automatically applied
- exactly-once external AI-provider invocation
- production-scale throughput or saturation limits
- multi-replica zero-downtime availability
- MariaDB HA or automatic failover
- completed HPA/OpenSearch tuning
- completed faulty-release rollback experiment
- full distributed tracing/dashboard/alerting platform
- unattended production Terraform apply safety

These are residual or future production-hardening topics, not prerequisites for the selected
portfolio cases.

## 10. Team project and modernization contribution boundary

The original service was a 5-person team project. Do not describe the entire original product as an
individual implementation.

The later modernization contribution is the repository-backed work around:

- backend domain/runtime boundary cleanup
- durable AnalysisJob ownership/recovery/retry/cleanup
- provider-neutral cloud/AI adapters
- RAG grounding measurement and evaluation
- GCP representative runtime and delivery
- operational telemetry and failure classification
- infrastructure lifecycle and final cost/resource closure

## 11. Recommended reading order

1. [AI Project State](docs/AI_PROJECT_STATE.md)
2. [Case A Final Closure](docs/evaluation/case-a-final-closure.md)
3. [Case B Integrated Closure](docs/evaluation/case-b-integrated-closure.md)
4. [Case C Final Portfolio Closure](docs/evaluation/case-c-portfolio-closure.md)
5. [Final Evidence and Interview Guide](docs/portfolio/final-evidence-and-interview-guide.md)
6. [GCP Final Teardown Runbook](docs/runbooks/gcp-target-final-teardown.md)
