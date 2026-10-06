# Cloud-only Agent Execution Protocol

## Status

**SELECTED / READY FOR ADOPTION**

This protocol defines how Terraformers work continues when the user's local computer is not a
reliable execution host.

The repository remains the durable source of truth. Cloud agents may execute work, but no task may
depend on a local daemon, local checkout, local Docker engine, local scheduler, or a single chat
session remaining alive.

This protocol does not authorize any product/runtime architecture change. It governs how already
approved work is executed and reviewed.

## 1. Problem

The project has repeatedly hit three execution problems:

1. long-running CI or cloud work outlives an interactive chat turn;
2. local hardware is unsuitable for running a persistent coding agent, Docker stack, or local MCP
   servers;
3. after a long conversation or context switch, work can regress into repeated verification or
   proceed from stale state.

The failure is not lack of another coding tool. The missing boundary is a durable execution protocol
that keeps state in GitHub and treats conversations and cloud agents as replaceable workers.

## 2. Decision

Adopt a **human-gated, cloud-only agent execution model**.

```text
User
  |
  | decision approval / merge approval / live-cost approval
  v
ChatGPT / Work
  technical lead + reviewer
  |
  | frozen Work Package
  v
Codex Cloud
  bounded implementation executor
  |
  v
GitHub
  SSOT: branch / commits / PR / CI / evidence
  |
  +-----------------------+
  |                       |
  v                       v
GitHub Actions         ChatGPT Work event task
CI / Terraform        optional PR-event review
  |
  v
GCP
```

The model intentionally does **not** require n8n, a local MCP host, or a local agent daemon.

## 3. Durable-state rule

Anything required to resume work after a browser, chat, agent, or machine disappears must exist in a
durable cloud source.

Authoritative durable state is limited to:

- GitHub `main`;
- the active/most recent Work Package under `.agents/work-packages/`;
- `docs/AI_PROJECT_STATE.md`;
- active decision/evaluation documents;
- PR diff, commits, reviews, comments and CI results;
- CI artifacts when evidence is intentionally not committed;
- explicitly approved cloud resources managed through repository-backed IaC.

The following are **not** durable state:

- local filesystem;
- an uncommitted Codex checkout;
- Cloud Shell home-directory files;
- terminal history;
- a local MCP process;
- an n8n workflow that contains project state not represented in GitHub;
- one ChatGPT/Codex conversation's private context.

A new agent must be able to recover the current task without reading a previous chat transcript.

## 4. Role boundaries

### User

The user retains authority for:

- Case/architecture decisions;
- Work Package approval when a new unit begins;
- merge approval;
- live inference when separately gated;
- paid/cost-increasing cloud actions;
- destructive operations;
- IAM/security-boundary expansion.

The user should not need to manually instruct routine steps such as "run tests", "read CI", "write
evidence", or "open the PR" when those actions are already allowed by an approved Work Package.

### ChatGPT / Work

Primary role: **technical lead, reviewer, and event-driven coordinator**.

Allowed within an approved Work Package:

- inspect current GitHub state;
- bind the exact execution base;
- prepare the bounded executor instruction;
- inspect PR/CI/log/evidence;
- perform acceptance review;
- write/update bounded work-branch documents when explicitly allowed;
- report `HUMAN_REQUIRED` when a stop condition is reached.

It must not silently convert a failed implementation into a new architecture decision.

### Codex Cloud

Primary role: **bounded implementation executor**.

Codex Cloud is preferred over local Codex for repository implementation because the work must not
depend on the user's computer remaining powered or responsive.

Each task receives:

- exact `execution_base_sha`;
- approved decision;
- allowed and prohibited paths;
- allowed and prohibited actions;
- acceptance criteria;
- existing verification commands;
- `auto_repair_limit`;
- explicit stop conditions;
- required completion evidence.

Codex does not choose the next Work Package in the default human-gated lane. In an explicitly approved Autonomous Program, Codex may choose only the next eligible Work Package already constrained by the durable program DAG.

### GitHub

GitHub is the orchestration ledger and source of truth.

It holds:

- branch/commit identity;
- PR state;
- reviewable diffs;
- CI results;
- evidence documents;
- Work Package state.

No other orchestration service may become a hidden second source of project truth.

### GitHub Actions

GitHub Actions is the execution boundary for repository CI and approved repository-backed cloud
operations.

Use existing workflows first.

Do not create a new workflow merely to move agent state from one step to another.

### Cloud Shell

Cloud Shell is **break-glass/bootstrap/manual inspection tooling**, not an execution coordinator.

A command that becomes a recurring project operation should move into repository-backed IaC,
workflow, script, or runbook instead of depending permanently on shell history.

## 5. Work Package lifecycle

The cloud-only execution unit remains an Approved Work Package.

### DRAFTED

The task has a candidate objective but is not approved.

No implementation is allowed.

### APPROVED

The user approves one logical outcome and its frozen acceptance contract.

If the execution base cannot be known until the contract merges, use
`approved_base_sha: BIND_AT_ACTIVATION`.

