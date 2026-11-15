import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { RegisterPage } from '../pages/auth/RegisterPage';
import { AuthProvider } from '../context/AuthContext';

// Mock API call
vi.mock('../services/api', () => ({
  api: {
    getCurrentUser: vi.fn().mockResolvedValue(null),
    logout: vi.fn().mockResolvedValue(undefined),
  },
}));

describe('RegisterPage', () => {
  it('renders all standard registration fields', () => {
    render(
      <AuthProvider>
        <BrowserRouter>
          <RegisterPage />
        </BrowserRouter>
      </AuthProvider>
    );

    expect(screen.getByText(/Create an account/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/Full name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/I am…/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Email$/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Password$/i)).toBeInTheDocument();
    expect(screen.getByText(/I accept the privacy policy in accordance with Moroccan Law 09-08/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Register$/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Login$/i })).toBeInTheDocument();
  });

  it('verifies Lab name and City do NOT appear for Student, but DO appear for HR and CEO', () => {
    render(
      <AuthProvider>
        <BrowserRouter>
          <RegisterPage />
        </BrowserRouter>
      </AuthProvider>
    );

    const select = screen.getByLabelText(/I am…/i);

    // Initial state: "Select your profile" (no profile selected) -> no Lab name or City
    expect(screen.queryByPlaceholderText(/Laboratory or research unit name/i)).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText(/City \(e\.g\. Rabat/i)).not.toBeInTheDocument();

    // 1. Select Student -> Lab name and City must NOT appear
    fireEvent.change(select, { target: { value: 'STUDENT' } });
    expect(screen.queryByPlaceholderText(/Laboratory or research unit name/i)).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText(/City \(e\.g\. Rabat/i)).not.toBeInTheDocument();

    // 2. Select HR -> Lab name and City MUST appear
    fireEvent.change(select, { target: { value: 'HR' } });
    expect(screen.getByPlaceholderText(/Laboratory or research unit name/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/City \(e\.g\. Rabat/i)).toBeInTheDocument();

    // 3. Select CEO -> Lab name and City MUST appear
    fireEvent.change(select, { target: { value: 'CEO' } });
    expect(screen.getByPlaceholderText(/Laboratory or research unit name/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/City \(e\.g\. Rabat/i)).toBeInTheDocument();

    // 4. Switch back to Student -> Lab name and City MUST disappear
    fireEvent.change(select, { target: { value: 'STUDENT' } });
    expect(screen.queryByPlaceholderText(/Laboratory or research unit name/i)).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText(/City \(e\.g\. Rabat/i)).not.toBeInTheDocument();
  });
});

