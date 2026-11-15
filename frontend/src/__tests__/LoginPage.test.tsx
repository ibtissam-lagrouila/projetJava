import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { LoginPage } from '../pages/auth/LoginPage';
import { AuthProvider } from '../context/AuthContext';

// Mock API call
vi.mock('../services/api', () => ({
  api: {
    getCurrentUser: vi.fn().mockResolvedValue(null),
    logout: vi.fn().mockResolvedValue(undefined),
  },
}));

describe('LoginPage', () => {
  it('renders sign in heading and input fields faithfully matching mockup page 1', () => {
    render(
      <AuthProvider>
        <BrowserRouter>
          <LoginPage />
        </BrowserRouter>
      </AuthProvider>
    );

    // Verify main headline from PDF page 1
    expect(screen.getByText(/Find the lab that will shape your future/i)).toBeInTheDocument();

    // Verify input fields
    expect(screen.getByPlaceholderText(/name@example.com/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/Your password/i)).toBeInTheDocument();

    // Verify Login button
    expect(screen.getByRole('button', { name: /Login/i })).toBeInTheDocument();

    // Verify Register link & password show toggle
    expect(screen.getByRole('button', { name: /Register/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Show password/i })).toBeInTheDocument();

    // Verify showcase cards
    expect(screen.getByText(/Vision models for medical imaging/i)).toBeInTheDocument();
    expect(screen.getByText(/96% match/i)).toBeInTheDocument();
    expect(screen.getByText(/NLP for Arabic dialects/i)).toBeInTheDocument();
    expect(screen.getByText(/Gene expression analysis/i)).toBeInTheDocument();
  });
});

