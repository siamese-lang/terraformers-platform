import React, { useState } from 'react';
import {
  authErrorMessage, confirmSignUp, isFirebaseAuth, resendVerification,
} from '../auth/authClient';
import { useNavigate } from 'react-router-dom';
import { useAuthSession } from '../auth/AuthSessionContext';

function ConfirmSignUpPage() {
  const [confirmationCode, setConfirmationCode] = useState('');
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState('');
  const navigate = useNavigate();
  const { refresh, user } = useAuthSession();

  const handleSubmit = async (event) => {
    event.preventDefault();
    setLoading(true);
    setError('');

    try {
      await confirmSignUp({
        username: email,
        confirmationCode,
      });
      if (isFirebaseAuth) {
        if (await refresh()) navigate('/generate', { replace: true });
        else setError('이메일 인증을 확인하지 못했습니다. 다시 로그인해 주세요.');
      } else navigate('/login');
    } catch (confirmError) {
      console.error('[auth] Account confirmation failed.', {
        name: confirmError?.name,
        code: confirmError?.code,
      });
      setError(authErrorMessage(confirmError));
    } finally {
      setLoading(false);
    }
  };

  const handleResend = async () => {
    setLoading(true);
    setError('');
    setNotice('');
    try {
      await resendVerification();
      setNotice('인증 메일을 다시 보냈습니다. 받은 편지함을 확인해 주세요.');
    } catch (resendError) {
      setError(authErrorMessage(resendError));
    } finally {
      setLoading(false);
    }
  };

  if (isFirebaseAuth) {
    return (
      <section id="entry-page">
        <form onSubmit={handleSubmit}>
          <h2>이메일 인증</h2>
          <p>{user?.email || '등록한 이메일'}로 보낸 인증 링크를 열어 완료해 주세요.</p>
          <p>인증 전에는 코드 생성과 내 프로젝트를 이용할 수 없습니다.</p>
          <button type="submit" disabled={loading}>{loading ? '확인 중...' : '인증 완료 확인'}</button>
          <button type="button" onClick={handleResend} disabled={loading}>인증 메일 다시 보내기</button>
          <button type="button" onClick={() => navigate('/login')} disabled={loading}>로그인으로 돌아가기</button>
          {notice && <p role="status">{notice}</p>}
          {error && <p role="alert" className="error">{error}</p>}
        </form>
      </section>
    );
  }

  return (
    <section id="entry-page">
      <form onSubmit={handleSubmit}>
        <h2>Confirm Your Account</h2>
        <fieldset>
          <legend>Email Confirmation</legend>
          <label htmlFor="email">Email</label>
          <input
            type="email"
            id="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
          <label htmlFor="confirmationCode">Confirmation Code</label>
          <input
            type="text"
            id="confirmationCode"
            value={confirmationCode}
            onChange={(event) => setConfirmationCode(event.target.value)}
            required
          />
        </fieldset>
        <button type="submit" disabled={loading}>{loading ? 'Confirming...' : 'Confirm'}</button>
        <button type="button" onClick={() => navigate('/login')} disabled={loading}>Back to login</button>
        {error && <p className="error">{error}</p>}
      </form>
    </section>
  );
}

export default ConfirmSignUpPage;
