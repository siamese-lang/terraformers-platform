import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import api from '../utils/api';
import ProjectDeleteButton from '../components/ProjectDeleteButton';
import AnalysisStatus from '../components/AnalysisStatus';

const visibilityDescriptions = {
  PRIVATE: '소유자만 조회할 수 있습니다.',
  PUBLIC: '커뮤니티에서 누구나 조회할 수 있습니다.',
};

function ProjectDetailPage() {
  const { projectId } = useParams();
  const navigate = useNavigate();
  const [project, setProject] = useState(null);
  const [terraform, setTerraform] = useState('');
  const [imageUrl, setImageUrl] = useState('');
  const [error, setError] = useState('');
  const [visibilityError, setVisibilityError] = useState('');
  const [deleteError, setDeleteError] = useState('');
  const [isUpdatingVisibility, setIsUpdatingVisibility] = useState(false);
  const objectUrlRef = useRef(null);

  useEffect(() => {
    let timer;
    let active = true;
    setProject(null);
    setError('');
    const load = async () => {
      try {
        const response = await api.get(`/api/projects/${projectId}`);
        if (!active) return;
        setError('');
        setProject(response.data);
        if (['PENDING', 'RUNNING'].includes(response.data.analysisStatus)) {
          timer = setTimeout(load, 2000);
        }
      } catch (err) {
        if (active) setError('서버의 최신 작업 상태를 확인하지 못했습니다. 표시된 내용은 마지막 확인 기록입니다. 페이지를 다시 불러와 확인해 주세요.');
      }
    };
    load();
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [projectId]);

  useEffect(() => {
    let active = true;
    if (!project?.sourceFileId) {
      setImageUrl('');
      return undefined;
    }
    api.get(`/api/projects/${projectId}/source-image`, { responseType: 'blob' }).then((response) => {
      if (!active) return;
      if (objectUrlRef.current) URL.revokeObjectURL(objectUrlRef.current);
      objectUrlRef.current = URL.createObjectURL(response.data);
      setImageUrl(objectUrlRef.current);
    });
    return () => {
      active = false;
    };
  }, [projectId, project?.sourceFileId]);

  useEffect(() => {
    let active = true;
    if (!project?.resultFileId) {
      setTerraform('');
      return undefined;
    }
    api.get(`/api/projects/${projectId}/terraform/main.tf`).then((response) => {
      if (active) setTerraform(response.data?.content || '');
    });
    return () => {
      active = false;
    };
  }, [projectId, project?.resultFileId]);

  useEffect(() => () => {
    if (objectUrlRef.current) URL.revokeObjectURL(objectUrlRef.current);
  }, []);

  const updateVisibility = async () => {
    const nextVisibility = project.visibility === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC';
    if (!window.confirm(`프로젝트를 ${nextVisibility === 'PUBLIC' ? '공개' : '비공개'}로 전환하시겠습니까?`)) return;

    setVisibilityError('');
    setIsUpdatingVisibility(true);
    try {
      const response = await api.patch(`/api/projects/${projectId}/visibility`, { visibility: nextVisibility });
      setProject(response.data);
    } catch (err) {
      setVisibilityError(err?.response?.data || err.message || '공개 범위를 변경하지 못했습니다.');
    } finally {
      setIsUpdatingVisibility(false);
    }
  };

  if (error && !project) return <p role="alert" className="error">{error}</p>;
  if (!project) return <p>프로젝트를 불러오는 중입니다.</p>;

  const isPublic = project.visibility === 'PUBLIC';
  return (
    <section className="page-stack">
      <Link to="/projects">내 프로젝트 목록으로 돌아가기</Link>
      <h1>{project.displayName}</h1>
      {error && <p role="alert" className="error">{error}</p>}
      <section className="project-detail-summary" aria-label="프로젝트 상태와 공개 범위">
        <AnalysisStatus key={project.latestAnalysisJobId || projectId} status={project.analysisStatus} quality={project.quality} timing={project.analysisTiming} />
        <p><strong>공개 범위: {project.visibility}</strong></p>
        <p>{visibilityDescriptions[project.visibility]}</p>
        {visibilityError && <p role="alert" className="error">공개 범위 변경 실패: {visibilityError}</p>}
        <button type="button" onClick={updateVisibility} disabled={isUpdatingVisibility}>
          {isUpdatingVisibility ? '변경 중...' : isPublic ? '비공개로 전환' : '공개하기'}
        </button>
      </section>
      {imageUrl && <img src={imageUrl} alt={`${project.displayName} architecture`} style={{ objectFit: 'contain', maxWidth: '100%', height: 'auto', maxHeight: 640 }} />}
      {project.failureReason && <p role="alert" className="error">{project.failureReason}</p>}
      {project.analysisStatus === 'FAILED' && <Link to="/generate">새 분석 시작</Link>}
      <p>{project.analysisSummary}</p>
      {project.detectedComponents?.length > 0 && <p><strong>구성요소:</strong> {project.detectedComponents.join(', ')}</p>}
      {project.detectedRelationships?.length > 0 && <p><strong>관계:</strong> {project.detectedRelationships.join(', ')}</p>}
      {project.warnings?.length > 0 && <p><strong>Warnings:</strong> {project.warnings.join(', ')}</p>}
      {project.resultFileId && <pre className="terraform-code"><code>{terraform}</code></pre>}
      <section className="project-danger-zone" aria-labelledby="project-delete-title">
        <h2 id="project-delete-title">프로젝트 관리</h2>
        <h3>프로젝트 삭제</h3>
        <p>프로젝트가 내 프로젝트와 공개 화면에서 제거되며, 더 이상 결과에 접근할 수 없습니다. 이 작업은 되돌릴 수 없습니다.</p>
        {deleteError && <p role="alert" className="error">{deleteError}</p>}
        <ProjectDeleteButton projectId={projectId} projectName={project.displayName || `Project ${projectId}`} onError={setDeleteError} onDeleted={() => navigate('/projects', { replace: true, state: { message: '프로젝트가 삭제되었습니다.' } })} />
      </section>
    </section>
  );
}

export default ProjectDetailPage;
