import { render, screen, waitFor } from '@testing-library/react';
import ProjectTreeReadOnly from './ProjectTreeReadOnly';
import api from '../utils/api';

jest.mock('../utils/api', () => ({ get: jest.fn() }));

beforeEach(() => {
  global.URL.createObjectURL = jest.fn(() => 'blob:image');
  global.URL.revokeObjectURL = jest.fn();
  api.get.mockImplementation((url) => {
    if (url === '/api/project-tree/1') return Promise.resolve({ data: { projectId: 1, tree: [] } });
    if (url.endsWith('/source-image')) return Promise.resolve({ data: new Blob(['image']) });
    return Promise.resolve({ data: { content: '' } });
  });
});

test('uses the containment wrapper for the source image', async () => {
  const { container } = render(<ProjectTreeReadOnly selectedProjectId="1" />);
  await waitFor(() => expect(container.querySelector('.project-source-image-wrapper')).toBeInTheDocument());
  expect(screen.getByAltText('Persisted architecture')).toHaveClass('project-source-image');
});

test('public detail carries the same bounded quality and terminal timing alongside the draft', async () => {
  api.get.mockImplementation((url) => {
    if (url === '/api/project-tree/1') return Promise.resolve({ data: { projectId: 1, analysisStatus: 'SUCCEEDED', tree: [],
      quality: { contractVersion: 'evidence-quality-v1', qualityStatus: 'DEGRADED', technicalStatus: 'PASS', runtimeQualityBoundary: 'CONDITIONAL_ON_EXTRACTED_FACTS', reasons: ['CLOUDFRONT_S3_ORIGIN_AUTHORIZATION_MISSING'] },
      analysisTiming: { acceptedAt: '2026-10-07T00:00:00Z', terminalAt: '2026-10-07T00:02:03Z', acceptedToTerminalMs: 123000 },
    } });
    if (url.endsWith('/source-image')) return Promise.resolve({ data: new Blob(['image']) });
    return Promise.resolve({ data: { content: 'resource "aws_s3_bucket" "draft" {}' } });
  });
  render(<ProjectTreeReadOnly selectedProjectId="1" />);
  expect(await screen.findByText('결과 품질: 검토 필요 (DEGRADED)')).toBeInTheDocument();
  expect(screen.getByText('CLOUDFRONT_S3_ORIGIN_AUTHORIZATION_MISSING')).toBeInTheDocument();
  expect(screen.getByText('접수부터 처리 종료까지: 2분 3초')).toBeInTheDocument();
  expect(await screen.findByText(/aws_s3_bucket/)).toBeInTheDocument();
});

test('shows a logical source key without constructing a provider-specific URI', async () => {
  api.get.mockImplementation((url) => {
    if (url === '/api/project-tree/1') {
      return Promise.resolve({
        data: {
          projectId: 1,
          tree: [{
            id: 'source-file-1',
            type: 'file',
            name: 'architecture.png',
            sourceBucket: 'portable-validation-bucket',
            sourceKey: 'browser-uploads/1/architecture.png',
          }],
        },
      });
    }
    if (url.endsWith('/source-image')) return Promise.resolve({ data: new Blob(['image']) });
    return Promise.resolve({ data: { content: '' } });
  });

  render(<ProjectTreeReadOnly selectedProjectId="1" />);

  expect(await screen.findByText('browser-uploads/1/architecture.png')).toBeInTheDocument();
  expect(screen.queryByText('s3://portable-validation-bucket/browser-uploads/1/architecture.png')).not.toBeInTheDocument();
});
