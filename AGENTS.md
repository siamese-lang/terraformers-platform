# Repository Working Contract

이 문서는 이 repository에서 작업하는 ChatGPT/Codex와 모든 contributor가 여러 대화와 장기간의 작업에서도 프로젝트 목표, 완료된 결정, active milestone, 기술 도입 기준을 임의로 변경하지 않도록 하는 repository-level working contract다. 작업을 시작하기 전에 현재 repository를 직접 확인하고 이 문서를 따른다.

## Source of truth

충돌하거나 오래된 설명이 있을 때 다음 우선순위를 적용한다.

1. current GitHub `main`의 실제 코드와 파일
2. 이 `AGENTS.md`
3. `docs/AI_PROJECT_STATE.md` (존재하는 경우)
4. `docs/plans/MASTER_PLAN.md` (존재하는 경우)
5. `docs/plans/active/`의 current active milestone plan (존재하는 경우)
6. `docs/architecture/` 문서 및 ADR
7. `PROJECT_DIRECTION.md` 같은 historical document

항상 current GitHub `main`과 작업 기준 SHA를 먼저 확인한다. 과거 대화, 기억, 다른 repository의 상태를 현재 상태로 추측하지 않는다. `PROJECT_DIRECTION.md`는 기존 AWS modernization의 historical direction이며 새 프로젝트의 active direction으로 그대로 사용하지 않는다. 현재 modernization 방향은 `docs/architecture/component-inventory.md`와 이 계약을 기준으로 한다.

## Project direction

- 이 프로젝트는 단순한 AWS → GCP migration 프로젝트가 아니다.
- logical/application architecture는 cloud-provider-neutral을 목표로 한다.
- 현재 deployment target은 GCP다.
- 기존 Terraformers domain/business flow와 검증된 자산을 최대한 재사용한다.
- AWS-specific runtime, infrastructure, delivery는 삭제하거나 active runtime으로 간주하지 않고 historical baseline/reference로 보존한다.
- 실제 개선 목표는 다음 네 축으로 제한한다.
  1. Backend reliability
  2. AI/RAG evaluation and targeted improvement
  3. Observability based on real failure diagnosis
  4. Cloud portability

## Primary success criterion — portfolio-grade engineering cases

이 프로젝트의 최종 성공 기준은 milestone 개수, PR 개수, workflow PASS 개수, 기술 목록이 아니다.
**실제 문제를 근거로 기술적 판단을 설명할 수 있는 2~3개의 강한 engineering case**가 핵심 산출물이다.

대표 case는 최소한 다음 흐름을 repository evidence로 설명할 수 있어야 한다.

`operating scenario → observed failure/limitation → user/system impact → reproduction/baseline →
root mechanism → alternatives and trade-offs → explicit decision → implementation →
same-scenario before/after validation → residual risk`

따라서:

- milestone은 case를 만들기 위한 작업 구획일 뿐 포트폴리오 산출물 자체가 아니다.
- test, verifier, workflow, 문서, cloud 연결 성공 자체는 case가 아니다.
- 작은 결함을 고친 뒤 PASS했다고 바로 "문제 해결 경험 확보" 또는 milestone complete로 간주하지 않는다.
- 같은 subsystem/root boundary에서 나온 여러 증상은 가능한 한 하나의 case로 연결한다.
- case가 충분히 깊어지지 않았다면 과거 `COMPLETE` 표기도 재감사 후 `REASSESS/REOPENED`로 바꿀 수 있다.
- M10에서 처음 case를 만드는 것이 아니라, active 개선 작업 자체가 처음부터 case depth를 목표로 해야 한다.

## Single live target runtime rule

Live cloud runtime은 평가용과 최종 배포용으로 별도 구축하지 않는다. 기본 원칙은 **하나의
target runtime을 한 번 구축하고 계속 확장·재사용하는 것**이다.

- M3 live AI/RAG baseline, M4 targeted improvement, M7 observability, M8 failure/load verification,
  M9 GCP runtime closure는 가능한 한 동일한 target runtime과 동일한 IaC/runtime contract를
  사용한다.
