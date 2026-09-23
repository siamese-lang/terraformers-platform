import api, { isAuthRequiredRequest } from './api';
import { getToken } from '../auth/authClient';

jest.mock('../auth/authClient', () => ({ getToken: jest.fn() }));

beforeEach(() => jest.clearAllMocks());

test('attaches the default access bearer token', async () => {
  getToken.mockResolvedValue('access-token');
  const adapter = jest.fn().mockResolvedValue({ data: {}, status: 200, statusText: 'OK', headers: {}, config: {} });
  await api.get('/api/private', { adapter });
  expect(getToken).toHaveBeenCalledWith('access');
  expect(adapter.mock.calls[0][0].headers.Authorization).toBe('Bearer access-token');
});

test('uses the requested id token type', async () => {
  getToken.mockResolvedValue('id-token');
  const adapter = jest.fn().mockResolvedValue({ data: {}, status: 200, statusText: 'OK', headers: {}, config: {} });
  await api.get('/api/private', { adapter, tokenType: 'id' });
  expect(getToken).toHaveBeenCalledWith('id');
  expect(adapter.mock.calls[0][0].headers.Authorization).toBe('Bearer id-token');
});

test('retries a 401 only once with a refreshed token', async () => {
  getToken.mockResolvedValueOnce('initial').mockResolvedValueOnce('refreshed').mockResolvedValueOnce('refreshed');
  const adapter = jest.fn()
    .mockRejectedValueOnce({ response: { status: 401 }, config: { url: '/api/private', method: 'get', headers: {}, adapter } })
    .mockResolvedValueOnce({ data: { ok: true }, status: 200, statusText: 'OK', headers: {}, config: {} });
  await expect(api.get('/api/private', { adapter })).resolves.toMatchObject({ data: { ok: true } });
  expect(adapter).toHaveBeenCalledTimes(2);
  expect(getToken).toHaveBeenCalledTimes(3);
});

test('emits auth expiration when token refresh is missing after 401', async () => {
  const listener = jest.fn();
  window.addEventListener('terraformers:auth-expired', listener);
  getToken.mockResolvedValueOnce('initial').mockResolvedValueOnce(null);
  const adapter = jest.fn().mockRejectedValue({ response: { status: 401 }, config: { url: '/api/private', method: 'get', headers: {}, adapter } });
  await expect(api.get('/api/private', { adapter })).rejects.toBeTruthy();
  expect(adapter).toHaveBeenCalledTimes(1);
  expect(listener).toHaveBeenCalledTimes(1);
  window.removeEventListener('terraformers:auth-expired', listener);
});

test('emits auth expiration when retry also returns 401', async () => {
  const listener = jest.fn();
  window.addEventListener('terraformers:auth-expired', listener);
  getToken.mockResolvedValueOnce('initial').mockResolvedValueOnce('refreshed').mockResolvedValueOnce('refreshed');
  const adapter = jest.fn()
    .mockRejectedValueOnce({ response: { status: 401 }, config: { url: '/api/private', method: 'get', headers: {}, adapter } })
    .mockRejectedValueOnce({ response: { status: 401 }, config: { url: '/api/private', method: 'get', headers: {}, _retry: true, adapter } });
  await expect(api.get('/api/private', { adapter })).rejects.toBeTruthy();
  expect(adapter).toHaveBeenCalledTimes(2);
  expect(listener).toHaveBeenCalledTimes(1);
  window.removeEventListener('terraformers:auth-expired', listener);
});

test('classifies public and authenticated API requests', () => {
  expect(isAuthRequiredRequest({ method: 'get', url: '/api/projects/12' })).toBe(false);
  expect(isAuthRequiredRequest({ method: 'post', url: '/api/projects/12' })).toBe(true);
  expect(isAuthRequiredRequest({ method: 'get', url: '/api/projects' })).toBe(true);
  expect(isAuthRequiredRequest({ method: 'options', url: '/api/projects' })).toBe(false);
});

test('redirects an authenticated request without a token to login', async () => {
  getToken.mockResolvedValue(null);
  const assign = jest.fn();
  const originalLocation = window.location;
  delete window.location;
  window.location = { pathname: '/projects', assign };
  const adapter = jest.fn().mockResolvedValue({ data: {}, status: 200, statusText: 'OK', headers: {}, config: {} });
  await api.get('/api/projects', { adapter });
  expect(assign).toHaveBeenCalledWith('/login');
  window.location = originalLocation;
});
