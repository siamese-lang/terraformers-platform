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

## Working rules

- completed milestone을 임의로 재설계하거나 다시 열지 않는다.
- current active milestone plan이 있으면 첫 미완료 작업부터 진행한다.
- 한 작업은 하나의 논리적으로 검증 가능한 결과로 제한한다.
- 현재 repository를 확인하지 않고 과거 대화나 다른 repository 상태를 추측하지 않는다.
- `KEEP`으로 판정된 domain/business contract를 이유 없이 재작성하지 않는다.
- historical AWS implementation을 현재 active runtime으로 오인하지 않는다.
- 대규모 rewrite보다 existing abstraction과 contract의 재사용을 우선한다.
- architecture change가 발생하면 관련 source-of-truth 문서를 같은 작업에서 갱신한다.
- inventory의 `UNKNOWN/TBD`를 확인된 요구사항이나 선택된 제품으로 바꾸지 않는다. 먼저 evidence와 명시적 결정을 남긴다.
- 각 작업 시작 시 해당 작업이 네 개선 축 중 무엇을 직접 전진시키는지, 또는 그 축을 진행하기 위한 필수 선행조건인지 명시한다. 둘 다 아니면 기본 결정은 DEFER다.
- test, verifier, workflow, evidence document 자체를 프로젝트 개선 결과로 취급하지 않는다. 그것들은 product/runtime/engineering claim을 뒷받침하는 수단이다.

## New technology decision gate

새 구조나 기술은 다음 질문에 **모두** `yes`라고 답하고 그 evidence를 남길 수 있을 때만 도입한다. 하나라도 충족하지 않으면 기본 결정은 **DEFER**다.

1. 현재 구현에서 실제 문제가 관찰되었는가?
2. 문제를 재현하거나 측정한 evidence가 있는가?
3. proposed change가 그 문제를 직접 해결하는가?
4. 동일 조건에서 개선을 재검증할 수 있는가?

특히 다음 항목은 확정 기술이 아니며 별도의 evidence와 decision gate 없이 도입하지 않는다.

- RabbitMQ
- Transactional Outbox
- LangGraph
- persistent Python AI worker/service
- Keycloak
- Redis
- Kafka
- multi-agent architecture

이 목록의 항목을 근거 없이 `NEW` 또는 `REPLACE`로 승격하지 않는다.

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
- milestone closure는 원칙적으로 이미 통과한 evidence를 검토하고 source-of-truth 문서를 갱신하는 작업이다. closure 자체를 위해 '검증을 검증하는 verifier'나 self-referential workflow를 만들지 않는다.
- 동일 fixture/bootstrap/JWT/JWKS/Kind setup을 여러 script에 복제하지 않는다. 반복 사용이 확인되면 기존 harness를 확장하거나 공통 helper로 통합하고, 단일 시나리오 때문에 새 parallel harness를 만들지 않는다.
- 새로운 validation code를 제안하기 전에는 다음 네 질문에 답한다: (1) 어떤 실제 product/runtime failure를 잡는가, (2) 기존 test/check가 왜 부족한가, (3) 어떤 미래 변경에서도 재사용되는가, (4) 추가 maintenance cost보다 regression value가 큰가. 하나라도 답할 수 없으면 추가하지 않는다.
- verification-only 변경 규모가 production/engineering 변경보다 커지는 작업은 자동 진행하지 않는다. baseline/evaluation/failure-injection처럼 milestone 자체가 측정인 경우를 제외하고, 왜 기존 자산 재사용으로 해결할 수 없는지 먼저 설명해야 한다.
- generated artifacts, logs, one-off investigation output은 source control에 영구 보존할 필요가 있을 때만 commit한다. 그렇지 않으면 CI artifact 또는 PR evidence로 남긴다.
- validation 성공은 프로젝트 목표 달성과 동일하지 않다. 완료 보고에는 반드시 '무엇이 실제로 개선되었는가'를 별도로 적고, 답이 '검증이 추가되었다'뿐이면 그 작업의 필요성을 재검토한다.

## Completion report

작업 완료 시 항상 다음 항목을 보고한다.

- base SHA
- changed files
- performed validation
- result
- unresolved blockers (`none`인 경우에도 명시)
- immediate next single task

완료 보고 전에 diff와 repository 상태를 확인하여 작업 범위를 벗어난 변경이 없는지 검증한다.