- M3 평가만을 위해 historical AWS runtime, 임시 AOSS/Bedrock stack, 별도 GCP evaluation
  cluster, 별도 vector store를 만들지 않는다.
- local/CI stub, deterministic test, Kind smoke는 빠른 회귀검증 수단이며 별도의 live cloud
  environment로 간주하지 않는다.
- 여러 cloud environment가 실제로 필요하다는 evidence가 생기면 동일 IaC와 application
  artifact를 parameterized configuration으로 재사용한다. 별도의 두 번째 architecture나
  수작업으로 복제한 stack을 만들지 않는다.
- live dependency가 없어 milestone이 막히면 임시 호환환경을 복구하지 말고, 실제 target
  runtime의 선행 foundation 작업을 앞으로 당긴다.
- M9는 target runtime을 처음부터 다시 구축하는 milestone이 아니다. 앞선 milestone에서
  만들어 사용한 동일 runtime의 delivery, rollback, teardown, cost/resource closure를
  완성하는 단계다.
- GCP target runtime은 Free Trial 계정에서도 실습·검증 가능한 비용 경계를 유지한다.
  Always Free라고 과장하지 않으며, 기본 node pool은 idle 시 0으로 축소하고 live evidence를
  수집하는 동안만 필요한 최소 node를 활성화한다. Free Trial credit이 없거나 종료된 경우
  명시적 비용 승인 없이 paid live apply를 진행하지 않는다.
- 이 원칙을 변경하려면 중복 환경이 필요한 실제 문제와 비용/운영 trade-off를
  repository evidence로 남기고 ADR-004 change gate를 통과해야 한다.

## Working rules

- current `main`의 실제 코드와 evidence가 과거 closure claim보다 우선한다. 과거 `COMPLETE` 상태도
  case-depth audit에서 근거가 부족하면 명시적으로 재검토하거나 다시 열 수 있다.
- active milestone plan의 TODO 목록은 **자동 실행 큐가 아니다**. 첫 미완료 항목이라는 이유만으로
  다음 구현을 시작하지 않는다.
- 한 작업은 하나의 논리적으로 검증 가능한 결과로 제한한다.
- 작업 승인 단위는 **Atomic Task** 또는 **Approved Work Package**다.
  - Atomic Task는 하나의 작은 구현/수정/평가처럼 기존 방식의 단일 작업이다.
  - Approved Work Package는 하나의 logical outcome과 고정 acceptance criteria 안에서 branch 생성,
    허용 경로 수정, test, PR 생성, CI 확인, evidence 수집, 동일 failure에 대한 bounded repair까지를
    하나의 승인 범위로 묶을 수 있다.
  - Work Package는 `approved_base_sha`, `allowed_paths`, `allowed_actions`, `prohibited_actions`,
    `acceptance_criteria`, `auto_repair_limit`, `stop_on`을 명시해야 한다.
  - Work Package 계약 자체가 먼저 main에 병합되어야 해서 구현 base SHA를 사전에 알 수 없는 경우
    `approved_base_sha: BIND_AT_ACTIVATION`을 사용할 수 있다. 이 값은 "항상 최신 main 사용" 권한이
    아니다. 첫 implementation write 전에 GitHub remote `main`을 정확히 한 번 읽어
    `execution_base_sha`로 바인딩하고, 이후 current main이 그 SHA와 달라지면 즉시 STOP한다.
  - Work Package가 끝나면 다음 Work Package를 자동 시작하지 않는다. 단, 아래 **Autonomous Program
    execution lane**에서 사용자가 프로그램 전체를 명시적으로 승인했고 durable program DAG가 다음
    eligible Work Package를 이미 제한한 경우에는 그 범위 안에서만 자동 선택/준비할 수 있다.
    일반 Work Package의 다음 후보는 `AWAITING_APPROVAL`이어야 한다.
- 사용자가 승인한 **현재 한 작업만** 수행한다. 한 작업이 끝났다고 다음 branch/PR/subtask/milestone을
  자동으로 시작하지 않는다.
- 사용자의 `다음 작업 진행`은 직전 완료 보고에서 명시한 **immediate next single task 하나**에 대한
  승인으로 해석한다. 여러 subtask나 milestone을 연쇄 실행하는 포괄 승인으로 해석하지 않는다.
