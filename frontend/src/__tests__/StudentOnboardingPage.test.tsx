import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { StudentOnboardingPage } from '../pages/student/StudentOnboardingPage';
import { AuthProvider } from '../context/AuthContext';

vi.mock('../services/api', () => ({
  api: {
    getCurrentUser: vi.fn().mockResolvedValue({
      id: 'mock-student-id',
      email: 'yassine.bennis@univ-casa.ma',
      role: 'STUDENT',
      fullName: 'Yassine Bennis',
    }),
    logout: vi.fn(),
  },
}));

describe('StudentOnboardingPage', () => {
  it('renders Step 2 CV dropzone and extracted fields faithfully matching mockup page 2', () => {
    render(
      <AuthProvider>
        <BrowserRouter>
          <StudentOnboardingPage />
        </BrowserRouter>
      </AuthProvider>
    );

    // Verify main headline from PDF page 2
    expect(screen.getByText(/Drop your CV and Fitwell will read it/i)).toBeInTheDocument();

    // Verify dropzone details
    expect(screen.getByText(/CV-Yassine.pdf/i)).toBeInTheDocument();
    expect(screen.getByText(/Uploaded - 214 KB/i)).toBeInTheDocument();

    // Verify extracted card
    expect(screen.getByText(/What Fitwell found/i)).toBeInTheDocument();
    expect(screen.getByText(/Master 1, Data science/i)).toBeInTheDocument();
    expect(screen.getByText(/Python/i)).toBeInTheDocument();
    expect(screen.getByText(/PyTorch/i)).toBeInTheDocument();
    expect(screen.getByText(/OpenCV/i)).toBeInTheDocument();
    expect(screen.getByText(/Arabic/i)).toBeInTheDocument();
    expect(screen.getByText(/Image segmentation/i)).toBeInTheDocument();

    // Verify fields read count (1 education + 5 skills + 3 languages + 2 projects = 11)
    expect(screen.getByText(/11 fields read/i)).toBeInTheDocument();

    // Verify "Check this" notice
    expect(screen.getByText(/Check this: one project has no result/i)).toBeInTheDocument();

    // Verify action buttons
    expect(screen.getByRole('button', { name: /Replace file/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Looks right, continue/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Skip for now/i })).toBeInTheDocument();
  });

  it('allows removing a skill pill and dynamically decrements the fields read counter', () => {
    render(
      <AuthProvider>
        <BrowserRouter>
          <StudentOnboardingPage />
        </BrowserRouter>
      </AuthProvider>
    );

    expect(screen.getByText(/11 fields read/i)).toBeInTheDocument();

    // Remove Python skill
    const removePythonBtn = screen.getByRole('button', { name: /Remove skill Python/i });
    fireEvent.click(removePythonBtn);

    // Python is removed
    expect(screen.queryByText(/^Python$/)).not.toBeInTheDocument();
    // Counter decrements to 10
    expect(screen.getByText(/10 fields read/i)).toBeInTheDocument();
  });

  it('supports dropzone keyboard navigation (Enter key)', () => {
    render(
      <AuthProvider>
        <BrowserRouter>
          <StudentOnboardingPage />
        </BrowserRouter>
      </AuthProvider>
    );

    const dropzone = screen.getByLabelText(/Upload your PDF CV/i);
    expect(dropzone).toHaveAttribute('tabIndex', '0');
    expect(dropzone).toHaveAttribute('role', 'button');

    fireEvent.keyDown(dropzone, { key: 'Enter', code: 'Enter' });
  });
});