### ACTIVE

Immediately before the first write:

1. read remote GitHub `main` exactly once;
2. bind that exact SHA as `execution_base_sha`;
3. create the work branch from that SHA;
4. persist the bound SHA in the Work Package / PR evidence;
5. never auto-refresh/rebase to a newer `main`.

If `main` changes after activation, stop with `HUMAN_REQUIRED: MAIN_DRIFT`.

### EXECUTING

Within the approved scope the executor may autonomously:

1. implement;
2. run approved local/CI checks;
3. classify the first failure;
4. apply a bounded mechanical repair only if the Work Package allows it;
5. update evidence/state;
6. create or update the work PR.

The executor may not widen the requirement in order to make acceptance pass.

### PR_OPEN

Once a reviewable PR exists:

- normal CI runs;
- long-running CI is not polled continuously by an interactive chat;
- an event-driven Work task may inspect supported GitHub PR activity when available;
- absence of Work event automation does not block the protocol.

### HUMAN_REQUIRED

Stop when any configured `stop_on` condition is reached, including:

- main drift;
- same failure repeats after repair allowance;
- failure class changes;
- architecture/product decision required;
- allowed-path expansion required;
- acceptance criteria would have to change;
- IAM/security boundary would broaden;
- live/cost/destructive action is required;
- source evidence contradicts the selected decision.

At this state the agent reports:

- exact base/head;
- first blocking evidence;
- what was attempted;
- why further work would cross the contract;
- alternatives that need a human decision.

### ACCEPTANCE_READY

The executor has finished. ChatGPT reviews independently against the original acceptance contract.

CI green alone is not sufficient.

### MERGE_PENDING

If acceptance passes, the user receives:

- exact base/head;
- changed files;
- validation;
- acceptance result;
- residual risks;
- immediate next candidate.

The user decides whether to merge.

### CLOSED

After merge, repository state is authoritative.

The next Work Package remains unapproved until the user explicitly authorizes it, except inside an explicitly approved Autonomous Program whose durable DAG pre-authorizes that repository-only transition.

## 6. Event-driven continuation

Long waits should use events, not conversational polling.

### Supported now

GitHub already emits PR and workflow state, and ChatGPT Work supports webhook-based tasks for
authorized GitHub pull-request activity such as PR open/ready/close and, depending on the configured
trigger, reviews, comments, commit updates, or completed merges.

Recommended optional Work task:

**Trigger scope**

- repository: `siamese-lang/terraformers-platform`;
- pull-request activity only;
- prefer `ready for review`, commit update on an already-open approved-WP PR, or completed merge;
- do not trigger on every comment when it creates noise.

**Coordinator prompt**

> Read AGENTS.md, the PR base/head/diff, the bound Work Package, CI state, and the relevant decision
> and evidence documents. Determine whether the PR is still inside the approved Work Package. If CI
> is complete, perform acceptance review. If the failure is covered by remaining bounded-repair
> authority, report the exact permitted repair; otherwise mark HUMAN_REQUIRED. Never merge, start a
> new Work Package, change architecture, dispatch live cloud work, or broaden IAM.

The Work event task is a convenience coordinator, **not a source of authority**.

If the event task is unavailable, a new ChatGPT session can resume from GitHub using the same
procedure.

### Not assumed

This protocol does not assume that a Work event automatically launches a Codex Cloud task. Until
that is an explicitly supported and configured capability, Codex task dispatch remains an explicit
handoff after Work Package approval.

## 7. Resume algorithm

Any new ChatGPT/Work/Codex session resumes in this order:

1. fetch remote GitHub `main`;
2. read `AGENTS.md`;
3. read `docs/AI_PROJECT_STATE.md`;
4. identify the active or last-completed Work Package;
5. inspect any open PR for that Work Package;
6. verify its base/head and current CI state;
7. read only the decision/evidence documents referenced by the Work Package;
8. continue only the currently approved state transition.

Do not ask the user to reconstruct prior work unless repository evidence is genuinely insufficient.

Do not use a synthetic/local SHA mismatch by itself as evidence of stale GitHub state.

## 8. Autonomous repair boundary

Default:

`auto_repair_limit: 1`

A repair is autonomous only when all are true:

- same logical acceptance outcome;
- same failure class;
- allowed paths/actions already cover it;
- no architecture/product choice;
- no live/cost/security action;
- no acceptance weakening.

Examples:

Allowed:

- test fixture path correction;
- deterministic configuration typo;
- dependency wiring correction already implied by the selected design.

Stop:

- authentication failure becomes SQL compatibility failure;
- a managed-service candidate needs migration history redesign;
- a new queue/cache/database is proposed;
- CI would require a new permanent workflow outside the approved contract;
- cost/quota forces a different runtime topology.

## 9. Live cloud execution

Cloud writes remain repository-governed.

Preferred path:

```text
approved repository change
    -> GitHub Actions
    -> short-lived WIF identity
    -> Terraform / approved deployment command
    -> evidence
```