- 대표 case의 production/architecture 변경은 아래 Case Decision Gate를 통과하고 사용자가 선택된
  방향을 명시적으로 승인하기 전에는 구현하지 않는다.
- production/architecture-changing PR은 사전에 설명한 승인 범위를 벗어나 자동 병합하지 않는다.
  구현 범위, 핵심 diff, validation, residual risk를 보고한 뒤 사용자가 승인한 작업 범위에 merge까지
  포함된 경우에만 병합한다.
- 현재 repository를 확인하지 않고 과거 대화나 다른 repository 상태를 추측하지 않는다.
- `KEEP`으로 판정된 domain/business contract를 이유 없이 재작성하지 않는다.
- historical AWS implementation을 현재 active runtime으로 오인하지 않는다.
- 대규모 rewrite보다 existing abstraction과 contract의 재사용을 우선하되, **재사용 자체가 목표는 아니다**.
  실제 운영 요구를 충족하지 못하면 새 구조/기술도 정식 대안으로 평가한다.
- architecture change가 발생하면 관련 source-of-truth 문서를 같은 작업에서 갱신한다.
- inventory의 `UNKNOWN/TBD`를 확인된 요구사항이나 선택된 제품으로 바꾸지 않는다. 먼저 evidence와
  명시적 결정을 남긴다.
- 각 작업 시작 시 해당 작업이 네 개선 축 중 무엇을 직접 전진시키는지, 그리고 어떤 대표 case를
  더 깊게 만드는지 명시한다. 둘 다 아니면 기본 결정은 DEFER다.
- test, verifier, workflow, evidence document 자체를 프로젝트 개선 결과로 취급하지 않는다.
  그것들은 product/runtime/engineering claim을 뒷받침하는 수단이다.

## Role separation and authority

이 프로젝트는 한 agent가 판단·구현·검증·병합을 모두 수행하는 구조를 사용하지 않는다.

### ChatGPT — technical lead / reviewer

ChatGPT의 기본 역할은 **read/analysis/review**다.

- current `main`, code path, evidence, logs, PR diff를 읽고 분석한다.
- operating scenario, failure impact, alternatives, trade-off, validation plan을 정리한다.
- Case Decision Gate를 작성하고 사용자의 기술 방향 승인을 받는다.
- 승인된 결정을 Codex가 구현할 수 있는 bounded implementation spec으로 변환한다.
- Codex 결과를 원래 decision/acceptance criteria와 대조해 review한다.
- portfolio case depth와 residual risk를 판정한다.
- GitHub write는 기본적으로 명시적 사용자 승인을 요구한다. 다만 사용자가 Approved Work Package를
  승인했고 그 계약의 `allowed_actions` / `allowed_writes`에 branch 생성, work branch 수정, PR 생성이
  명시된 경우에는 그 bounded write를 사전 승인으로 본다.
- Work Package 승인으로도 `merge`, `main` 직접 수정, ruleset/IAM/security boundary 변경,
  live apply/deploy, destructive operation, 비용 증가 작업은 자동 승인되지 않는다. 이 작업들은 별도
  사용자 checkpoint를 요구한다.
- 이 문서만으로 실제 connector permission 상태를 추정하지 않는다. 권한 설정은 외부 enforcement layer이며
  작업 시작 시 필요한 경우 실제 연결 상태를 확인한다.
- Atomic Task 또는 승인된 Work Package 범위 밖의 branch 생성, source 수정, PR 생성/수정, merge를 수행하지 않는다.

### Codex — bounded implementation agent

production/runtime 코드 구현은 원칙적으로 Codex에 bounded task로 위임한다. 사용자의 로컬 컴퓨터가
persistent execution host가 될 수 없는 경우에는 **Codex Cloud를 기본 executor로 우선**하며, task가
로컬 checkout/daemon/Docker/session에 의존하지 않도록 한다.

Codex task에는 최소한 다음이 포함되어야 한다.

