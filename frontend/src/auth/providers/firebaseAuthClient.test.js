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
  signOut,
  updateProfile,
} from 'firebase/auth';
import { firebaseAuthClient } from './firebaseAuthClient';

jest.mock('firebase/app', () => ({ initializeApp: jest.fn(() => ({ name: 'test-app' })) }));
jest.mock('firebase/auth', () => ({
  createUserWithEmailAndPassword: jest.fn(), getAuth: jest.fn(), getIdToken: jest.fn(),
  onAuthStateChanged: jest.fn(), reload: jest.fn(), sendEmailVerification: jest.fn(),
  sendPasswordResetEmail: jest.fn(), signInWithEmailAndPassword: jest.fn(),
  signOut: jest.fn(), updateProfile: jest.fn(),
}));

const configured = {
  REACT_APP_FIREBASE_API_KEY: 'AIzaTestPublicBrowserKey123456789',
  REACT_APP_FIREBASE_AUTH_DOMAIN: 'terraformers-test.firebaseapp.com',
  REACT_APP_FIREBASE_PROJECT_ID: 'terraformers-test',
  REACT_APP_FIREBASE_APP_ID: '1:123456:web:abcdef',
};
const original = Object.fromEntries(Object.keys(configured).map((key) => [key, process.env[key]]));
const user = () => ({
  uid: 'firebase-uid', email: 'person@example.com', displayName: 'Builder', emailVerified: false,
});
let client;

beforeEach(() => {
  jest.clearAllMocks();
  Object.assign(process.env, configured);
  client = { currentUser: user(), authStateReady: jest.fn().mockResolvedValue(undefined) };
  getAuth.mockReturnValue(client);
  reload.mockResolvedValue(undefined);
  getIdToken.mockResolvedValue('firebase-id-token');
  signOut.mockResolvedValue(undefined);
  firebaseAuthClient.initialize();
});

afterAll(() => {
  for (const [key, value] of Object.entries(original)) {
    if (value === undefined) delete process.env[key];
    else process.env[key] = value;
  }
});

test('initializes only from explicit public web configuration and fails closed when missing', async () => {
  expect(initializeApp).toHaveBeenCalledWith({
    apiKey: configured.REACT_APP_FIREBASE_API_KEY,
    authDomain: configured.REACT_APP_FIREBASE_AUTH_DOMAIN,
    projectId: configured.REACT_APP_FIREBASE_PROJECT_ID,
    appId: configured.REACT_APP_FIREBASE_APP_ID,
  }, 'terraformers-firebase');
  delete process.env.REACT_APP_FIREBASE_PROJECT_ID;
  initializeApp.mockClear();
  firebaseAuthClient.initialize();
  await expect(firebaseAuthClient.getCurrentUser()).rejects.toMatchObject({ code: 'auth/configuration' });
  expect(initializeApp).not.toHaveBeenCalled();
  Object.assign(process.env, configured);
  process.env.REACT_APP_FIREBASE_API_KEY = 'malformed';
  firebaseAuthClient.initialize();
  await expect(firebaseAuthClient.getCurrentUser()).rejects.toMatchObject({ code: 'auth/configuration' });
  expect(initializeApp).not.toHaveBeenCalled();
});

test('registration updates nickname and sends a verification link, while unverified user gets no ID token', async () => {
  createUserWithEmailAndPassword.mockResolvedValue({ user: client.currentUser });
  await firebaseAuthClient.signUp({ email: 'person@example.com', password: 'secret', nickname: 'Builder' });
  expect(createUserWithEmailAndPassword).toHaveBeenCalledWith(client, 'person@example.com', 'secret');
  expect(updateProfile).toHaveBeenCalledWith(client.currentUser, { displayName: 'Builder' });
  expect(sendEmailVerification).toHaveBeenCalledWith(client.currentUser);
  await expect(firebaseAuthClient.getCurrentUser()).resolves.toMatchObject({
    userId: 'firebase-uid', emailVerified: false,
  });
  await expect(firebaseAuthClient.getToken('id')).resolves.toBeNull();
  expect(getIdToken).not.toHaveBeenCalled();
  await firebaseAuthClient.resendVerification();
  expect(sendEmailVerification).toHaveBeenCalledTimes(2);
});

test('verified restored account obtains Firebase ID Token and refreshed token after confirmation', async () => {
  const listener = jest.fn();
  const unsubscribe = jest.fn();
  onAuthStateChanged.mockReturnValue(unsubscribe);
  expect(firebaseAuthClient.subscribe(listener)).toBe(unsubscribe);
  expect(onAuthStateChanged).toHaveBeenCalledWith(client, listener);
  reload.mockImplementation(async (account) => { account.emailVerified = true; });
  await firebaseAuthClient.confirmSignUp();
  expect(getIdToken).toHaveBeenCalledWith(client.currentUser, true);
  await expect(firebaseAuthClient.getCurrentUser()).resolves.toMatchObject({
    userId: 'firebase-uid', nickname: 'Builder', emailVerified: true,
  });
  await expect(firebaseAuthClient.getToken('id', false)).resolves.toBe('firebase-id-token');
  await expect(firebaseAuthClient.getToken('access')).resolves.toBeNull();
  expect(client.authStateReady).toHaveBeenCalled();
});

test('unverified confirmation remains pending; login, reset and logout delegate to the SDK', async () => {
  await expect(firebaseAuthClient.confirmSignUp()).rejects.toMatchObject({ code: 'auth/email-not-verified' });
  await firebaseAuthClient.signIn({ username: 'person@example.com', password: 'secret' });
  await firebaseAuthClient.requestPasswordReset('person@example.com');
  await firebaseAuthClient.signOut();
  expect(signInWithEmailAndPassword).toHaveBeenCalledWith(client, 'person@example.com', 'secret');
  expect(sendPasswordResetEmail).toHaveBeenCalledWith(client, 'person@example.com');
  expect(signOut).toHaveBeenCalledWith(client);
});

test('invalid restored token clears the SDK session rather than exposing a user', async () => {
  reload.mockRejectedValue({ code: 'auth/user-token-expired' });
  await expect(firebaseAuthClient.getCurrentUser()).resolves.toBeNull();
  expect(signOut).toHaveBeenCalledWith(client);
});
