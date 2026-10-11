jest.mock('./providers/firebaseAuthClient', () => ({ firebaseAuthClient: {
  initialize: jest.fn(), getCurrentUser: jest.fn(), getToken: jest.fn(),
} }));
jest.mock('./providers/cognitoAmplifyAuthClient', () => ({ cognitoAmplifyAuthClient: {
  initialize: jest.fn(), getCurrentUser: jest.fn(), getToken: jest.fn(),
} }));
jest.mock('./providers/localGcpBrowserAuthClient', () => ({ localGcpBrowserAuthClient: {
  initialize: jest.fn(), getCurrentUser: jest.fn(), getToken: jest.fn(),
} }));

const { firebaseAuthClient } = require('./providers/firebaseAuthClient');
const { cognitoAmplifyAuthClient } = require('./providers/cognitoAmplifyAuthClient');
const { localGcpBrowserAuthClient } = require('./providers/localGcpBrowserAuthClient');
const previous = {
  NODE_ENV: process.env.NODE_ENV,
  REACT_APP_AUTH_PROVIDER: process.env.REACT_APP_AUTH_PROVIDER,
  REACT_APP_GCP_BROWSER_PREVIEW: process.env.REACT_APP_GCP_BROWSER_PREVIEW,
};

function client(provider, preview = 'false', environment = 'test') {
  process.env.REACT_APP_AUTH_PROVIDER = provider;
  process.env.REACT_APP_GCP_BROWSER_PREVIEW = preview;
  process.env.NODE_ENV = environment;
  let selected;
  jest.isolateModules(() => { selected = require('./authClient'); });
  return selected;
}

beforeEach(() => jest.clearAllMocks());
afterAll(() => {
  for (const [key, value] of Object.entries(previous)) {
    if (value === undefined) delete process.env[key];
    else process.env[key] = value;
  }
});

test('explicit Firebase selection uses only its ID token for API calls, even with preview flag', async () => {
  const selected = client('firebase', 'true', 'development');
  firebaseAuthClient.getToken.mockResolvedValue('id-token');
  selected.initializeAuth();
  expect(selected.isFirebaseAuth).toBe(true);
  expect(selected.isGcpBrowserPreview).toBe(false);
  expect(firebaseAuthClient.initialize).toHaveBeenCalledTimes(1);
  expect(cognitoAmplifyAuthClient.initialize).not.toHaveBeenCalled();
  expect(localGcpBrowserAuthClient.initialize).not.toHaveBeenCalled();
  await expect(selected.getApiToken(true)).resolves.toBe('id-token');
  expect(firebaseAuthClient.getToken).toHaveBeenCalledWith('id', true);
});

test('selected Firebase ID Token reaches a protected API Authorization header', async () => {
  client('firebase');
  firebaseAuthClient.getToken.mockResolvedValue('firebase-id-token');
  let api;
  jest.isolateModules(() => { api = require('../utils/api').default; });
  const adapter = jest.fn().mockResolvedValue({
    data: {}, status: 200, statusText: 'OK', headers: {}, config: {},
  });
  await api.get('/api/projects', { adapter });
  expect(firebaseAuthClient.getToken).toHaveBeenCalledWith('id', false);
  expect(adapter.mock.calls[0][0].headers.Authorization).toBe('Bearer firebase-id-token');
});

test('Cognito remains the default and development preview never enters a production build', async () => {
  const historical = client('cognito');
  historical.initializeAuth();
  await historical.getApiToken();
  expect(cognitoAmplifyAuthClient.getToken).toHaveBeenCalledWith('access', false);
  expect(client('cognito', 'true', 'development').isGcpBrowserPreview).toBe(true);
  expect(client('cognito', 'true', 'production').isGcpBrowserPreview).toBe(false);
});

test('unsupported provider fails closed instead of silently falling back', async () => {
  const selected = client('unexpected');
  selected.initializeAuth();
  expect(() => selected.getCurrentUser()).toThrow('REACT_APP_AUTH_PROVIDER');
  expect(cognitoAmplifyAuthClient.initialize).not.toHaveBeenCalled();
});