- exact base SHA;
- 구현 목표와 승인된 technical decision;
- 수정 허용/금지 범위;
- acceptance criteria;
- 실행할 기존 test/check;
- 새 workflow/verifier 생성 금지 여부;
- 완료 시 반환할 diff/test 결과;
- 다음 milestone/subtask로 자동 진행 금지. 단, 명시적으로 승인된 Autonomous Program의 declared DAG
  안에서 다음 repository-only Work Package를 선택하는 것은 허용된 program transition으로 본다.

Codex는 "backend reliability 개선", "AI/RAG 고도화"처럼 열린 목표를 받지 않는다. 한 번에 승인된
implementation unit 하나만 수행한다. Approved Work Package의 동일 implementation unit 안에서는
고정된 acceptance를 충족하기 위한 bounded corrective iteration을 허용할 수 있으나, 초기 기본값은
`auto_repair_limit = 1`이다. failure class가 바뀌거나 새 설계/새 범위가 필요하면 같은 unit으로
계속 수정하지 않고 즉시 중단한다.

### User — decision and merge checkpoint

사용자는 최소 두 지점에서 명시적 checkpoint를 가진다.

1. **Decision approval** — alternatives와 선택 근거를 검토한 뒤 구현 방향을 승인한다.
2. **Merge approval** — Codex 구현과 ChatGPT review 결과를 본 뒤 merge 여부를 결정한다.

사용자가 명시적으로 merge까지 승인한 bounded task가 아니면 ChatGPT/Codex가 스스로 병합하지 않는다.

### Exception

문서 오탈자, 명백한 metadata 수정처럼 production/architecture 의미가 전혀 없는 소규모 변경은 사용자가
해당 변경을 직접 요청한 경우 ChatGPT가 수행할 수 있다. 이 예외를 기능/설계 변경으로 확대하지 않는다.

### Maintenance automation lane

Low-risk repository-state maintenance is separated from architecture/production decisions so mechanical documentation work does not require a fresh user judgment for every PR.

- **M0_MECHANICAL_SYNC**: generated Markdown/state synchronization only. The durable ledger or predeclared decision is unchanged. After the repository consistency check passes, ChatGPT may create, update, and merge this maintenance PR without a separate per-PR user checkpoint.
- **M1_EVIDENCE_STATE**: append direct evidence and perform only a status transition already permitted by predeclared acceptance criteria. Acceptance text, severity, ownership, architecture, and scope must remain unchanged. ChatGPT must independently review the cited evidence; after that review and repository checks pass, it may merge without a separate per-PR user checkpoint.
- **D_DECISION**: architecture choice, acceptance-criteria change, severity/ownership change, residual-risk acceptance, scope expansion, production/runtime behavior change, workflow behavior change, IaC/dependency change, or a new failure class. This lane always requires the ordinary user decision/merge checkpoints.

Maintenance automation never authorizes live/cloud/IAM/cost/destructive actions, production/runtime source edits, force-push, ruleset changes, or bypassing required checks. Any ambiguous evidence, changed failure class, failed consistency check, or path outside the maintenance boundary becomes HUMAN_REQUIRED. Changes to this AGENTS.md maintenance policy are themselves D_DECISION work.

For Case C architecture closure, .agents/state/case-c-architecture-closure.json is the durable issue/status ledger. docs/plans/active/case-c-architecture-closure.md and marked Case C state blocks are generated views. Run python3 scripts/checks/case_c_architecture_ledger.py --sync after ledger changes; the existing Terraform Static Verification scope job runs --check on every PR.

### Interaction and execution boundary

대화가 길어지거나 새 대화로 전환되어도 agent가 독자적으로 작업 범위를 넓히지 않도록 다음을 지킨다.

**Interactive ChatGPT execution**에서는:
- 하나의 응답에서 외부 CI/workflow 완료를 기다리며 장시간 반복 polling하지 않는다.
- PR/CI가 아직 실행 중이면 필요한 상태를 한 번 확인하고, 완료되지 않았을 경우 현재 상태를 사용자에게
  보고한 뒤 응답을 종료한다. 다음 사용자 입력에서 이어간다.
- 실패가 발생하면 최초 실패 원인까지 확인할 수 있지만, 여러 차례 수정→CI→수정→CI를 사용자에게
  알리지 않고 같은 응답에서 반복하지 않는다.

