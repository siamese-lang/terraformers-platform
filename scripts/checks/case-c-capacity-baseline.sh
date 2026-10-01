#!/usr/bin/env bash
set -euo pipefail

# Case C closed-loop capacity baseline. This script performs live requests; callers must enforce
# the protected workflow confirmation gate before invoking it.
: "${GITHUB_RUN_ID:?GITHUB_RUN_ID is required}"
: "${GITHUB_SHA:?GITHUB_SHA is required}"
: "${EXPECTED_MAIN_SHA:?EXPECTED_MAIN_SHA is required}"
: "${EXPECTED_BACKEND_IMAGE:?EXPECTED_BACKEND_IMAGE is required}"
: "${EXPECTED_BACKEND_SOURCE_SHA:?EXPECTED_BACKEND_SOURCE_SHA is required}"
: "${TOKEN_FILE:?TOKEN_FILE is required}"
: "${FIXTURE:?FIXTURE is required}"

NAMESPACE="${NAMESPACE:-terraformers-target}"
BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:18080}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${RUNNER_TEMP:-/tmp}/case-c-capacity-baseline}"
PRESAMPLE_SECONDS=30
ACTIVE_SECONDS=240
DRAIN_SECONDS=600
SAMPLE_SECONDS=5
STEPS=(1 2 4 6 8)
mkdir -p "$ARTIFACT_DIR/steps" "$ARTIFACT_DIR/tmp"
: > "$ARTIFACT_DIR/requests.jsonl"
PIDS=()

cleanup() {
  local pid
  for pid in "${PIDS[@]:-}"; do kill "$pid" >/dev/null 2>&1 || true; done
  for pid in "${PIDS[@]:-}"; do wait "$pid" >/dev/null 2>&1 || true; done
}
trap cleanup EXIT INT TERM

fail() { echo "case-c-capacity-baseline: $*" >&2; exit 1; }
jsonpath() { kubectl -n "$NAMESPACE" "$@"; }
require_equal() { [[ "$1" == "$2" ]] || fail "$3: expected '$2', observed '$1'"; }

[[ "$GITHUB_SHA" == "$EXPECTED_MAIN_SHA" ]] || fail "checked-out SHA does not match approved main"
[[ "$EXPECTED_MAIN_SHA" =~ ^[0-9a-f]{40}$ ]] || fail "invalid expected main SHA"
[[ "$EXPECTED_BACKEND_SOURCE_SHA" =~ ^[0-9a-f]{40}$ ]] || fail "invalid backend source SHA"
[[ "$EXPECTED_BACKEND_IMAGE" =~ @sha256:[0-9a-f]{64}$ ]] || fail "backend image is not immutable"
[[ -s "$TOKEN_FILE" ]] || fail "JWT token file is absent"
[[ -s "$FIXTURE" ]] || fail "fixture is absent"
command -v jq >/dev/null
command -v kubectl >/dev/null
command -v curl >/dev/null
command -v flock >/dev/null

fixture_sha="$(sha256sum "$FIXTURE" | awk '{print $1}')"
require_equal "$fixture_sha" a254981735b1060513251cfd9d6dcab82de8a818730f10b128d1c3904635c8c8 "fixture SHA-256"
backend_deployment="$ARTIFACT_DIR/tmp/backend-deployment.json"
opensearch_statefulset="$ARTIFACT_DIR/tmp/opensearch-statefulset.json"
backend_pod_json="$ARTIFACT_DIR/tmp/backend-pod.json"
nodes_json="$ARTIFACT_DIR/tmp/nodes.json"
config_json="$ARTIFACT_DIR/tmp/runtime-config.json"
jsonpath get deployment terraformers-backend -o json > "$backend_deployment"
jsonpath get statefulset terraformers-opensearch -o json > "$opensearch_statefulset"
jsonpath get pods -l app.kubernetes.io/name=terraformers-backend -o json > "$backend_pod_json"
kubectl get nodes -o json > "$nodes_json"
jsonpath get configmap terraformers-backend-runtime-config -o json > "$config_json"

# Fail closed on every frozen runtime invariant visible through the live control plane.
jq -e --arg image "$EXPECTED_BACKEND_IMAGE" '
  .spec.replicas == 1 and
  .spec.strategy.rollingUpdate.maxUnavailable == 1 and
  .spec.strategy.rollingUpdate.maxSurge == 0 and
  (.spec.template.spec.containers[] | select(.name == "backend") |
    .image == $image and .resources.requests.cpu == "250m" and
    .resources.limits.cpu == "1" and .resources.requests.memory == "512Mi" and
    .resources.limits.memory == "1Gi")
