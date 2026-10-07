import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import ProjectDetailPage from './ProjectDetailPage';
import api from '../utils/api';

jest.mock('../utils/api', () => ({ get: jest.fn(), patch: jest.fn(), delete: jest.fn() }));
const renderPage = () => render(<MemoryRouter initialEntries={['/projects/7']}><Routes><Route path="/projects/:projectId" element={<ProjectDetailPage />} /></Routes></MemoryRouter>);

test('separates completed processing from persisted UNKNOWN quality', async () => {
  api.get.mockResolvedValue({ data: {
    displayName: 'Diagram', analysisStatus: 'SUCCEEDED', resultFileId: null,
    quality: { contractVersion: 'evidence-quality-v1', technicalStatus: 'PASS',
      knowledgeStatus: 'COMPLETE', qualityStatus: 'UNKNOWN', projectDecisionStatus: 'UNKNOWN',
      runtimeQualityBoundary: 'CONDITIONAL_ON_EXTRACTED_FACTS', reasons: [] },
  } });
  renderPage();
  await screen.findByText('Diagram');
  expect(screen.getByText('결과 품질: 확인 불가 (UNKNOWN)')).toBeInTheDocument();
  expect(screen.getByText(/기술 검사: 통과/)).toBeInTheDocument();
});

beforeEach(() => { jest.useFakeTimers(); jest.clearAllMocks(); jest.spyOn(window, 'confirm').mockReturnValue(true); global.URL.createObjectURL = jest.fn(() => 'blob:source'); global.URL.revokeObjectURL = jest.fn(); });
afterEach(() => { window.confirm.mockRestore(); jest.useRealTimers(); });

test('polls metadata only until success, then loads each job-linked artifact once', async () => {
  let metadataCalls = 0;
  api.get.mockImplementation((url) => {
    if (url === '/api/projects/7') { metadataCalls += 1; return Promise.resolve({ data: { displayName: 'Diagram', analysisStatus: metadataCalls === 1 ? 'PENDING' : 'SUCCEEDED', sourceFileId: 10, resultFileId: metadataCalls === 1 ? null : 20 } }); }
    if (url.endsWith('source-image')) return Promise.resolve({ data: new Blob(['image']) });
    return Promise.resolve({ data: { content: 'resource "aws_s3_bucket" "x" {}' } });
  });
  renderPage();
  await screen.findByText(/대기 중/);
  expect(api.get).toHaveBeenCalledWith('/api/projects/7/source-image', { responseType: 'blob' });
  act(() => jest.advanceTimersByTime(2000));
  await waitFor(() => expect(screen.getByText(/aws_s3_bucket/)).toBeInTheDocument());
  expect(api.get.mock.calls.filter(([url]) => url === '/api/projects/7/source-image')).toHaveLength(1);
  expect(api.get.mock.calls.filter(([url]) => url === '/api/projects/7/terraform/main.tf')).toHaveLength(1);
});

test('shows failure without requesting Terraform and cleans object URLs on unmount', async () => {
  api.get.mockResolvedValueOnce({ data: { displayName: 'Diagram', analysisStatus: 'FAILED', sourceFileId: 10, resultFileId: null, failureReason: 'Bedrock request failed' } })
    .mockResolvedValueOnce({ data: new Blob(['image']) });
  const { unmount } = renderPage();
  await screen.findByText('Bedrock request failed');
  expect(api.get).not.toHaveBeenCalledWith('/api/projects/7/terraform/main.tf');
  unmount();
  expect(global.URL.revokeObjectURL).toHaveBeenCalledWith('blob:source');
});