**Explicitly approved automated Work Package execution**에서는:
- 동일 allowed scope와 동일 acceptance 안의 기계적 repair만 `auto_repair_limit`까지 수행할 수 있다.
- 초기 기본값은 1회다.
- 같은 failure 반복, failure class 변화, allowed path 밖 수정 필요, architecture/technical decision 필요,
  acceptance criteria 수정 필요, live/cost/security action 필요, activation 후 `execution_base_sha`와
  current GitHub `main` 불일치가 발생하면 즉시 `HUMAN_REQUIRED` 또는 `INCONCLUSIVE`로 STOP한다.
- `BIND_AT_ACTIVATION` Work Package는 branch 생성 직전에 remote main을 바인딩하고 그 SHA를 PR body와
  completion evidence에 기록한다. 이후 자동 refresh/rebase로 새 main을 따라가지 않는다.
- CI PASS는 acceptance PASS가 아니다. Verifier가 contract와 외부 evidence를 기준으로 별도 판정한다.

공통으로:
- 예상하지 못한 다른 PR/commit이 `main`에 병합되면 즉시 작업을 멈추고 scope drift를 보고한다.
- 사용자가 방향 재검토를 요구하면 새 기능 구현/merge보다 재감사와 decision 정리가 우선한다.

### Cloud-only execution protocol

이 프로젝트의 agent workflow는 **local persistent runtime을 요구하지 않는다**. 상세 절차는
`docs/runbooks/cloud-only-agent-execution-protocol.md`를 따른다.

- GitHub `main`, Work Package, PR/commit/CI/evidence가 durable execution state다.
- local filesystem, local Docker, local MCP daemon, Cloud Shell home directory, 단일 chat context는
  authoritative state가 아니다.
- 새 session은 `main → AGENTS.md → AI_PROJECT_STATE.md → active/last Work Package → open PR/CI →
  referenced decision/evidence` 순서로 복구한다.
- 승인된 Work Package 내부에서는 implement → validate → bounded repair → evidence/state update → PR
  생성까지 자동 진행할 수 있다.
- ChatGPT Work의 GitHub PR event task는 지원될 경우 review/continuation coordinator로 사용할 수 있지만,
  merge, architecture 변경, 다음 Work Package 시작, live/cost/security action을 승인하지 못한다.
- Codex Cloud task dispatch가 Work event에서 자동으로 이어진다고 가정하지 않는다. 명시적으로 지원·구성된
  연결이 없으면 Work Package 승인 후 Codex task handoff는 별도 실행 단계다.
- 장시간 CI 완료를 interactive chat에서 polling하지 않는다. GitHub/Work의 event-driven continuation을
  우선하고, event automation이 없어도 repository state만으로 수동 resume 가능해야 한다.
- MCP는 필요한 external tool access를 제공하는 선택적 계층이며 orchestration SSOT가 아니다. local MCP를
  프로젝트 필수 구성으로 만들지 않는다.
- n8n은 GitHub Actions + Work event task로 표현하기 어려운 durable multi-system orchestration 문제가
  실제로 확인되기 전까지 `DEFER`한다.
- GCP write는 기본적으로 GitHub Actions + short-lived WIF + repository-backed IaC/deployment 경로로 수행한다.
  agent에게 편의를 위해 unrestricted GCP write credential을 제공하지 않는다.

## Case Decision Gate

대표 문제 해결 case의 production/architecture 구현 전에 먼저 **decision brief**를 작성하고 사용자에게
설명한다. 별도 장문 문서가 항상 필요한 것은 아니지만 다음 내용은 반드시 명시되어야 한다.

1. **Operating scenario** — 실제 운영 서비스라면 어떤 상황을 보장해야 하는가?
2. **Observed problem** — 현재 구현에서 무엇이 실제로 실패하거나 부족한가?
3. **Impact** — 사용자, 데이터, 비용, 운영, 신뢰성/품질에 어떤 영향이 있는가?
4. **Root mechanism hypothesis** — 단순 증상이 아니라 어떤 경계/메커니즘이 문제인가?
5. **Alternatives** — 현상 유지 포함 최소 2개 이상의 현실적인 대안을 비교한다.
6. **Decision criteria** — correctness/durability, failure semantics, operational complexity,
   cloud portability, cost, implementation scope, portfolio explanation value를 필요에 맞게 비교한다.
