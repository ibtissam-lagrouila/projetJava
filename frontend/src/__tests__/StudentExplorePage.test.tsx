import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { StudentExplorePage } from '../pages/student/StudentExplorePage';
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

describe('StudentExplorePage', () => {
  it('renders free-query search, "understood as" pills, and scored offer cards', async () => {
    const mockOfferResponse = {
      offers: [
        {
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
          tasks: ['Build annotated dataset', 'Train models'],
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
        },
      ],
      totalCount: 1,
      parsedQuery: {
        rawQuery: 'I want to work on medical images in Rabat for about four months',
        city: 'Rabat',
        minDurationMonths: 3,
        maxDurationMonths: 5,
        durationLabel: '3 to 5 months',
        domains: ['Medical imaging'],
        skills: ['Medical imaging'],
        compensation: 'Any funding',
        pills: [
          { label: 'Medical imaging', type: 'domain' },
          { label: 'Rabat', type: 'city' },
          { label: '3 to 5 months', type: 'duration' },
          { label: 'Any funding', type: 'compensation' },
        ],
      },
    };

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockOfferResponse,
    }) as any;

    render(
      <AuthProvider>
        <BrowserRouter>
          <StudentExplorePage />
        </BrowserRouter>
      </AuthProvider>
    );

    // Verify page header
    expect(screen.getByText(/Tell Fitwell what you want/i)).toBeInTheDocument();

    // Verify filter dropdowns
    expect(screen.getByText(/City:/i)).toBeInTheDocument();
    expect(screen.getByText(/Domain:/i)).toBeInTheDocument();
    expect(screen.getByText(/Duration:/i)).toBeInTheDocument();
    expect(screen.getByText(/Min Fit:/i)).toBeInTheDocument();

    // Verify parsed query pills
    await waitFor(() => {
      expect(screen.getByText(/understood as:/i)).toBeInTheDocument();
      expect(screen.getAllByText('Medical imaging').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('Rabat').length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText('3 to 5 months')).toBeInTheDocument();
      expect(screen.getByText('Any funding')).toBeInTheDocument();
    });

    // Verify scored offer card details
    expect(screen.getByText(/Vision models for medical imaging/i)).toBeInTheDocument();
    expect(screen.getByText(/94%/i)).toBeInTheDocument();
    expect(screen.getByText(/Excellent match/i)).toBeInTheDocument();
    expect(screen.getByText(/closes in 17 days/i)).toBeInTheDocument();
    expect(screen.getByText(/Why Fitwell picked it:/i)).toBeInTheDocument();
    expect(screen.getByText(/your PyTorch segmentation project covers the main task/i)).toBeInTheDocument();
    expect(screen.getByText(/DICOM \(Optional\)/i)).toBeInTheDocument();
  });
});
