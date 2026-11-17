import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { StudentOfferDetailPage } from '../pages/student/StudentOfferDetailPage';
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

describe('StudentOfferDetailPage', () => {
  it('renders the 3 score breakdown bars, Gap to close, and Law 09-08 application box matching mockup page 4', async () => {
    const mockOffer = {
      id: 'offer-1',
      labId: 'lab-1',
      labName: 'Vision and Imaging Lab',
      labUniversity: 'Université Mohammed V',
      labCity: 'Rabat',
      title: 'Vision models for medical imaging',
      domain: 'Medical imaging, Computer Vision',
      durationMonths: 6,
      startDate: '2026-02-01',
      location: 'Rabat',
      compensationType: 'Funded',
      compensationAmount: 'Gratification mensuelle',
      description: 'The team trains segmentation models on annotated microscopy images.',
      tasks: ['Build annotated dataset with team', 'Train and compare three segmentation models'],
      places: 2,
      status: 'PUBLISHED',
      origin: 'LAB_POSTED',
      closesAt: '2026-03-01',
      daysRemaining: 17,
      skills: [
        { id: 's1', name: 'Python', normalizedName: 'python', isRequired: true },
        { id: 's2', name: 'PyTorch', normalizedName: 'pytorch', isRequired: true },
        { id: 's3', name: 'OpenCV', normalizedName: 'opencv', isRequired: true },
        { id: 's4', name: 'DICOM', normalizedName: 'dicom', isRequired: false },
      ],
      score: {
        overallScore: 94,
        matchLabel: 'Excellent match',
        skillsScore: 90,
        interestScore: 95,
        datesScore: 100,
        whyPicked: 'your PyTorch segmentation project covers the main task, and you already use OpenCV.',
        matchedSkills: ['Python', 'PyTorch', 'OpenCV'],
        gapToClose: [
          {
            name: 'DICOM',
            isRequired: false,
            advice: 'Familiarize yourself with DICOM viewer libraries before interview.',
          },
        ],
      },
    };

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockOffer,
    }) as any;

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/student/offers/offer-1']}>
          <Routes>
            <Route path="/student/offers/:id" element={<StudentOfferDetailPage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    // Wait for offer data to load
    await waitFor(() => {
      expect(screen.getByText(/Vision models for medical imaging/i)).toBeInTheDocument();
    });

    // Verify 3 score breakdown bars
    expect(screen.getByText(/Skills match/i)).toBeInTheDocument();
    expect(screen.getByText('90%')).toBeInTheDocument();

    expect(screen.getByText(/Interest & Domain/i)).toBeInTheDocument();
    expect(screen.getByText('95%')).toBeInTheDocument();

    expect(screen.getByText(/Dates & Availability/i)).toBeInTheDocument();
    expect(screen.getByText('100%')).toBeInTheDocument();

    // Verify Gap to close
    expect(screen.getByText(/Gap to close before applying/i)).toBeInTheDocument();
    expect(screen.getByText(/Familiarize yourself with DICOM viewer libraries/i)).toBeInTheDocument();

    // Verify Why Fitwell picked it banner
    expect(screen.getByText(/Why Fitwell picked it:/i)).toBeInTheDocument();

    // Verify Law 09-08 notice
    expect(screen.getByText(/Moroccan Law 09-08 Safeguard:/i)).toBeInTheDocument();

    // Verify Apply with Fitwell button
    expect(screen.getByRole('button', { name: /Apply with Fitwell/i })).toBeInTheDocument();
  });
});