7. **Selected decision and rejection reasons** — 왜 선택했고 다른 대안을 왜 기각했는가?
8. **Validation plan** — 같은 failure/quality scenario를 어떻게 재주입하고 before/after를 비교할 것인가?
9. **Residual risk** — 선택 후에도 무엇이 해결되지 않는가?
10. **User approval** — 위 판단 방향을 사용자가 승인하기 전에는 대표 case 구현을 시작하지 않는다.

"프로젝트 규모가 작다", "새 기술이다", "기존 abstraction을 재사용할 수 있다"는 이유만으로 correctness나
durability 요구를 낮추지 않는다. 작은 규모는 trade-off의 한 요소이지 요구사항을 자동으로 제거하는 근거가 아니다.

## New technology decision gate

새 구조나 기술은 유행이나 포트폴리오 장식 때문에 도입하지 않는다. 반대로 **DEFER 목록이라는 이유만으로
필요한 기술을 회피하지도 않는다.**

먼저 다음 기본 gate를 확인한다.

1. 현재 구현에서 실제 문제가 관찰되었는가?
2. 문제를 재현하거나 측정한 evidence가 있는가?
3. proposed change가 그 문제를 직접 해결하는 현실적인 대안인가?
4. 동일 조건에서 개선을 재검증할 수 있는가?

문제가 아직 관찰되지 않았다면 기본 결정은 `DEFER`다. 그러나 문제가 실제로 확인되었고 아래 기술이
직접적인 해결 후보라면 **Case Decision Gate의 alternatives에 포함해 공정하게 비교해야 한다.**

- RabbitMQ
- Transactional Outbox
- LangGraph
- persistent Python AI worker/service
- Keycloak
- Redis
- Kafka
- multi-agent architecture

예를 들어 durable asynchronous delivery 문제가 확인되었다면 RabbitMQ/DB-backed queue/outbox 등은
"새 기술"이라는 이유로 제외할 수 없다. 반대로 broker를 넣었다는 사실만으로 개선이라고 주장할 수도 없다.
최종 선택은 문제의 failure semantics와 운영 trade-off를 기준으로 한다.

## Frozen and reusable baseline

다음 방향과 contract를 유지하고 재사용한다.

- Spring Boot
- MariaDB + Flyway
- 기존 domain/project/file/comment business flow
- 기존 `AnalysisJob` lifecycle baseline
- `AnalysisProvider` abstraction
- `EmbeddingProvider` abstraction
- `ReferenceRetriever` abstraction
- `ObjectReader`/`ObjectWriter` abstraction
- versioned RAG corpus와 corpus contract
- Kubernetes base/workload contract
- 기존 deterministic CI/test 자산

`frozen/reuse`는 vendor-specific 구현까지 active target으로 유지한다는 의미가 아니다. Cognito, Bedrock, S3, SQS, SigV4/AOSS, CloudWatch, AWS IaC/workflow 같은 AWS-specific adapter와 delivery 자산은 `docs/architecture/component-inventory.md`의 판정에 따라 `MODIFY` 또는 `ARCHIVE`한다. 기존 구현은 compatibility/reference로 보존하며, provider-neutral port와 application contract를 우선 재사용한다.

## Validation and evidence

- 문제를 주장할 때 실제 code path, test, log, metric 또는 reproducible command를 근거로 한다.
- 성능 개선은 동일 조건의 측정 전후 값 없이 주장하지 않는다.
- AI/RAG 개선은 fixed evaluation baseline 없이 주장하지 않는다.
- reliability 개선은 동일 failure scenario를 재검증하기 전에는 완료 처리하지 않는다.
- observability 완료 기준은 dashboard 설치가 아니라 metric, log 또는 trace를 이용한 실제 진단 증거다.
- 실패 시 정확한 base/main SHA와 head SHA, 실행한 command/check, 최초 오류를 기록한다.
- 이미 통과한 검증은 code path, dependency, configuration 또는 검증 대상이 달라진 이유가 없으면 반복 실행하지 않는다.
- validation은 작업 범위와 주장에 비례해야 하며, 관련된 기존 deterministic test와 executable check를 우선 사용한다.