test('shows durable coarse status and elapsed time with no invented model wait or fixed promise', async () => {
  jest.setSystemTime(new Date('2026-10-07T00:08:00Z'));
  api.get.mockResolvedValue({ data: { displayName: 'Diagram', analysisStatus: 'RUNNING', sourceFileId: null, resultFileId: null, analysisTiming: { acceptedAt: '2026-10-07T00:00:00Z' } } });
  renderPage();
  await screen.findByText(/분석 작업을 처리 중입니다/);
  expect(screen.getByText('접수 후 경과 (기기 시각 기준): 약 8분 0초')).toBeInTheDocument();
  expect(screen.getByText(/서버에 저장된 작업 상태를 내 프로젝트에서 다시 확인/)).toBeInTheDocument();
  expect(screen.queryByText('분석 모델의 응답을 기다리고 있습니다.')).not.toBeInTheDocument();
  act(() => jest.advanceTimersByTime(30000));
  expect(await screen.findByText('접수 후 경과 (기기 시각 기준): 약 8분 30초')).toBeInTheDocument();
  expect(screen.queryByText('분석 모델의 응답을 기다리고 있습니다.')).not.toBeInTheDocument();
  expect(screen.queryByText(/1~3분/)).not.toBeInTheDocument();
  expect(screen.queryByText(/%/)).not.toBeInTheDocument();
});

test('retains the last durable snapshot while explicitly reporting a failed status refresh', async () => {
  api.get.mockResolvedValueOnce({ data: { displayName: 'Last snapshot', analysisStatus: 'RUNNING', sourceFileId: null } })
    .mockRejectedValueOnce(new Error('network'));
  renderPage();
  await screen.findByText('Last snapshot');
  act(() => jest.advanceTimersByTime(2000));
  expect(await screen.findByRole('alert')).toHaveTextContent('마지막 확인 기록');
  expect(screen.getByText('Last snapshot')).toBeInTheDocument();
});

test('a completed DEGRADED result retains the editable draft and the semantic reason across reload', async () => {
  api.get.mockImplementation((url) => Promise.resolve({ data: url.endsWith('/main.tf') ? { content: 'resource "aws_s3_bucket" "editable" {}' } : {
    displayName: 'Review draft', analysisStatus: 'SUCCEEDED', resultFileId: 20,
    quality: { contractVersion: 'evidence-quality-v1', technicalStatus: 'PASS', qualityStatus: 'DEGRADED', runtimeQualityBoundary: 'CONDITIONAL_ON_EXTRACTED_FACTS', reasons: ['CLOUDFRONT_S3_ORIGIN_AUTHORIZATION_MISSING'] },
  } }));
  const { unmount } = renderPage();
  await screen.findByText(/aws_s3_bucket/);
  expect(screen.getByText('결과 품질: 검토 필요 (DEGRADED)')).toBeInTheDocument();
  expect(screen.getByText('CLOUDFRONT_S3_ORIGIN_AUTHORIZATION_MISSING')).toBeInTheDocument();
  unmount();
  renderPage();
  await screen.findByText(/aws_s3_bucket/);
  expect(screen.getByText('결과 품질: 검토 필요 (DEGRADED)')).toBeInTheDocument();
});

test('shows safe failure reason and link to start a new analysis', async () => {
  api.get.mockResolvedValue({ data: { displayName: 'Diagram', analysisStatus: 'FAILED', sourceFileId: null, resultFileId: null, failureReason: 'AI 모델의 응답 시간이 초과되었습니다. 잠시 후 새 분석을 시작해 주세요.' } });
  renderPage();
  expect(await screen.findByText('AI 모델의 응답 시간이 초과되었습니다. 잠시 후 새 분석을 시작해 주세요.')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '새 분석 시작' })).toHaveAttribute('href', '/generate');
});

const project = (visibility) => ({ displayName: 'Diagram', visibility, analysisStatus: 'SUCCEEDED', sourceFileId: null, resultFileId: null });

test('shows a private project visibility and its publish control', async () => {
  api.get.mockResolvedValue({ data: project('PRIVATE') });

  renderPage();

  expect(await screen.findByText('공개 범위: PRIVATE')).toBeInTheDocument();
  expect(screen.getByText('소유자만 조회할 수 있습니다.')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '공개하기' })).toBeInTheDocument();
});

