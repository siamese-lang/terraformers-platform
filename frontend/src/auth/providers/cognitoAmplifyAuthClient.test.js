import { Amplify } from 'aws-amplify';
import {
  confirmSignUp,
  fetchAuthSession,
  fetchUserAttributes,
  getCurrentUser,
  resetPassword,
  signIn,
  signOut,
  signUp,
} from 'aws-amplify/auth';
import { buildAmplifyConfig, logCognitoConfigSummary } from '../../awsConfig';
import { cognitoAmplifyAuthClient } from './cognitoAmplifyAuthClient';

jest.mock('aws-amplify', () => ({ Amplify: { configure: jest.fn() } }));
jest.mock('aws-amplify/auth', () => ({
  confirmSignUp: jest.fn(), fetchAuthSession: jest.fn(), fetchUserAttributes: jest.fn(),
  getCurrentUser: jest.fn(), resetPassword: jest.fn(), signIn: jest.fn(),
  signOut: jest.fn(), signUp: jest.fn(),
}));
jest.mock('../../awsConfig', () => ({
  buildAmplifyConfig: jest.fn(), logCognitoConfigSummary: jest.fn(),
}));

beforeEach(() => jest.clearAllMocks());

test('initializes the current provider when configuration is available', () => {
  const config = { Auth: { Cognito: { userPoolId: 'pool' } } };
  buildAmplifyConfig.mockReturnValue(config);
  cognitoAmplifyAuthClient.initialize();
  expect(logCognitoConfigSummary).toHaveBeenCalledWith(config, process.env.NODE_ENV);
  expect(Amplify.configure).toHaveBeenCalledWith(config);
});

test('returns the neutral current-user shape', async () => {
  getCurrentUser.mockResolvedValue({ userId: 'subject', username: 'person' });
  fetchUserAttributes.mockResolvedValue({ email: 'person@example.com', nickname: 'Builder' });
  await expect(cognitoAmplifyAuthClient.getCurrentUser()).resolves.toEqual({
    userId: 'subject', username: 'person', email: 'person@example.com', nickname: 'Builder',
  });
});

test.each(['UserUnAuthenticatedException', 'NotAuthorizedException'])(
  'normalizes %s as an unauthenticated result', async (name) => {
    getCurrentUser.mockRejectedValue({ name });
    fetchUserAttributes.mockResolvedValue({});
    await expect(cognitoAmplifyAuthClient.getCurrentUser()).resolves.toBeNull();
  },
);

test('propagates unexpected current-user failures', async () => {
  getCurrentUser.mockRejectedValue(new Error('unavailable'));
  fetchUserAttributes.mockResolvedValue({});
  await expect(cognitoAmplifyAuthClient.getCurrentUser()).rejects.toThrow('unavailable');
});

test('returns access, id, and missing tokens as strings or null', async () => {
  fetchAuthSession.mockResolvedValue({ tokens: {
    accessToken: { toString: () => 'access' }, idToken: { toString: () => 'id' },
  } });
  await expect(cognitoAmplifyAuthClient.getToken('access')).resolves.toBe('access');
  await expect(cognitoAmplifyAuthClient.getToken('id')).resolves.toBe('id');
  fetchAuthSession.mockResolvedValue({ tokens: {} });
  await expect(cognitoAmplifyAuthClient.getToken('access')).resolves.toBeNull();
});

test('delegates auth flows using the current provider argument shapes', async () => {
  await cognitoAmplifyAuthClient.signIn({ username: 'person', password: 'secret' });
  await cognitoAmplifyAuthClient.signUp({ email: 'person@example.com', password: 'secret', nickname: 'Builder' });
  await cognitoAmplifyAuthClient.confirmSignUp({ username: 'person@example.com', confirmationCode: '123456' });
  await cognitoAmplifyAuthClient.requestPasswordReset('person@example.com');
  await cognitoAmplifyAuthClient.signOut();
  expect(signIn).toHaveBeenCalledWith({ username: 'person', password: 'secret' });
  expect(signUp).toHaveBeenCalledWith({
    username: 'person@example.com', password: 'secret',
    options: { userAttributes: { email: 'person@example.com', nickname: 'Builder' } },
  });
  expect(confirmSignUp).toHaveBeenCalledWith({ username: 'person@example.com', confirmationCode: '123456' });
  expect(resetPassword).toHaveBeenCalledWith({ username: 'person@example.com' });
  expect(signOut).toHaveBeenCalledTimes(1);
});
