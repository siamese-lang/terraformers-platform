import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { isGcpBrowserPreview } from '../auth/authClient';
import {
  getToken,
  requestPasswordReset,
  signIn,
  signUp,
} from '../auth/authClient';
import { useAuthSession } from '../auth/AuthSessionContext';
import '../styles/login.css';

function EntryPage() {
  const [currentView, setCurrentView] = useState('logIn');
  const [formData, setFormData] = useState({ nickname: '', email: '', username: '', password: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [previewToken, setPreviewToken] = useState('');
  const navigate = useNavigate();
  const { refresh } = useAuthSession();

  const changeView = (view) => {
    setCurrentView(view);
    setError('');
  };

  const handleInputChange = (event) => {
    setFormData({ ...formData, [event.target.id]: event.target.value });
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setLoading(true);
    setError('');

    try {
      if (currentView === 'signUp') {
        await signUp({
          email: formData.email,
          password: formData.password,
          nickname: formData.nickname,
        });
        navigate('/confirm-sign-up');
      } else if (currentView === 'logIn') {
        await signIn({
          username: formData.username,
          password: formData.password,
        });
        const token = await getToken('access');
        if (token !== null) {
          await refresh();
          navigate('/generate', { replace: true });
        } else {
          setError('Operation failed: missing access token after sign-in. Please check authentication configuration.');
        }
      } else if (currentView === 'PWReset') {
        await requestPasswordReset(formData.email);
        changeView('logIn');
      }
    } catch (authError) {
      console.error('[auth] Sign-up/sign-in failed. Check browser auth configuration.', {
        name: authError?.name,
        message: authError?.message,
      });
      setError(`Operation failed: ${authError.message}`);
    } finally {
      setLoading(false);
    }
  };

  const handlePreviewSignIn = async (event) => {
    event.preventDefault();
    setLoading(true);
    setError('');
    try {
      await signIn({ token: previewToken.trim() });
      setPreviewToken('');
      if (!await refresh()) {
        throw new Error('local session unavailable');
      }
      navigate('/generate', { replace: true });
    } catch (_) {
      setError('임시 테스트 토큰이 만료되었거나 유효하지 않습니다. 인증된 새 토큰을 사용하세요.');
    } finally {
      setLoading(false);
    }
  };

  // Only available through the development server, never through a production build.
  if (isGcpBrowserPreview) {
    return (
      <section id="entry-page">
        <form onSubmit={handlePreviewSignIn}>
          <h2>GCP Backend 개발 미리보기</h2>
          <p>운영 계정 로그인이 아닙니다. 관리자가 승인한 임시 JWT/JWKS 테스트 세션만 사용합니다.</p>
          <label htmlFor="preview-access-token">임시 Access Token</label>
          <input
            id="preview-access-token"
            type="password"
            autoComplete="off"
            spellCheck={false}
            value={previewToken}
            onChange={(event) => setPreviewToken(event.target.value)}
            required
          />
          <p>토큰은 브라우저 메모리에만 보관되며 새로고침하거나 로그아웃하면 사라집니다.</p>
          <button type="submit" disabled={loading}>
            {loading ? '확인 중...' : '개발용 세션 시작'}
          </button>
          {error && <p role="alert" className="error">{error}</p>}
        </form>
      </section>
    );
  }

  const renderView = () => {
    switch (currentView) {
      case 'signUp':
        return (
          <form onSubmit={handleSubmit}>
            <h2>Sign Up</h2>
            <fieldset>
              <legend>Create Account</legend>
              <label htmlFor="nickname">Nickname</label>
              <input type="text" id="nickname" value={formData.nickname} onChange={handleInputChange} required />
              <label htmlFor="email">Email</label>
              <input type="email" id="email" value={formData.email} onChange={handleInputChange} required />
              <label htmlFor="password">Password</label>
              <input type="password" id="password" value={formData.password} onChange={handleInputChange} autoComplete="current-password" required />
            </fieldset>
            <button type="submit" disabled={loading}>{loading ? 'Submitting...' : 'Submit'}</button>
            <button type="button" onClick={() => changeView('logIn')} disabled={loading}>Have an account?</button>
            {error && <p className="error">{error}</p>}
          </form>
        );
      case 'logIn':
        return (
          <form onSubmit={handleSubmit}>
            <h2>Welcome Back</h2>
            <fieldset>
              <legend>Log In</legend>
              <label htmlFor="username">Email</label>
              <input type="text" id="username" value={formData.username} onChange={handleInputChange} required />
              <label htmlFor="password">Password</label>
              <input type="password" id="password" value={formData.password} onChange={handleInputChange} autoComplete="current-password" required />
              <button type="button" className="link-button" onClick={() => changeView('PWReset')}>Forgot Password?</button>
            </fieldset>
            <button type="submit" disabled={loading}>{loading ? 'Logging in...' : 'Login'}</button>
            <button type="button" onClick={() => changeView('signUp')} disabled={loading}>Create an Account</button>
            {error && <p className="error">{error}</p>}
          </form>
        );
      case 'PWReset':
        return (
          <form onSubmit={handleSubmit}>
            <h2>Reset Password</h2>
            <fieldset>
              <legend>Password Reset</legend>
              <p>A reset link will be sent to your inbox.</p>
              <label htmlFor="email">Email</label>
              <input type="email" id="email" value={formData.email} onChange={handleInputChange} required />
            </fieldset>
            <button type="submit" disabled={loading}>{loading ? 'Sending...' : 'Send Reset Link'}</button>
            <button type="button" onClick={() => changeView('logIn')} disabled={loading}>Go Back</button>
            {error && <p className="error">{error}</p>}
          </form>
        );
      default:
        return null;
    }
  };

  return <section id="entry-page">{renderView()}</section>;
}

export default EntryPage;
