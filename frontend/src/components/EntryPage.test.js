import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import EntryPage from './EntryPage';
import { getApiToken, requestPasswordReset, signIn, signUp } from '../auth/authClient';

const mockNavigate = jest.fn();
const mockRefresh = jest.fn();
let mockSessionError = '';
jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'), useNavigate: () => mockNavigate,
}));
jest.mock('../auth/AuthSessionContext', () => ({
  useAuthSession: () => ({ refresh: mockRefresh, error: mockSessionError }),
}));
jest.mock('../auth/authClient', () => ({
  getApiToken: jest.fn(), requestPasswordReset: jest.fn(), signIn: jest.fn(), signUp: jest.fn(),
  authErrorMessage: (error) => error.message,
  isFirebaseAuth: false, isGcpBrowserPreview: false,
}));

beforeEach(() => {
  jest.clearAllMocks();
  mockSessionError = '';
});
const renderPage = () => render(<MemoryRouter><EntryPage /></MemoryRouter>);

test('login success refreshes the session and navigates to generate', async () => {
  signIn.mockResolvedValue({});
  getApiToken.mockResolvedValue('token');
  mockRefresh.mockResolvedValue(true);
  renderPage();
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'person@example.com' } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'secret' } });
  fireEvent.click(screen.getByRole('button', { name: 'Login' }));
  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/generate', { replace: true }));
  expect(signIn).toHaveBeenCalledWith({ username: 'person@example.com', password: 'secret' });
  expect(mockRefresh).toHaveBeenCalledTimes(1);
});

test('sign-up success navigates to confirmation', async () => {
  signUp.mockResolvedValue({});
  renderPage();
  fireEvent.click(screen.getByRole('button', { name: 'Create an Account' }));
  fireEvent.change(screen.getByLabelText('Nickname'), { target: { value: 'Builder' } });
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'person@example.com' } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'secret' } });
  fireEvent.click(screen.getByRole('button', { name: 'Submit' }));
  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/confirm-sign-up'));
  expect(signUp).toHaveBeenCalledWith({ email: 'person@example.com', password: 'secret', nickname: 'Builder' });
});

test('password reset uses the neutral client and returns to login', async () => {
  requestPasswordReset.mockResolvedValue({});
  renderPage();
  fireEvent.click(screen.getByRole('button', { name: 'Forgot Password?' }));
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'person@example.com' } });
  fireEvent.click(screen.getByRole('button', { name: 'Send Reset Link' }));
  await waitFor(() => expect(requestPasswordReset).toHaveBeenCalledWith('person@example.com'));
  expect(await screen.findByRole('heading', { name: 'Welcome Back' })).toBeInTheDocument();
});

test('shows an actionable fail-closed authentication configuration state', () => {
  mockSessionError = 'Firebase 로그인 설정을 확인해 주세요: REACT_APP_FIREBASE_PROJECT_ID.';
  renderPage();
  expect(screen.getByRole('alert')).toHaveTextContent('REACT_APP_FIREBASE_PROJECT_ID');
});