Do not give a general-purpose agent unrestricted GCP write authority merely to reduce clicks.

MCP, if later introduced, should default to read-only observation for quota/status/log/resource
inspection. GCP mutation remains GitHub Actions + WIF + repository-backed IaC unless a separately
approved decision changes this.

## 10. MCP and n8n policy

### MCP

MCP is an optional tool-access layer, not the orchestration backbone.

Adopt an MCP server only when:

- a required external system lacks an existing connected tool;
- remote/cloud hosting is possible;
- credentials can be short lived or appropriately scoped;
- the capability materially reduces manual inspection or handoff.

Do not add local stdio MCP servers as mandatory project infrastructure.

### n8n

n8n is **DEFERRED**.

Introduce it only if a measured orchestration problem appears that GitHub Actions + Work event tasks
cannot express cleanly, such as:

- a durable multi-system state machine across several non-GitHub services;
- long external waits with resumable cross-service steps;
- repeated routing between GitHub, GCP, Slack/Jira/email and agents;
- workflow state that cannot reasonably remain in GitHub.

If introduced later, n8n may coordinate events but must not become the exclusive home of
architecture decisions, acceptance state, or deployment truth.

## 11. No-local-runtime invariant

The protocol must continue to work if the user's local computer is:

- powered off;
- disconnected;
- too slow to run Docker;
- missing the repository checkout;
- replaced entirely.

Therefore:

- no mandatory local scheduler;
- no mandatory local MCP daemon;
- no mandatory local Docker;
- no local-only database;
- no local-only secrets;
- no local-only handoff file.

Local tools may be used opportunistically but never become a prerequisite.

## 12. Minimum automation level selected now

Adopt now:

- GitHub as durable SSOT;
- frozen Work Packages;
- Codex Cloud as bounded implementation executor;
- GitHub Actions as CI/cloud-execution engine;
- ChatGPT/Work as reviewer/coordinator;
- optional GitHub-PR event-triggered Work review;
- explicit human decision/merge/live-cost gates;
- event-driven continuation rather than long polling;
- repository-only resume algorithm.

Do not add now:

- n8n;
- local MCP servers;
- unrestricted GCP agent credentials;
- another project-state database;
- a new workflow solely to track agent lifecycle;
- automatic merge;
- automatic next-Work-Package chaining outside an explicitly approved Autonomous Program.


## 12A. Autonomous Program mode

The default execution unit remains one Approved Work Package. Autonomous Program mode is a narrow
extension for a user-approved multi-phase objective whose decisions and stop conditions can be
declared before execution.

A program requires:

- `.agents/programs/<id>.yml` with goal, success criteria, phase DAG, human gates, prohibited
  directions and stop conditions;
- `.agents/state/<id>.json` with current durable execution state;
- a human-readable active plan;
- explicit user approval of the program after those files are reviewable.

Until that approval, the state is `AWAITING_PROGRAM_APPROVAL` and Codex must not start phase work.

After approval, Codex Cloud may autonomously create/execute the next eligible repository Work
Package, run bounded validation/repair, update evidence/state and open a PR. It may not infer a new
program phase from an interesting failure or technology. A failure requiring an undeclared product
or architecture choice becomes `HUMAN_REQUIRED`.

Program autonomy does not imply automatic merge. Unless the program has a separately approved
merge policy, production/source PRs still wait for the ordinary merge checkpoint. Live inference,
GCP mutation, IAM/security expansion, cost increase and destructive operations always obey the
program's explicit human gates.

The first program using this mode is `product-trust-v1`. It intentionally excludes Kubernetes/GKE
platform reselection and unrelated production-hardening work; the retained representative runtime is
used only as needed to measure and prove user-facing product trust.

## 13. Acceptance criteria for this protocol

The protocol is adopted when repository instructions make all of the following explicit:

- no required local persistent runtime;
- GitHub is the durable state source;
- Work Package activation binds one exact remote-main SHA;
- Codex Cloud is the preferred implementation executor;
- interactive polling is replaced by event-driven continuation where supported;
- Work PR-event automation cannot merge or redesign; it may start/continue a next Work Package only when an explicitly approved Autonomous Program DAG already authorizes that transition;
- bounded repair and stop semantics remain authoritative;
- live/cost/security actions retain human checkpoints;
- MCP/n8n remain optional and evidence-driven;
- a new session can resume from repository state without the previous conversation.

## 14. External capability references

- OpenAI — ChatGPT Work and Codex:
  https://help.openai.com/en/articles/20001275-chatgpt-work-and-codex
- OpenAI — Using Codex with your ChatGPT plan:
  https://help.openai.com/en/articles/11369540-using-codex-with-your-chatgpt-plan
- OpenAI — Connecting GitHub to ChatGPT:
  https://help.openai.com/en/articles/11145903-connecting-github-to-chatgpt
- GitHub — Events that trigger workflows:
  https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows
- GitHub — Triggering a workflow:
  https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow
