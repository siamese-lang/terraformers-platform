import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { AuthSessionProvider, useAuthSession } from './AuthSessionContext';
import api from '../utils/api';
import { getCurrentUser, signOut } from './authClient';

jest.mock('./authClient', () => ({ getCurrentUser: jest.fn(), signOut: jest.fn() }));
jest.mock('../utils/api', () => ({ patch: jest.fn() }));

function SessionState() {
  const { status, user, error, logout } = useAuthSession();
  return (
    <>
      <span>{status}</span>
      <span>{user?.nickname}</span>
      <span>{error}</span>
      <button type="button" onClick={() => logout().catch(() => {})}>logout</button>
    </>
  );
}

const user = { userId: 'user-1', username: 'user', email: 'user@example.com', nickname: 'Terraformer' };

beforeEach(() => {
  jest.clearAllMocks();
  getCurrentUser.mockResolvedValue(user);
});

test('resolves initial checking state to an authenticated neutral user and syncs nickname', async () => {
  let resolveUser;
  getCurrentUser.mockReturnValue(new Promise((resolve) => { resolveUser = resolve; }));
  api.patch.mockResolvedValue({});
  render(<AuthSessionProvider><SessionState /></AuthSessionProvider>);
  expect(screen.getByText('checking')).toBeInTheDocument();
  await act(async () => resolveUser(user));
  expect(await screen.findByText('authenticated')).toBeInTheDocument();
  expect(screen.getByText('Terraformer')).toBeInTheDocument();
  expect(api.patch).toHaveBeenCalledWith('/api/users/me/display-name', { displayName: 'Terraformer' });
});

test('resolves an unauthenticated result to guest without an error', async () => {
  getCurrentUser.mockResolvedValue(null);
  render(<AuthSessionProvider><SessionState /></AuthSessionProvider>);
  expect(await screen.findByText('guest')).toBeInTheDocument();
  expect(screen.queryByText('로그인 상태를 확인하지 못했습니다.')).not.toBeInTheDocument();
});

test('stays authenticated when nickname synchronization fails', async () => {
  api.patch.mockRejectedValue(new Error('profile sync failed'));
  render(<AuthSessionProvider><SessionState /></AuthSessionProvider>);
  await waitFor(() => expect(api.patch).toHaveBeenCalled());
  expect(screen.getByText('authenticated')).toBeInTheDocument();
});

test('reports an unexpected provider failure and becomes guest', async () => {
  jest.spyOn(console, 'error').mockImplementation(() => {});
  getCurrentUser.mockRejectedValue(new Error('provider unavailable'));
  render(<AuthSessionProvider><SessionState /></AuthSessionProvider>);
  expect(await screen.findByText('guest')).toBeInTheDocument();
  expect(screen.getByText('로그인 상태를 확인하지 못했습니다.')).toBeInTheDocument();
  console.error.mockRestore();
});

test('logout success clears the authenticated session', async () => {
  signOut.mockResolvedValue(undefined);
  render(<AuthSessionProvider><SessionState /></AuthSessionProvider>);
  expect(await screen.findByText('authenticated')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'logout' }));
  expect(await screen.findByText('guest')).toBeInTheDocument();
  expect(signOut).toHaveBeenCalledTimes(1);
});

test('logout failure preserves the session and exposes an error', async () => {
  jest.spyOn(console, 'error').mockImplementation(() => {});
  signOut.mockRejectedValue(new Error('sign-out unavailable'));
  render(<AuthSessionProvider><SessionState /></AuthSessionProvider>);
  expect(await screen.findByText('authenticated')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'logout' }));
  expect(await screen.findByText('로그아웃하지 못했습니다. 다시 시도해 주세요.')).toBeInTheDocument();
  expect(screen.getByText('authenticated')).toBeInTheDocument();
  console.error.mockRestore();
});

test('auth-expired event clears the session', async () => {
  render(<AuthSessionProvider><SessionState /></AuthSessionProvider>);
  expect(await screen.findByText('authenticated')).toBeInTheDocument();
  act(() => window.dispatchEvent(new Event('terraformers:auth-expired')));
  expect(screen.getByText('guest')).toBeInTheDocument();
  expect(screen.getByText('로그인 세션이 만료되었습니다. 다시 로그인해 주세요.')).toBeInTheDocument();
});