' "$backend_deployment" >/dev/null || fail "backend deployment identity drift"
jq -e '
  .spec.replicas == 1 and
  (.spec.template.spec.containers[] | select(.name == "opensearch") |
    (.env[] | select(.name == "OPENSEARCH_JAVA_OPTS").value) == "-Xms1g -Xmx1g" and
    .resources.requests.cpu == "1" and .resources.limits.cpu == "2" and
    .resources.requests.memory == "2Gi" and .resources.limits.memory == "4Gi")
' "$opensearch_statefulset" >/dev/null || fail "OpenSearch runtime identity drift"
jq -e '
  .data.ANALYSIS_PROVIDER == "vertex" and .data.EMBEDDING_PROVIDER == "vertex" and
  .data.RETRIEVAL_MODE == "REQUIRED" and .data.OBJECT_READER_PROVIDER == "gcs" and
  .data.OBJECT_WRITER_PROVIDER == "gcs" and .data.INDEX_NAME == "terraformers-reference-v3" and
  ((.data.ANALYSIS_DISPATCH_POLL_INTERVAL // "2s") == "2s") and
  ((.data.ANALYSIS_DISPATCH_BATCH_SIZE // "4") == "4")
' "$config_json" >/dev/null || fail "analysis contract or dispatcher identity drift"
require_equal "$(jq '.items | length' "$backend_pod_json")" 1 "backend pod count"
backend_pod="$(jq -r '.items[0].metadata.name' "$backend_pod_json")"
backend_uid="$(jq -r '.items[0].metadata.uid' "$backend_pod_json")"
backend_restarts="$(jq '[.items[0].status.containerStatuses[]?.restartCount] | add // 0' "$backend_pod_json")"
require_equal "$(jq -r '.items[0].status.phase' "$backend_pod_json")" Running "backend pod phase"
require_equal "$(jsonpath exec "$backend_pod" -c backend -- sh -c 'printf %s "$BUILD_SOURCE_REVISION"')" "$EXPECTED_BACKEND_SOURCE_SHA" "embedded backend source SHA"
require_equal "$(jq '[.items[] | select(.spec.unschedulable != true)] | length' "$nodes_json")" 2 "GKE node count"
jq -e 'all(.items[]; .metadata.labels["node.kubernetes.io/instance-type"] == "e2-standard-2")' "$nodes_json" >/dev/null || fail "GKE node shape drift"

mariadb_shape="$(gcloud compute instances describe terraformers-mariadb --project=terraformers-platform --zone=asia-northeast3-a --format='value(machineType.basename())')"
require_equal "$mariadb_shape" e2-medium "MariaDB shape"

health="$ARTIFACT_DIR/tmp/health.json"
curl -fsS --max-time 10 "$BACKEND_URL/actuator/health" -o "$health"
jq -e '.status == "UP"' "$health" >/dev/null || fail "backend health is not UP"
kubectl top pod -n "$NAMESPACE" "$backend_pod" >/dev/null || fail "backend CPU/memory metrics unavailable"
opensearch_pod="$(jsonpath get pod -l app.kubernetes.io/name=terraformers-opensearch -o jsonpath='{.items[0].metadata.name}')"
[[ -n "$opensearch_pod" ]] || fail "OpenSearch pod missing"
kubectl top pod -n "$NAMESPACE" "$opensearch_pod" >/dev/null || fail "OpenSearch CPU/memory metrics unavailable"

prometheus="$ARTIFACT_DIR/tmp/prometheus.txt"
curl -fsS --max-time 15 "$BACKEND_URL/actuator/prometheus" -o "$prometheus"
grep -Eq '^terraformers_analysis_(jobs|duration|stage_duration)' "$prometheus" || fail "required analysis metrics unavailable"
grep -Eq '^executor_active_threads\{[^}]*name="analysisJobExecutor"' "$prometheus" || fail "analysis executor active-worker gauge unavailable"
grep -Eq '^executor_queued_tasks\{[^}]*name="analysisJobExecutor"' "$prometheus" || fail "analysis executor queue-depth gauge unavailable"

os_stats="$ARTIFACT_DIR/tmp/opensearch-stats.json"
jsonpath exec "$opensearch_pod" -c opensearch -- curl -fsS 'http://127.0.0.1:9200/_nodes/stats/jvm,thread_pool,indices' > "$os_stats"
jq -e '.nodes | length == 1' "$os_stats" >/dev/null || fail "OpenSearch native stats unavailable"

jq -n \
  --arg workflowMainSha "$GITHUB_SHA" --arg backendImage "$EXPECTED_BACKEND_IMAGE" \
  --arg backendSourceSha "$EXPECTED_BACKEND_SOURCE_SHA" --arg backendPod "$backend_pod" \
  --arg backendPodUid "$backend_uid" --arg fixture "$FIXTURE" --arg fixtureSha256 "$fixture_sha" \
  --arg mariadbShape "$mariadb_shape" --argjson backendRestarts "$backend_restarts" \
  --slurpfile deployment "$backend_deployment" --slurpfile opensearch "$opensearch_statefulset" \
  --slurpfile nodes "$nodes_json" --slurpfile config "$config_json" '
  {
    workflow_main_sha:$workflowMainSha, backend_image_digest:$backendImage,
    embedded_backend_source_sha:$backendSourceSha, backend_pod:$backendPod,
    backend_pod_uid:$backendPodUid, backend_restart_count:$backendRestarts,
    fixture:$fixture, fixture_sha256:$fixtureSha256,
    analysis_executor:{core_threads:2,max_threads:4,queue_capacity:50},
    dispatcher:{poll_interval:"2s",batch_size:4}, mariadb:{instance:"terraformers-mariadb",shape:$mariadbShape},
    gke_nodes:[$nodes[0].items[] | {name:.metadata.name,shape:.metadata.labels["node.kubernetes.io/instance-type"]}],
    backend_resources:($deployment[0].spec.template.spec.containers[]|select(.name=="backend")|.resources),
    opensearch_resources:($opensearch[0].spec.template.spec.containers[]|select(.name=="opensearch")|.resources),
    opensearch_heap:"1Gi", deployment_strategy:$deployment[0].spec.strategy,
    effective_runtime_config:$config[0].data, real_analysis_requests:0
  }' > "$ARTIFACT_DIR/runtime-identity.json"

metric_value() {
  local file="$1" regex="$2"
  awk -v pattern="$regex" '$0 ~ pattern {sum += $NF; found=1} END {if(found) print sum; else print "null"}' "$file"
}

sample_metrics() {
  local step="$1" phase="$2" output="$3" now prom top_backend top_os stats pod_now current_backend_pod
  now="$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)"
  prom="$ARTIFACT_DIR/tmp/prom-${step}-${RANDOM}.txt"
  top_backend="$ARTIFACT_DIR/tmp/top-backend-${step}-${RANDOM}.txt"
  top_os="$ARTIFACT_DIR/tmp/top-os-${step}-${RANDOM}.txt"
  stats="$ARTIFACT_DIR/tmp/os-${step}-${RANDOM}.json"
  curl -fsS --max-time 15 "$BACKEND_URL/actuator/prometheus" -o "$prom"
  current_backend_pod="$(jsonpath get pods -l app.kubernetes.io/name=terraformers-backend -o jsonpath='{.items[0].metadata.name}')"
  [[ -n "$current_backend_pod" ]] || fail "backend pod absent during sampling"
  kubectl top pod -n "$NAMESPACE" "$current_backend_pod" --no-headers > "$top_backend"
  kubectl top pod -n "$NAMESPACE" "$opensearch_pod" --no-headers > "$top_os"
  jsonpath exec "$opensearch_pod" -c opensearch -- curl -fsS 'http://127.0.0.1:9200/_nodes/stats/jvm,thread_pool,indices' > "$stats"
  pod_now="$(jsonpath get pod "$current_backend_pod" -o json)"
  jq -n --arg timestamp "$now" --arg phase "$phase" --argjson step "$step" \
    --arg backendCpu "$(awk '{print $2}' "$top_backend")" --arg backendMemory "$(awk '{print $3}' "$top_backend")" \
    --arg osCpu "$(awk '{print $2}' "$top_os")" --arg osMemory "$(awk '{print $3}' "$top_os")" \
    --arg active "$(metric_value "$prom" '^executor_active_threads\{[^}]*name="analysisJobExecutor"')" \
    --arg queued "$(metric_value "$prom" '^executor_queued_tasks\{[^}]*name="analysisJobExecutor"')" \
    --arg rejected "$(metric_value "$prom" '^terraformers_analysis_executor_rejections_total')" \
    --arg jobsStarted "$(metric_value "$prom" '^terraformers_analysis_jobs_total\{[^}]*outcome="started"')" \
    --arg jobsSucceeded "$(metric_value "$prom" '^terraformers_analysis_jobs_total\{[^}]*outcome="succeeded"')" \
    --arg jobsFailed "$(metric_value "$prom" '^terraformers_analysis_jobs_total\{[^}]*outcome="failed"')" \
    --argjson telemetry "$(awk '/^(terraformers_analysis_|terraformers_vertex_|terraformers_aoss_|executor_)/' "$prom" | jq -Rsc 'split("\n")[:-1]')" \
    --argjson restarts "$(jq '[.status.containerStatuses[]?.restartCount] | add // 0' <<< "$pod_now")" \
    --arg uid "$(jq -r '.metadata.uid' <<< "$pod_now")" --slurpfile os "$stats" '
      def number_or_null: if . == "null" then null else tonumber end;
      ($os[0].nodes | to_entries[0].value) as $node |
      {timestamp:$timestamp,step:$step,phase:$phase,
       backend:{cpu:$backendCpu,memory:$backendMemory,pod_uid:$uid,restarts:$restarts},
       executor:{active_workers:($active|number_or_null),queue_depth:($queued|number_or_null),rejections_total:($rejected|number_or_null)},
       jobs:{started_total:($jobsStarted|number_or_null),succeeded_total:($jobsSucceeded|number_or_null),failed_total:($jobsFailed|number_or_null)},
       analysis_prometheus_series:$telemetry,
       opensearch:{cpu:$osCpu,memory:$osMemory,jvm_heap_used_bytes:$node.jvm.mem.heap_used_in_bytes,
         thread_pool_rejected:([$node.thread_pool[]?.rejected // 0]|add),
         query_total:($node.indices.search.query_total // null),query_time_ms:($node.indices.search.query_time_in_millis // null)}}
    ' >> "$output"
  rm -f "$prom" "$top_backend" "$top_os" "$stats"
}

collect_metrics() {
  local step="$1" phase="$2" seconds="$3" output="$4" end
  end=$(( $(date +%s) + seconds ))
  while (( $(date +%s) < end )); do sample_metrics "$step" "$phase" "$output"; sleep "$SAMPLE_SECONDS"; done
}

classify_failure() {
  local reason="${1,,}"
  if [[ "$reason" == *resource_exhausted* || "$reason" == *quota* || "$reason" == *rate\ limit* ]]; then echo external_provider_saturation
  elif [[ "$reason" == *rejected* || "$reason" == *capacity* || "$reason" == *oom* ]]; then echo capacity_failure
  elif [[ -n "$reason" && "$reason" != null ]]; then echo application_failure
  else echo none; fi
}

slot_worker() {
  local step="$1" slot="$2" active_deadline="$3" drain_deadline="$4" sequence=0 token project_name
  token="$(cat "$TOKEN_FILE")"
  while (( $(date +%s) < active_deadline )); do
    sequence=$((sequence + 1))
    project_name="case-c-capacity-${GITHUB_RUN_ID}-c${step}-s${slot}-n${sequence}"
    local request_start accepted_at terminal_at upload_ms code response job_file job_id project_id status failure category created updated end_ms
    request_start="$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)"
    response="$ARTIFACT_DIR/tmp/upload-${step}-${slot}-${sequence}.json"
    job_file="$ARTIFACT_DIR/tmp/job-${step}-${slot}-${sequence}.json"
    upload_ms="$ARTIFACT_DIR/tmp/upload-ms-${step}-${slot}-${sequence}.txt"
    code="$(curl -sS --max-time 60 -o "$response" -w '%{http_code} %{time_total}' \
      -H "Authorization: Bearer ${token}" -F "file=@${FIXTURE};type=image/webp" \
      -F "projectName=${project_name}" "$BACKEND_URL/api/upload" || true)"
    printf '%s\n' "${code#* }" > "$upload_ms"
    code="${code%% *}"
    accepted_at="$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)"
    job_id="$(jq -r '.analysisJobId // null' "$response" 2>/dev/null || echo null)"
    project_id="$(jq -r '.projectId // null' "$response" 2>/dev/null || echo null)"
    status=UPLOAD_FAILED; failure="HTTP_${code}"; category=acceptance_failure; created=null; updated=null
    if [[ "$code" == 201 && "$job_id" != null && "$project_id" != null ]]; then
      status=PENDING; failure=null; category=none
      while (( $(date +%s) < drain_deadline )); do
        local job_code
        job_code="$(curl -sS --max-time 30 -o "$job_file" -w '%{http_code}' -H "Authorization: Bearer ${token}" "$BACKEND_URL/api/analysis/jobs/${job_id}" || true)"
        if [[ "$job_code" == 200 ]]; then
          status="$(jq -r '.status // "UNKNOWN"' "$job_file")"
          created="$(jq -r '.createdAt // null' "$job_file")"; updated="$(jq -r '.updatedAt // null' "$job_file")"
          if [[ "$status" == SUCCEEDED || "$status" == FAILED ]]; then
            failure="$(jq -r '.failureReason // null' "$job_file")"
            category="$(classify_failure "$failure")"
            break
          fi
        fi
        sleep 5
      done
      if [[ "$status" != SUCCEEDED && "$status" != FAILED ]]; then status=DRAIN_TIMEOUT; failure=drain_timeout; category=capacity_failure; fi
    fi
    terminal_at="$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)"
    end_ms="$(python3 - "$request_start" "$terminal_at" <<'PY'
from datetime import datetime
import sys
p=lambda s: datetime.fromisoformat(s.replace('Z','+00:00'))
print(round((p(sys.argv[2])-p(sys.argv[1])).total_seconds()*1000))
PY
)"
    jq -cn --argjson step "$step" --argjson slot "$slot" --argjson sequence "$sequence" \
      --arg projectName "$project_name" --arg requestStartedAt "$request_start" --arg acceptedAt "$accepted_at" \
      --arg terminalAt "$terminal_at" --arg httpStatus "$code" --arg acceptanceSeconds "$(cat "$upload_ms")" \
      --arg jobId "$job_id" --arg projectId "$project_id" --arg status "$status" --arg failureReason "$failure" \
      --arg failureCategory "$category" --arg createdAt "$created" --arg updatedAt "$updated" --argjson endToEndMs "$end_ms" '
      {step:$step,slot:$slot,sequence:$sequence,project_name:$projectName,request_started_at:$requestStartedAt,
       upload_accepted_at:$acceptedAt,terminal_at:$terminalAt,http_status:($httpStatus|tonumber? // $httpStatus),
       acceptance_latency_ms:(($acceptanceSeconds|tonumber? // 0)*1000|round),project_id:($projectId|tonumber? // $projectId),
       analysis_job_id:$jobId,terminal_status:$status,job_created_at:$createdAt,job_updated_at:$updatedAt,
       queue_wait_ms:null,queue_wait_source:"aggregate_prometheus_only",end_to_end_ms:$endToEndMs,
       failure_reason:$failureReason,failure_category:$failureCategory}' \
      | flock "$ARTIFACT_DIR/requests.lock" tee -a "$ARTIFACT_DIR/requests.jsonl" >/dev/null
  done
}

aggregate_step() {
  local step="$1" metrics="$2" output="$3"
  python3 - "$step" "$ARTIFACT_DIR/requests.jsonl" "$metrics" "$output" <<'PY'
import json,sys,statistics
step=int(sys.argv[1])
def rows(path):
  with open(path) as f: return [json.loads(x) for x in f if x.strip()]
r=[x for x in rows(sys.argv[2]) if x['step']==step]; m=rows(sys.argv[3])
lat=[x['acceptance_latency_ms'] for x in r]; end=[x['end_to_end_ms'] for x in r]
active=[x for x in m if x['phase']=='active']
pressure=sum(x['executor']['active_workers']==4 and (x['executor']['queue_depth'] or 0)>0 for x in active)
first,last=(active[0] if active else None),(active[-1] if active else None)
delta=lambda path: None if not first or path(first) is None or path(last) is None else path(last)-path(first)
rejections=delta(lambda x:x['executor']['rejections_total'])
os_rejections=delta(lambda x:x['opensearch']['thread_pool_rejected'])
provider=any(x['failure_category']=='external_provider_saturation' for x in r)
capacity=any(x['failure_category']=='capacity_failure' for x in r)
restart=any(x['backend']['restarts'] != m[0]['backend']['restarts'] or x['backend']['pod_uid'] != m[0]['backend']['pod_uid'] for x in m)
queue_sat=bool(active) and pressure*2>=len(active)
classification='NONE'
if provider: classification='EXTERNAL_PROVIDER_SATURATION'
elif restart: classification='BACKEND_RESTART_OR_OOM'
elif (rejections or 0)>0: classification='EXECUTOR_REJECTION'
elif (os_rejections or 0)>0: classification='OPENSEARCH_REJECTION'
elif capacity: classification='CAPACITY_ATTRIBUTABLE_FAILURE'
elif queue_sat: classification='BACKEND_QUEUE_PRESSURE'
out={'step':step,'accepted_requests':sum(x['http_status']==201 for x in r),'http_status_counts':{},
 'terminal_jobs':sum(x['terminal_status'] in ('SUCCEEDED','FAILED') for x in r),
 'succeeded':sum(x['terminal_status']=='SUCCEEDED' for x in r),'failed':sum(x['terminal_status']!='SUCCEEDED' for x in r),
 'acceptance_latency_ms':{'count':len(lat),'min':min(lat) if lat else None,'median':statistics.median(lat) if lat else None,'max':max(lat) if lat else None},
 'end_to_end_ms':{'count':len(end),'min':min(end) if end else None,'median':statistics.median(end) if end else None,'max':max(end) if end else None},
 'active_metric_samples':len(active),'queue_pressure_samples':pressure,'executor_rejection_delta':rejections,
 'opensearch_rejection_delta':os_rejections,'backend_restart_or_uid_change':restart,
 'saturation':classification,'saturated':classification!='NONE'}
for x in r: out['http_status_counts'][str(x['http_status'])]=out['http_status_counts'].get(str(x['http_status']),0)+1
json.dump(out,open(sys.argv[4],'w'),indent=2);open(sys.argv[4],'a').write('\n')
PY
}

first_saturation_step=null
stop_after_step=null
executed=()
for step in "${STEPS[@]}"; do
  metrics="$ARTIFACT_DIR/steps/concurrency-${step}-metrics.jsonl"
  aggregate="$ARTIFACT_DIR/steps/concurrency-${step}-aggregate.json"
  : > "$metrics"
  collect_metrics "$step" pre_sample "$PRESAMPLE_SECONDS" "$metrics"
  active_deadline=$(( $(date +%s) + ACTIVE_SECONDS )); drain_deadline=$(( active_deadline + DRAIN_SECONDS ))
  collect_metrics "$step" active "$ACTIVE_SECONDS" "$metrics" & collector=$!; PIDS+=("$collector")
  workers=()
  for slot in $(seq 1 "$step"); do slot_worker "$step" "$slot" "$active_deadline" "$drain_deadline" & workers+=("$!"); PIDS+=("$!"); done
  wait "$collector"
  for pid in "${workers[@]}"; do wait "$pid"; done
  sample_metrics "$step" drain_complete "$metrics"
  aggregate_step "$step" "$metrics" "$aggregate"
  executed+=("$step")
  saturated="$(jq -r '.saturated' "$aggregate")"
  if [[ "$first_saturation_step" == null && "$saturated" == true ]]; then first_saturation_step="$step"; stop_after_step="$(awk -v s="$step" 'BEGIN{a[1]=2;a[2]=4;a[4]=6;a[6]=8; print (s in a?a[s]:s)}')"; fi
  [[ "$stop_after_step" != null && "$step" == "$stop_after_step" ]] && break
done

if [[ "$first_saturation_step" == null ]]; then classification=INCONCLUSIVE_FOR_SATURATION_EXTENSION
else classification="$(jq -r '.saturation' "$ARTIFACT_DIR/steps/concurrency-${first_saturation_step}-aggregate.json")"; fi
jq -n --arg classification "$classification" --arg firstStep "$first_saturation_step" --arg stopAfter "$stop_after_step" \
  --argjson executed "$(printf '%s\n' "${executed[@]}" | jq -Rsc 'split("\n")[:-1]|map(tonumber)')" \
  '{classification:$classification,first_saturation_step:($firstStep|tonumber? // null),stop_after_step:($stopAfter|tonumber? // null),executed_steps:$executed}' \
  > "$ARTIFACT_DIR/saturation-classification.json"
request_count="$(wc -l < "$ARTIFACT_DIR/requests.jsonl" | tr -d ' ')"
tmp_identity="$ARTIFACT_DIR/runtime-identity.tmp.json"
jq --argjson count "$request_count" '.real_analysis_requests=$count' "$ARTIFACT_DIR/runtime-identity.json" > "$tmp_identity"
mv "$tmp_identity" "$ARTIFACT_DIR/runtime-identity.json"
jq -n --arg status COMPLETED --argjson requests "$request_count" --slurpfile saturation "$ARTIFACT_DIR/saturation-classification.json" \
  --arg generatedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" '{status:$status,generated_at:$generatedAt,real_analysis_requests:$requests,saturation:$saturation[0]}' > "$ARTIFACT_DIR/summary.json"

echo "Case C baseline artifacts written to $ARTIFACT_DIR"
