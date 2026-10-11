import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { getCurrentUser, isFirebaseAuth, signOut, subscribeAuthState } from './authClient';
import api from '../utils/api';

const AuthSessionContext = createContext(undefined);

export function AuthSessionProvider({ children }) {
  const [status, setStatus] = useState('checking');
  const [user, setUser] = useState(null);
  const [error, setError] = useState('');
  const refreshSequence = useRef(0);

  const refresh = useCallback(async () => {
    const sequence = ++refreshSequence.current;
    setStatus('checking');
    setError('');

    try {
      const currentUser = await getCurrentUser();
      if (sequence !== refreshSequence.current) return false;
      if (!currentUser) {
        setUser(null);
        setStatus('guest');
        return false;
      }

      setUser(currentUser);
      if (isFirebaseAuth && currentUser.emailVerified === false) {
        setStatus('verification-required');
        return false;
      }
      setStatus('authenticated');
      if (currentUser.nickname.trim()) {
        api.patch('/api/users/me/display-name', { displayName: currentUser.nickname }).catch(() => {
          // Profile synchronization is intentionally best-effort and must not affect login.
        });
      }
      return true;
    } catch (authError) {
      if (sequence !== refreshSequence.current) return false;
      setUser(null);
      setStatus('guest');

      console.error('[auth] Failed to resolve the current user.', {
        name: authError?.name,
        code: authError?.code,
      });
      setError(authError?.code === 'auth/configuration'
        ? authError.message : '로그인 상태를 확인하지 못했습니다.');

      return false;
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => subscribeAuthState(() => { refresh(); }), [refresh]);

  useEffect(() => {
    const handleAuthExpired = () => {
      refreshSequence.current += 1;
      setError('로그인 세션이 만료되었습니다. 다시 로그인해 주세요.');
      setUser(null);
      setStatus('guest');
      if (isFirebaseAuth) signOut().catch(() => {});
    };

    window.addEventListener('terraformers:auth-expired', handleAuthExpired);
    return () => {
      window.removeEventListener('terraformers:auth-expired', handleAuthExpired);
    };
  }, []);

  const logout = useCallback(async () => {
    setError('');

    try {
      await signOut();
      refreshSequence.current += 1;
      setUser(null);
      setStatus('guest');
    } catch (signOutError) {
      console.error('[auth] Sign-out failed.', {
        name: signOutError?.name,
        code: signOutError?.code,
      });
      setError('로그아웃하지 못했습니다. 다시 시도해 주세요.');
      throw signOutError;
    }
  }, []);

  const value = useMemo(() => ({
    status,
    user,
    error,
    refresh,
    logout,
    isAuthenticated: status === 'authenticated',
  }), [status, user, error, refresh, logout]);

  return (
    <AuthSessionContext.Provider value={value}>
      {children}
    </AuthSessionContext.Provider>
  );
}

export function useAuthSession() {
  const context = useContext(AuthSessionContext);

  if (!context) {
    throw new Error('useAuthSession must be used inside AuthSessionProvider.');
  }

  return context;
}
