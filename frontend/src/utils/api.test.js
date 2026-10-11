import api, { isAuthRequiredRequest } from './api';
import { getApiToken, needsEmailVerification } from '../auth/authClient';

jest.mock('../auth/authClient', () => ({
  getApiToken: jest.fn(), needsEmailVerification: jest.fn(() => false),
}));

beforeEach(() => {
  jest.clearAllMocks();
  needsEmailVerification.mockReturnValue(false);
});

test('attaches the provider-selected API bearer token', async () => {
  getApiToken.mockResolvedValue('id-token');
  const adapter = jest.fn().mockResolvedValue({ data: {}, status: 200, statusText: 'OK', headers: {}, config: {} });
  await api.get('/api/private', { adapter });
  expect(getApiToken).toHaveBeenCalledWith(false);
  expect(adapter.mock.calls[0][0].headers.Authorization).toBe('Bearer id-token');
});

test('request tokenType cannot override the selected provider token', async () => {
  getApiToken.mockResolvedValue('provider-token');
  const adapter = jest.fn().mockResolvedValue({ data: {}, status: 200, statusText: 'OK', headers: {}, config: {} });
  await api.get('/api/private', { adapter, tokenType: 'access' });
  expect(getApiToken).toHaveBeenCalledWith(false);
  expect(adapter.mock.calls[0][0].headers.Authorization).toBe('Bearer provider-token');
});

test('retries a 401 only once with a refreshed token', async () => {
  getApiToken.mockResolvedValueOnce('initial').mockResolvedValueOnce('refreshed');
  const adapter = jest.fn();
  adapter
    .mockRejectedValueOnce({ response: { status: 401 }, config: { url: '/api/private', method: 'get', headers: {}, adapter } })
    .mockResolvedValueOnce({ data: { ok: true }, status: 200, statusText: 'OK', headers: {}, config: {} });
  await expect(api.get('/api/private', { adapter })).resolves.toMatchObject({ data: { ok: true } });
  expect(adapter).toHaveBeenCalledTimes(2);
  expect(getApiToken).toHaveBeenNthCalledWith(1, false);
  expect(getApiToken).toHaveBeenNthCalledWith(2, true);
  expect(getApiToken).toHaveBeenCalledTimes(2);
  expect(adapter.mock.calls[1][0].headers.Authorization).toBe('Bearer refreshed');
});

test('emits auth expiration when token refresh is missing after 401', async () => {
  const listener = jest.fn();
  window.addEventListener('terraformers:auth-expired', listener);
  getApiToken.mockResolvedValueOnce('initial').mockResolvedValueOnce(null);
  const adapter = jest.fn();
  adapter.mockRejectedValue({ response: { status: 401 }, config: { url: '/api/private', method: 'get', headers: {}, adapter } });
  await expect(api.get('/api/private', { adapter })).rejects.toBeTruthy();
  expect(adapter).toHaveBeenCalledTimes(1);
  expect(listener).toHaveBeenCalledTimes(1);
  window.removeEventListener('terraformers:auth-expired', listener);
});

test('emits auth expiration when retry also returns 401', async () => {
  const listener = jest.fn();
  window.addEventListener('terraformers:auth-expired', listener);
  getApiToken.mockResolvedValueOnce('initial').mockResolvedValueOnce('refreshed');
  const adapter = jest.fn();
  adapter
    .mockRejectedValueOnce({ response: { status: 401 }, config: { url: '/api/private', method: 'get', headers: {}, adapter } })
    .mockRejectedValueOnce({ response: { status: 401 }, config: { url: '/api/private', method: 'get', headers: {}, _retry: true, adapter } });
  await expect(api.get('/api/private', { adapter })).rejects.toBeTruthy();
  expect(adapter).toHaveBeenCalledTimes(2);
  expect(listener).toHaveBeenCalledTimes(1);
  expect(getApiToken).toHaveBeenCalledTimes(2);
  window.removeEventListener('terraformers:auth-expired', listener);
});

test('403 is not retried or treated as session expiration', async () => {
  const listener = jest.fn();
  window.addEventListener('terraformers:auth-expired', listener);
  getApiToken.mockResolvedValue('id-token');
  const adapter = jest.fn().mockRejectedValue({
    response: { status: 403 }, config: { url: '/api/private', method: 'get', headers: {} },
  });
  await expect(api.get('/api/private', { adapter })).rejects.toBeTruthy();
  expect(adapter).toHaveBeenCalledTimes(1);
  expect(getApiToken).toHaveBeenCalledTimes(1);
  expect(listener).not.toHaveBeenCalled();
  window.removeEventListener('terraformers:auth-expired', listener);
});

test('classifies public and authenticated API requests', () => {
  expect(isAuthRequiredRequest({ method: 'get', url: '/api/projects/12' })).toBe(false);
  expect(isAuthRequiredRequest({ method: 'post', url: '/api/projects/12' })).toBe(true);
  expect(isAuthRequiredRequest({ method: 'get', url: '/api/projects' })).toBe(true);
  expect(isAuthRequiredRequest({ method: 'options', url: '/api/projects' })).toBe(false);
});

test('redirects an authenticated request without a token to login', async () => {
  getApiToken.mockResolvedValue(null);
  const assign = jest.fn();
  const originalLocation = window.location;
  delete window.location;
  window.location = { pathname: '/projects', assign };
  const adapter = jest.fn().mockResolvedValue({ data: {}, status: 200, statusText: 'OK', headers: {}, config: {} });
  await api.get('/api/projects', { adapter });
  expect(assign).toHaveBeenCalledWith('/login');
  window.location = originalLocation;
});

test('missing token for an unverified Firebase user routes to link verification', async () => {
  getApiToken.mockResolvedValue(null);
  needsEmailVerification.mockReturnValue(true);
  const assign = jest.fn();
  const originalLocation = window.location;
  delete window.location;
  window.location = { pathname: '/projects', assign };
  const adapter = jest.fn().mockResolvedValue({ data: {}, status: 200, statusText: 'OK', headers: {}, config: {} });
  await api.get('/api/projects', { adapter });
  expect(assign).toHaveBeenCalledWith('/confirm-sign-up');
  window.location = originalLocation;
});
