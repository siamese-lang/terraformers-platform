import { useEffect, useState } from 'react';

const processingLabels = {
  PENDING: '분석 요청이 대기 중입니다.',
  RUNNING: '분석 작업을 처리 중입니다.',
  SUCCEEDED: '분석 작업의 처리가 완료되었습니다.',
  FAILED: '분석 작업의 처리가 실패했습니다.',
};
const qualityLabels = {
  UNKNOWN: '확인 불가 (UNKNOWN)',
  DEGRADED: '검토 필요 (DEGRADED)',
  EVIDENCE_BACKED: '선택된 근거로 뒷받침됨 (EVIDENCE_BACKED)',
  NOT_APPLICABLE: '평가 대상 아님 (NOT_APPLICABLE)',
};
const reasonLabels = {
  CLOUDFRONT_S3_ORIGIN_AUTHORIZATION_MISSING: 'CloudFront에서 새 S3 원본을 읽기 위한 권한 선언을 찾지 못했습니다. 원본과 권한 설정을 검토하세요.',
  RESOURCE_UNKNOWN_TO_PROVIDER: '공급자 schema에서 확인되지 않은 리소스 유형이 있습니다.',
  OFFICIAL_KNOWLEDGE_NOT_AVAILABLE: '일부 리소스 유형의 공식 지식이 없습니다.',
  REQUIRED_EVIDENCE_NOT_RETRIEVED: '필요한 근거 일부를 검색하지 못했습니다.',
  REQUIRED_PROJECT_DECISION_NOT_RETRIEVED: '필요한 프로젝트 결정 근거 일부가 없습니다.',
  GENERATED_RESOURCE_UNSUPPORTED_BY_EVIDENCE: '생성된 리소스 일부를 선택된 근거가 뒷받침하지 못합니다.',
  PROVIDER_CONTENT_BLOCKED: '모델 공급자가 입력이나 응답을 차단했습니다.',
  PROVIDER_OUTPUT_TRUNCATED: '모델 응답이 잘려 완전한 초안을 얻지 못했습니다.',
  PROVIDER_EMPTY_RESPONSE: '모델 응답이 비어 있습니다.',
  PROVIDER_TIMEOUT: '모델 응답 시간이 초과되었습니다.',
  PROVIDER_RATE_LIMITED: '모델 공급자의 요청 한도에 도달했습니다.',
  PROVIDER_ERROR: '모델 공급자 호출에 실패했습니다.',
  PROVIDER_SCHEMA_FAILURE: 'Terraform 공급자 schema 검사에 실패했습니다.',
  TERRAFORM_EXECUTABLE_FAILURE: 'Terraform 실행 검사에 실패했습니다.',
};

function duration(ms) {
  const seconds = Math.floor(ms / 1000);
  return `${Math.floor(seconds / 60)}분 ${seconds % 60}초`;
}

function AnalysisStatus({ status, quality, timing, compact = false }) {
  const terminal = ['SUCCEEDED', 'FAILED'].includes(status);
  const active = ['PENDING', 'RUNNING'].includes(status);
  const accepted = timing?.acceptedAt ? Date.parse(timing.acceptedAt) : NaN;
  const ended = timing?.terminalAt ? Date.parse(timing.terminalAt) : NaN;
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (compact || !active || !Number.isFinite(accepted)) return undefined;
    setNow(Date.now());
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [active, accepted, compact]);

  const supported = quality?.contractVersion === 'evidence-quality-v1'
    && quality?.runtimeQualityBoundary === 'CONDITIONAL_ON_EXTRACTED_FACTS';
  const qualityLabel = !terminal ? '평가 전'
    : !quality ? '평가 기록 없음'
      : supported && qualityLabels[quality.qualityStatus]
        ? qualityLabels[quality.qualityStatus] : '확인 불가 (지원되지 않는 평가 기록)';
  const terminalMs = terminal && Number.isFinite(accepted) && Number.isFinite(ended)
    && ended >= accepted && Number.isFinite(timing?.acceptedToTerminalMs)
    && timing.acceptedToTerminalMs >= 0 ? timing.acceptedToTerminalMs : null;
  const Wrapper = compact ? 'div' : 'section';

  return (
    <Wrapper className="analysis-status" aria-label="분석 처리와 결과 품질">
      <p><strong>처리 상태 (서버 기록): {status || 'NO_ANALYSIS'}</strong> {processingLabels[status] || (status ? '처리 상태를 확인할 수 없습니다.' : '분석 작업이 없습니다.')}</p>
      <p><strong>결과 품질: {qualityLabel}</strong></p>
      {!compact && <>
        {terminal && <p>처리 완료와 결과의 신뢰도는 별개입니다. Terraform은 검토하고 수정할 수 있는 초안입니다.</p>}
        {terminal && quality && <>
          <p>기술 검사: {quality.technicalStatus === 'PASS' ? '통과 (PASS)' : quality.technicalStatus === 'FAIL' ? '실패 (FAIL)' : '확인 불가'}</p>
          <p>리소스 지식: {quality.knowledgeStatus || 'UNKNOWN'} · 프로젝트 결정: {quality.projectDecisionStatus || 'UNKNOWN'}</p>
          {Array.isArray(quality.reasons) && quality.reasons.length > 0 && <ul aria-label="결과 검토 사유">{quality.reasons.map((reason) => <li key={reason}>{reasonLabels[reason] || '추가 검토 사유가 기록되어 있습니다.'} <code>{reason}</code></li>)}</ul>}
        </>}
        {terminal && <p>품질 평가는 이미지에서 추출한 해석과 선택된 근거에 한정됩니다. 기술 검사 통과나 권한 선언의 존재만으로 실제 권한 유효성 또는 배포 성공이 보장되지 않습니다.</p>}
        {Number.isFinite(accepted) && <p>요청 접수: <time dateTime={timing.acceptedAt}>{new Date(accepted).toLocaleString()}</time></p>}
        {terminal && (terminalMs !== null
          ? <p>접수부터 처리 종료까지: {duration(terminalMs)}</p>
          : <p>접수부터 처리 종료까지: 기록 없음 또는 확인 불가</p>)}
        {terminal && Number.isFinite(ended) && <p>처리 종료: <time dateTime={timing.terminalAt}>{new Date(ended).toLocaleString()}</time></p>}
        {active && <>
          <p>{Number.isFinite(accepted) && now >= accepted ? `접수 후 경과 (기기 시각 기준): 약 ${duration(now - accepted)}` : '접수 후 경과 시간을 확인할 수 없습니다.'}</p>
          <p>처리 시간은 요청과 모델 응답에 따라 달라집니다. 완료 시각을 예측할 수 없습니다.</p>
          <p>다른 페이지로 이동해도 서버에 저장된 작업 상태를 내 프로젝트에서 다시 확인할 수 있습니다.</p>
        </>}
      </>}
    </Wrapper>
  );
}

export default AnalysisStatus;
