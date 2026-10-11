import { cognitoAmplifyAuthClient } from './providers/cognitoAmplifyAuthClient';
import { localGcpBrowserAuthClient } from './providers/localGcpBrowserAuthClient';
import { firebaseAuthClient } from './providers/firebaseAuthClient';

const provider = process.env.REACT_APP_AUTH_PROVIDER || 'cognito';
// The local GCP preview can never be enabled in a production build or Firebase mode.
export const isFirebaseAuth = provider === 'firebase';
export const isGcpBrowserPreview = !isFirebaseAuth && provider === 'cognito'
  && process.env.NODE_ENV === 'development'
  && process.env.REACT_APP_GCP_BROWSER_PREVIEW === 'true';
const unsupportedProvider = {
  initialize() {},
  getCurrentUser() {
    const error = new Error('REACT_APP_AUTH_PROVIDER는 firebase 또는 cognito여야 합니다.');
    error.code = 'auth/configuration';
    throw error;
  },
};
const authClient = isFirebaseAuth ? firebaseAuthClient
  : isGcpBrowserPreview ? localGcpBrowserAuthClient
    : provider === 'cognito' ? cognitoAmplifyAuthClient : unsupportedProvider;
const selectedClient = () => {
  if (authClient === unsupportedProvider) unsupportedProvider.getCurrentUser();
  return authClient;
};

// Application-facing authentication boundary.
export const initializeAuth = () => authClient.initialize();
export const subscribeAuthState = (listener) => authClient.subscribe?.(listener) || (() => {});
export const needsEmailVerification = () => authClient.needsEmailVerification?.() || false;
export const getCurrentUser = () => selectedClient().getCurrentUser();
export const getToken = (tokenType = 'access') => selectedClient().getToken(tokenType);
export const getApiToken = (forceRefresh = false) => selectedClient().getToken(
  isFirebaseAuth ? 'id' : 'access', forceRefresh,
);
export const signIn = (credentials) => selectedClient().signIn(credentials);
export const signUp = (account) => selectedClient().signUp(account);
export const confirmSignUp = (confirmation) => selectedClient().confirmSignUp(confirmation);
export const resendVerification = () => selectedClient().resendVerification();
export const requestPasswordReset = (username) => selectedClient().requestPasswordReset(username);
export const signOut = () => selectedClient().signOut();

const firebaseErrorMessages = {
  'auth/invalid-credential': '이메일 또는 비밀번호를 확인해 주세요.',
  'auth/email-already-in-use': '이미 등록된 이메일입니다. 로그인하거나 비밀번호를 재설정해 주세요.',
  'auth/weak-password': '더 안전한 비밀번호를 입력해 주세요.',
  'auth/too-many-requests': '요청이 많습니다. 잠시 후 다시 시도해 주세요.',
  'auth/network-request-failed': '연결을 확인한 뒤 다시 시도해 주세요.',
  'auth/email-not-verified': '인증 메일의 링크를 완료한 뒤 다시 확인해 주세요.',
  'auth/requires-sign-in': '인증 대기 중인 계정으로 다시 로그인해 주세요.',
};

export const authErrorMessage = (error) => {
  if (!isFirebaseAuth) return error?.message || '인증 요청에 실패했습니다.';
  if (error?.code === 'auth/configuration') return error.message;
  return firebaseErrorMessages[error?.code] || '인증 요청을 완료하지 못했습니다. 다시 시도해 주세요.';
};