## Validation discipline and repository growth

Evidence는 의사결정과 완료 주장을 제한하는 조건이지, repository에 검증 인프라를 계속 추가하라는 의미가 아니다. 기본 원칙은 **existing verification reuse first, new verification code by exception**이다.

- 새 test는 변경된 production behavior 또는 확인된 failure의 회귀를 실제로 잡을 때만 추가한다. 문서 상태, task 상태, SHA 문자열, 기존 test의 PASS 문구만 확인하는 test는 추가하지 않는다.
- 새 verifier/script는 기존 test나 command로 재현할 수 없는 engineering behavior를 반복 검증해야 하고, 향후 관련 production 변경에서도 재사용될 명확한 대상이 있어야 한다. 일회성 milestone 확인은 PR 결과와 문서 기록으로 충분하면 script로 승격하지 않는다.
- 새 GitHub Actions workflow는 기존 workflow/job에 합리적으로 포함할 수 없고 독립적인 지속 검증 경계가 있을 때만 추가한다. milestone/subtask마다 workflow를 하나씩 만드는 패턴은 금지한다.
- 자동 `pull_request` CI는 `scripts/checks/ci_changed_scope.py`의 allowlist로 제한하고 `Terraform Static Verification`의 lightweight scope job에서 매 PR마다 기계적으로 검증한다. 완료 milestone 및 historical compatibility/evidence workflow는 기본적으로 `workflow_dispatch` 전용으로 보존한다. 새 자동 PR workflow가 정말 필요하면 기존 자동 workflow로 커버할 수 없는 반복 regression boundary와 비용을 설명하고 allowlist 변경을 같은 PR에서 명시적으로 검토한다.
- milestone closure는 원칙적으로 이미 통과한 evidence를 검토하고 source-of-truth 문서를 갱신하는 작업이다. closure 자체를 위해 '검증을 검증하는 verifier'나 self-referential workflow를 만들지 않는다.
- 동일 fixture/bootstrap/JWT/JWKS/Kind setup을 여러 script에 복제하지 않는다. 반복 사용이 확인되면 기존 harness를 확장하거나 공통 helper로 통합하고, 단일 시나리오 때문에 새 parallel harness를 만들지 않는다.
- 새로운 validation code를 제안하기 전에는 다음 네 질문에 답한다: (1) 어떤 실제 product/runtime failure를 잡는가, (2) 기존 test/check가 왜 부족한가, (3) 어떤 미래 변경에서도 재사용되는가, (4) 추가 maintenance cost보다 regression value가 큰가. 하나라도 답할 수 없으면 추가하지 않는다.
- verification-only 변경 규모가 production/engineering 변경보다 커지는 작업은 자동 진행하지 않는다. baseline/evaluation/failure-injection처럼 milestone 자체가 측정인 경우를 제외하고, 왜 기존 자산 재사용으로 해결할 수 없는지 먼저 설명해야 한다.
- generated artifacts, logs, one-off investigation output은 source control에 영구 보존할 필요가 있을 때만 commit한다. 그렇지 않으면 CI artifact 또는 PR evidence로 남긴다.
- validation 성공은 프로젝트 목표 달성과 동일하지 않다. 완료 보고에는 반드시 '무엇이 실제로 개선되었는가'를 별도로 적고, 답이 '검증이 추가되었다'뿐이면 그 작업의 필요성을 재검토한다.


## Autonomous Program execution lane

일반 Work Package 방식은 기본값으로 유지한다. 다만 사용자가 하나의 장기 목표와 단계/DAG를 충분히
검토한 뒤 **Autonomous Program 전체를 명시적으로 승인**한 경우, 반복적인 "다음 작업 진행" 지시를
줄이기 위해 좁은 program-level autonomy를 사용할 수 있다.

Autonomous Program은 다음 파일을 durable source로 사용한다.

