import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import EntryPage from './EntryPage';
import ConfirmSignUpPage from './ConfirmSignUpPage';
import {
  confirmSignUp, getApiToken, requestPasswordReset, resendVerification, signIn, signUp,
} from '../auth/authClient';

const mockNavigate = jest.fn();
const mockRefresh = jest.fn();
jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'), useNavigate: () => mockNavigate,
}));
jest.mock('../auth/AuthSessionContext', () => ({ useAuthSession: () => ({
  refresh: mockRefresh, user: { email: 'person@example.com' },
}) }));
jest.mock('../auth/authClient', () => ({
  isFirebaseAuth: true, isGcpBrowserPreview: false,
  authErrorMessage: (error) => error?.code === 'auth/invalid-credential'
    ? '이메일 또는 비밀번호를 확인해 주세요.' : '요청을 완료하지 못했습니다.',
  confirmSignUp: jest.fn(), getApiToken: jest.fn(), requestPasswordReset: jest.fn(),
  resendVerification: jest.fn(), signIn: jest.fn(), signUp: jest.fn(),
}));

beforeEach(() => jest.clearAllMocks());
const renderEntry = () => render(<MemoryRouter><EntryPage /></MemoryRouter>);

test('Firebase registration sends user to verification guidance, not the protected page', async () => {
  signUp.mockResolvedValue(undefined);
  renderEntry();
  fireEvent.click(screen.getByRole('button', { name: 'Create an Account' }));
  fireEvent.change(screen.getByLabelText('Nickname'), { target: { value: 'Builder' } });
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'person@example.com' } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'secret' } });
  fireEvent.click(screen.getByRole('button', { name: 'Submit' }));
  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/confirm-sign-up'));
  expect(signUp).toHaveBeenCalledWith({ email: 'person@example.com', password: 'secret', nickname: 'Builder' });
  expect(getApiToken).not.toHaveBeenCalled();
});

test('unverified Firebase login does not navigate to generate or expose a token', async () => {
  signIn.mockResolvedValue({});
  getApiToken.mockResolvedValue(null);
  renderEntry();
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'person@example.com' } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'secret' } });
  fireEvent.click(screen.getByRole('button', { name: 'Login' }));
  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/confirm-sign-up', { replace: true }));
  expect(mockNavigate).not.toHaveBeenCalledWith('/generate', { replace: true });
});

test('verification is link-based with resend and explicit refreshed confirmation', async () => {
  resendVerification.mockResolvedValue(undefined);
  confirmSignUp.mockResolvedValue(undefined);
  mockRefresh.mockResolvedValue(true);
  render(<MemoryRouter><ConfirmSignUpPage /></MemoryRouter>);
  expect(screen.queryByLabelText('Confirmation Code')).not.toBeInTheDocument();
  expect(screen.getByText(/person@example.com/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '인증 메일 다시 보내기' }));
  expect(await screen.findByRole('status')).toHaveTextContent('인증 메일을 다시 보냈습니다');
  fireEvent.click(screen.getByRole('button', { name: '인증 완료 확인' }));
  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/generate', { replace: true }));
  expect(confirmSignUp).toHaveBeenCalled();
});

test('password reset gives safe link guidance', async () => {
  requestPasswordReset.mockResolvedValue(undefined);
  renderEntry();
  fireEvent.click(screen.getByRole('button', { name: 'Forgot Password?' }));
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'person@example.com' } });
  fireEvent.click(screen.getByRole('button', { name: 'Send Reset Link' }));
  expect(await screen.findByRole('status')).toHaveTextContent('비밀번호 재설정 메일');
});
