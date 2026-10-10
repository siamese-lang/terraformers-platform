import { act, render, screen } from '@testing-library/react';
import AnalysisStatus from './AnalysisStatus';

const quality = (qualityStatus, extra = {}) => ({
  contractVersion: 'evidence-quality-v1', technicalStatus: 'PASS', knowledgeStatus: 'COMPLETE',
  qualityStatus, projectDecisionStatus: 'UNKNOWN',
  runtimeQualityBoundary: 'CONDITIONAL_ON_EXTRACTED_FACTS', reasons: [], ...extra,
});
const acceptedAt = '2026-10-07T00:00:00Z';

beforeEach(() => { jest.useFakeTimers(); jest.setSystemTime(new Date('2026-10-07T00:08:00Z')); });
afterEach(() => { jest.useRealTimers(); });

test.each([
  ['UNKNOWN', '결과 품질: 확인 불가 (UNKNOWN)'],
  ['DEGRADED', '결과 품질: 검토 필요 (DEGRADED)'],
  ['EVIDENCE_BACKED', '결과 품질: 선택된 근거로 뒷받침됨 (EVIDENCE_BACKED)'],
  ['NOT_APPLICABLE', '결과 품질: 평가 대상 아님 (NOT_APPLICABLE)'],
])('separates terminal processing from %s quality without a confidence score', (status, label) => {
  const { container } = render(<AnalysisStatus status="SUCCEEDED" quality={quality(status)} />);
  expect(screen.getByText(label)).toBeInTheDocument();
  expect(screen.getByText(/기술 검사: 통과/)).toBeInTheDocument();
  expect(screen.getByText(/프로젝트 결정: UNKNOWN/)).toBeInTheDocument();
  expect(screen.getByText(/처리 완료와 결과의 신뢰도는 별개/)).toBeInTheDocument();
  expect(screen.getByText(/이미지에서 추출한 해석과 선택된 근거에 한정/)).toBeInTheDocument();
  expect(container.textContent).not.toMatch(/%|확신도|정확도/);
});

test('semantic degradation is visible despite technical PASS and does not assert effective IAM access', () => {
  render(<AnalysisStatus status="SUCCEEDED" quality={quality('DEGRADED', { reasons: ['CLOUDFRONT_S3_ORIGIN_AUTHORIZATION_MISSING'] })} />);
  expect(screen.getByText(/읽기 위한 권한 선언을 찾지 못했습니다/)).toBeInTheDocument();
  expect(screen.getByText(/기술 검사: 통과/)).toBeInTheDocument();
  expect(screen.getByText(/실제 권한 유효성 또는 배포 성공이 보장되지 않습니다/)).toBeInTheDocument();
});

test('certificate issuance omission remains visible with technical PASS', () => {
  render(<AnalysisStatus status="SUCCEEDED" quality={quality('DEGRADED', { reasons: ['CLOUDFRONT_CERTIFICATE_VALIDATION_MISSING'] })} />);
  expect(screen.getByText(/새 인증서의 발급 완료 의존성을 찾지 못했습니다/)).toBeInTheDocument();
  expect(screen.getByText(/기술 검사: 통과/)).toBeInTheDocument();
  expect(screen.queryByText(/선택된 근거로 뒷받침됨/)).not.toBeInTheDocument();
});

test.each([
  quality('EVIDENCE_BACKED', { contractVersion: 'future-contract' }),
  quality('EVIDENCE_BACKED', { runtimeQualityBoundary: 'UNCONDITIONAL' }),
  quality('FUTURE_STATUS'),
])('unrecognized quality semantics cannot be shown as evidence-backed trust', (snapshot) => {
  render(<AnalysisStatus status="SUCCEEDED" quality={snapshot} />);
  expect(screen.getByText('결과 품질: 확인 불가 (지원되지 않는 평가 기록)')).toBeInTheDocument();
  expect(screen.queryByText(/선택된 근거로 뒷받침됨/)).not.toBeInTheDocument();
});

test('missing quality is explicit and unknown reason codes remain inspectable', () => {
  const { rerender } = render(<AnalysisStatus status="SUCCEEDED" />);
  expect(screen.getByText('결과 품질: 평가 기록 없음')).toBeInTheDocument();
  rerender(<AnalysisStatus status="FAILED" quality={quality('UNKNOWN', { technicalStatus: 'FAIL', reasons: ['FUTURE_REASON'] })} />);
  expect(screen.getByText('FUTURE_REASON')).toBeInTheDocument();
  expect(screen.getByText('기술 검사: 실패 (FAIL)')).toBeInTheDocument();
});

test('durable acceptance anchors elapsed time across unmount/remount instead of resetting a local stopwatch', () => {
  const props = { status: 'RUNNING', timing: { acceptedAt } };
  const { unmount } = render(<AnalysisStatus {...props} />);
  expect(screen.getByText('접수 후 경과 (기기 시각 기준): 약 8분 0초')).toBeInTheDocument();
  act(() => jest.advanceTimersByTime(10000));
  unmount();
  render(<AnalysisStatus {...props} />);
  expect(screen.getByText('접수 후 경과 (기기 시각 기준): 약 8분 10초')).toBeInTheDocument();
  expect(screen.queryByText(/모델의 응답을 기다리고/)).not.toBeInTheDocument();
  expect(screen.queryByText(/1~3분/)).not.toBeInTheDocument();
});

test('terminal duration freezes on the persisted terminal time even if later metadata was updated', () => {
  render(<AnalysisStatus status="FAILED" timing={{ acceptedAt, terminalAt: '2026-10-07T00:02:03.464Z', acceptedToTerminalMs: 123464 }} />);
  expect(screen.getByText('접수부터 처리 종료까지: 2분 3초')).toBeInTheDocument();
  act(() => jest.advanceTimersByTime(60000));
  expect(screen.getByText('접수부터 처리 종료까지: 2분 3초')).toBeInTheDocument();
});

test.each([
  { acceptedAt },
  { acceptedAt: 'invalid', terminalAt: 'invalid', acceptedToTerminalMs: 1 },
  { acceptedAt, terminalAt: '2026-10-06T00:00:00Z', acceptedToTerminalMs: -1 },
])('legacy or invalid terminal timing is unknown, never a later observation or updatedAt', (timing) => {
  render(<AnalysisStatus status="SUCCEEDED" timing={timing} />);
  expect(screen.getByText('접수부터 처리 종료까지: 기록 없음 또는 확인 불가')).toBeInTheDocument();
  expect(screen.queryByText(/접수 후 경과/)).not.toBeInTheDocument();
});

test('active state exposes only durable coarse progress with no fixed completion promise', () => {
  const { container } = render(<AnalysisStatus status="PENDING" timing={{ acceptedAt: '2026-10-08T00:00:00Z' }} />);
  expect(screen.getByText('접수 후 경과 시간을 확인할 수 없습니다.')).toBeInTheDocument();
  expect(screen.getByText('결과 품질: 평가 전')).toBeInTheDocument();
  act(() => jest.advanceTimersByTime(60000));
  expect(screen.getByText(/완료 시각을 예측할 수 없습니다/)).toBeInTheDocument();
  expect(container.textContent).not.toMatch(/%|1~3분|모델의 응답을 기다리고/);
});