- `.agents/programs/<program-id>.yml` — 목표, 단계/DAG, 성공 기준, 허용된 자동 전이, human gate,
  stop condition;
- `.agents/state/<program-id>.json` — 현재 phase, active Work Package/PR, bound base SHA, blocker,
  다음 eligible phase;
- 관련 `docs/plans/active/` plan — 사람이 검토할 수 있는 문제/제품 관점과 evidence policy.

### Program approval and activation

- program manifest를 repository에 추가한 것 자체는 프로그램 실행 승인이 아니다.
- 사용자가 program ID를 명시적으로 승인해야 `AWAITING_PROGRAM_APPROVAL → ACTIVE`로 전이할 수 있다.
- activation 시 remote `main`을 한 번 읽고 `activationBaseSha`를 기록한다.
- 실제 구현 Work Package는 기존 규칙대로 각 activation 시점의 exact main SHA에 묶이고, active
  Work Package 중 main drift가 발생하면 STOP한다.
- 동시에 active implementation Work Package는 하나만 허용한다.

### Codex autonomy inside an approved Program

승인된 program 안에서 **이미 실행 중인 Codex Cloud task/Goal**은 별도의 "다음 작업" 사용자 입력 없이
다음 eligible repository 작업을 이어갈 수 있다. 이는 실행 권한이지 scheduler가 아니다. 저장소 state만으로
새 Codex task가 자동 생성된다고 가정하지 않는다. executor가 종료되면 다음 invocation은 durable state에서
재개하며, 별도 event integration이 실제 구성된 경우에만 그 invocation을 자동화할 수 있다.

1. program/state/current main을 읽어 next eligible phase를 결정한다;
2. declared phase objective와 acceptance 안에서 bounded Work Package를 생성/활성화한다;
3. repository 조사, 구현, test, 동일 failure class의 1회 bounded repair, evidence/state 갱신,
   PR 생성까지 수행한다;
4. PR/CI가 완료되고 merge 후 repository state가 갱신되면 다음 eligible repository-only phase를
   선택한다.

이는 **목표를 스스로 발명하는 권한이 아니다**. manifest에 없는 phase, 새로운 기술/architecture,
acceptance 완화, unrelated cleanup은 program autonomy에 포함되지 않는다.

### Mandatory human gates

Program manifest가 더 엄격한 gate를 정의할 수 있으며, 최소한 다음은 자동 진행하지 않는다.

- program 최초 승인;
- product/architecture decision이 필요한 분기;
- 현실 truth/label처럼 모델이 스스로 정답을 만들면 안 되는 freeze;
- IAM 또는 security boundary 확대;
- 새 paid/cost-increasing live cloud action;
- destructive teardown;
- 자동 merge가 별도로 명시적으로 승인되지 않은 production PR merge.

CI PASS만으로 다음 phase acceptance를 만들지 않는다. 자연 실패는 evidence로 남기며 rerun-until-green을
금지한다.

### Product Trust program

현재 정의된 첫 Autonomous Program은 `product-trust-v1`이다.

- contract: `.agents/programs/product-trust-v1.yml`
- state: `.agents/state/product-trust-v1.json`
- plan: `docs/plans/active/product-trust-modernization.md`

이 program은 Kubernetes/GKE 플랫폼 재선정, MSA 전환, capacity/HPA/HA, 새 observability 제품 또는
새 IdP 도입 자체를 목표로 하지 않는다. 현실 입력 fidelity, 사용자-visible trust, waiting experience,
access safety, browser-level user flow, CI/CD trust gates와 최종 product proof만 다룬다.

Program state가 `AWAITING_PROGRAM_APPROVAL`인 동안 PT phase를 시작하지 않는다.

## Completion report

작업 완료 시 항상 다음 항목을 보고한다.

- base SHA
- changed files
- performed validation
- result
- unresolved blockers (`none`인 경우에도 명시)
- immediate next single task 또는 `next_candidate_work_package`
- 다음 후보 Work Package를 기록하더라도 자동 실행 권한으로 해석하지 않으며 `AWAITING_APPROVAL`로 둔다.

완료 보고 전에 diff와 repository 상태를 확인하여 작업 범위를 벗어난 변경이 없는지 검증한다.