test('publishes a private project and updates the displayed project from the PATCH response', async () => {
  api.get.mockResolvedValue({ data: project('PRIVATE') });
  api.patch.mockResolvedValue({ data: project('PUBLIC') });
  renderPage();

  fireEvent.click(await screen.findByRole('button', { name: '공개하기' }));

  await waitFor(() => expect(api.patch).toHaveBeenCalledWith('/api/projects/7/visibility', { visibility: 'PUBLIC' }));
  expect(window.confirm).toHaveBeenCalled();
  expect(await screen.findByText('공개 범위: PUBLIC')).toBeInTheDocument();
  expect(screen.getByText('커뮤니티에서 누구나 조회할 수 있습니다.')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '비공개로 전환' })).toBeInTheDocument();
  expect(api.get.mock.calls.filter(([url]) => url === '/api/projects/7')).toHaveLength(1);
});

test('requests a private visibility change for a public project', async () => {
  api.get.mockResolvedValue({ data: project('PUBLIC') });
  api.patch.mockResolvedValue({ data: project('PRIVATE') });
  renderPage();

  fireEvent.click(await screen.findByRole('button', { name: '비공개로 전환' }));

  await waitFor(() => expect(api.patch).toHaveBeenCalledWith('/api/projects/7/visibility', { visibility: 'PRIVATE' }));
  expect(await screen.findByText('공개 범위: PRIVATE')).toBeInTheDocument();
});

test('disables the visibility button while the PATCH request is pending', async () => {
  api.get.mockResolvedValue({ data: project('PRIVATE') });
  let resolvePatch;
  api.patch.mockImplementation(() => new Promise((resolve) => { resolvePatch = resolve; }));
  renderPage();

  fireEvent.click(await screen.findByRole('button', { name: '공개하기' }));

  expect(screen.getByRole('button', { name: '변경 중...' })).toBeDisabled();
  resolvePatch({ data: project('PUBLIC') });
  expect(await screen.findByRole('button', { name: '비공개로 전환' })).toBeInTheDocument();
});

test('shows a visibility error and keeps the existing visibility when PATCH fails', async () => {
  api.get.mockResolvedValue({ data: project('PUBLIC') });
  api.patch.mockRejectedValue({ response: { data: '공개 범위를 변경할 권한이 없습니다.' } });
  renderPage();

  fireEvent.click(await screen.findByRole('button', { name: '비공개로 전환' }));

  expect(await screen.findByText('공개 범위 변경 실패: 공개 범위를 변경할 권한이 없습니다.')).toBeInTheDocument();
  expect(screen.getByText('공개 범위: PUBLIC')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '비공개로 전환' })).toBeInTheDocument();
});

test('deletes from the detail danger zone and replaces the detail route', async () => {
  api.get.mockResolvedValue({ data: project('PRIVATE') });
  api.delete.mockResolvedValue({});
  render(<MemoryRouter initialEntries={['/projects/7']}><Routes><Route path="/projects/:projectId" element={<ProjectDetailPage />} /><Route path="/projects" element={<p>목록으로 이동</p>} /></Routes></MemoryRouter>);
  fireEvent.click(await screen.findByRole('button', { name: 'Diagram 프로젝트 삭제' }));
  await waitFor(() => expect(api.delete).toHaveBeenCalledWith('/api/projects/7'));
  expect(await screen.findByText('목록으로 이동')).toBeInTheDocument();
});

test('keeps detail content and separates deletion failure from visibility errors', async () => {
  api.get.mockResolvedValue({ data: project('PUBLIC') });
  api.delete.mockRejectedValue(new Error('internal details'));
  renderPage();
  fireEvent.click(await screen.findByRole('button', { name: 'Diagram 프로젝트 삭제' }));
  expect(await screen.findByText('프로젝트 삭제에 실패했습니다. 잠시 후 다시 시도해 주세요.')).toBeInTheDocument();
  expect(screen.getByText('공개 범위: PUBLIC')).toBeInTheDocument();
});
