import { initializeApp } from 'firebase/app';
import {
  createUserWithEmailAndPassword,
  getAuth,
  getIdToken,
  onAuthStateChanged,
  reload,
  sendEmailVerification,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signOut as firebaseSignOut,
  updateProfile,
} from 'firebase/auth';

let auth;
let configurationError;

const invalidSessionCodes = new Set([
  'auth/invalid-user-token', 'auth/user-token-expired', 'auth/user-disabled',
]);

function configuration() {
  const apiKey = process.env.REACT_APP_FIREBASE_API_KEY?.trim();
  const authDomain = process.env.REACT_APP_FIREBASE_AUTH_DOMAIN?.trim();
  const projectId = process.env.REACT_APP_FIREBASE_PROJECT_ID?.trim();
  const appId = process.env.REACT_APP_FIREBASE_APP_ID?.trim();
  if (!apiKey || !/^AIza[0-9A-Za-z_-]{25,}$/.test(apiKey) || !authDomain ||
      !/^[a-z0-9-]+(?:\.[a-z0-9-]+)+$/i.test(authDomain) ||
      !projectId || !/^[a-z][a-z0-9-]{4,29}$/.test(projectId) ||
      !appId || !/^\d+:[^:\s]+:web:[^:\s]+$/.test(appId)) {
    const error = new Error('Firebase 로그인 설정을 확인해 주세요: REACT_APP_FIREBASE_API_KEY, AUTH_DOMAIN, PROJECT_ID, APP_ID.');
    error.code = 'auth/configuration';
    throw error;
  }
  return { apiKey, authDomain, projectId, appId };
}

function configuredAuth() {
  if (configurationError) throw configurationError;
  if (!auth) {
    const error = new Error('Firebase 로그인 설정을 초기화하지 못했습니다.');
    error.code = 'auth/configuration';
    throw error;
  }
  return auth;
}

async function currentFirebaseUser() {
  const client = configuredAuth();
  await client.authStateReady();
  return client.currentUser;
}

async function recoverInvalidSession(error) {
  if (!invalidSessionCodes.has(error?.code)) throw error;
  await firebaseSignOut(configuredAuth());
  return null;
}

export const firebaseAuthClient = {
  initialize() {
    try {
      auth = getAuth(initializeApp(configuration(), 'terraformers-firebase'));
      configurationError = undefined;
    } catch (error) {
      auth = undefined;
      configurationError = error;
    }
  },

  subscribe(listener) {
    if (configurationError || !auth) return () => {};
    return onAuthStateChanged(auth, listener);
  },

  needsEmailVerification() {
    return Boolean(auth?.currentUser && !auth.currentUser.emailVerified);
  },

  async getCurrentUser() {
    const user = await currentFirebaseUser();
    if (!user) return null;
    try {
      await reload(user);
    } catch (error) {
      return recoverInvalidSession(error);
    }
    if (configuredAuth().currentUser?.uid !== user.uid) return null;
    return {
      userId: user.uid,
      username: user.email || user.uid,
      email: user.email || '',
      nickname: user.displayName || '',
      emailVerified: user.emailVerified,
    };
  },

  async getToken(tokenType = 'id', forceRefresh = false) {
    if (tokenType !== 'id') return null;
    const user = await currentFirebaseUser();
    if (!user || !user.emailVerified) return null;
    try {
      return await getIdToken(user, forceRefresh);
    } catch (error) {
      return recoverInvalidSession(error);
    }
  },

  async signUp({ email, password, nickname }) {
    const { user } = await createUserWithEmailAndPassword(configuredAuth(), email, password);
    if (nickname?.trim()) await updateProfile(user, { displayName: nickname.trim() });
    await sendEmailVerification(user);
  },

  signIn({ username, password }) {
    return signInWithEmailAndPassword(configuredAuth(), username, password);
  },

  async confirmSignUp() {
    const user = await currentFirebaseUser();
    if (!user) {
      const error = new Error('이메일 인증 후 다시 로그인해 주세요.');
      error.code = 'auth/requires-sign-in';
      throw error;
    }
    await reload(user);
    if (!user.emailVerified) {
      const error = new Error('인증 메일의 링크를 완료한 뒤 다시 확인해 주세요.');
      error.code = 'auth/email-not-verified';
      throw error;
    }
    await getIdToken(user, true);
  },

  async resendVerification() {
    const user = await currentFirebaseUser();
    if (!user || user.emailVerified) {
      const error = new Error('인증 대기 중인 계정으로 먼저 로그인해 주세요.');
      error.code = 'auth/requires-sign-in';
      throw error;
    }
    return sendEmailVerification(user);
  },

  requestPasswordReset(email) {
    return sendPasswordResetEmail(configuredAuth(), email);
  },

  signOut() {
    return firebaseSignOut(configuredAuth());
  },
};
