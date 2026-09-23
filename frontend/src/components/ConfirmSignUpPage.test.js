import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ConfirmSignUpPage from './ConfirmSignUpPage';
import { confirmSignUp } from '../auth/authClient';

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'), useNavigate: () => mockNavigate,
}));
jest.mock('../auth/authClient', () => ({ confirmSignUp: jest.fn() }));

test('confirmation success navigates to login', async () => {
  confirmSignUp.mockResolvedValue({});
  render(<MemoryRouter><ConfirmSignUpPage /></MemoryRouter>);
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'person@example.com' } });
  fireEvent.change(screen.getByLabelText('Confirmation Code'), { target: { value: '123456' } });
  fireEvent.click(screen.getByRole('button', { name: 'Confirm' }));
  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/login'));
  expect(confirmSignUp).toHaveBeenCalledWith({ username: 'person@example.com', confirmationCode: '123456' });
});
