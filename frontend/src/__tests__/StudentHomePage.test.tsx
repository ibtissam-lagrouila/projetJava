import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { StudentHomePage } from '../pages/student/StudentHomePage';
import { AuthProvider } from '../context/AuthContext';
import { api } from '../services/api';

vi.mock('../services/api', () => ({
  api: {
    getCurrentUser: vi.fn(),
    logout: vi.fn(),
  },
}));

describe('StudentHomePage', () => {
  beforeEach(() => {
    vi.mocked(api.getCurrentUser).mockResolvedValue({
      id: 'mock-student-id',
      email: 'yassine.bennis@univ-casa.ma',
      role: 'STUDENT',
      fullName: 'Yassine Bennis',
      level: 'Master 1, data science',
      emailVerified: true,
    });
  });

  it('renders greeting, search bar, best match today card with score ring, and "This week" calendar strip with active items', async () => {
    const mockHomeData = {
      studentName: 'Yassine Bennis',
      studentUniversity: 'Université Hassan II de Casablanca',
      studentLevel: 'Master 1, data science',
      applicationsCount: 3,
      inReviewCount: 2,
      interviewsCount: 1,
      bestMatchToday: {
        id: 'offer-vision-1',
        labId: 'lab-vision',
        labName: 'Vision and Imaging Lab',
        labUniversity: 'Université Mohammed V',
        labCity: 'Rabat',
        title: 'Vision models for medical imaging',
        domain: 'Computer Vision',
        durationMonths: 6,
        startDate: '2026-02-01',
        location: 'Rabat',
        compensationType: 'Funded',
        compensationAmount: 'Gratification mensuelle',
        description: 'Deep learning research on medical images.',
        tasks: ['Model training'],
        places: 2,
        status: 'PUBLISHED',
        origin: 'LAB_POSTED',
        closesAt: '2026-03-01',
        daysRemaining: 17,
        skills: [],
        score: {
          overallScore: 96,
          skillsScore: 96,
          interestScore: 90,
          datesScore: 100,
          matchLabel: 'Excellent match',
          matchedSkills: ['Python', 'PyTorch', 'OpenCV'],
          missingRequiredSkills: [],
          missingOptionalSkills: ['DICOM'],
          gapToClose: [],
          whyPicked: 'your PyTorch segmentation project covers the main task, and you already use OpenCV.',
        },
      },
      recommendedOffers: [
        {
          id: 'offer-nlp',
          title: 'NLP for Arabic dialects',
          labName: 'NLP Group',
          location: 'Casablanca',
          durationMonths: 3,
          score: { overallScore: 88 },
        },
        {
          id: 'offer-robotics',
          title: 'Drone navigation for inspection',
          labName: 'Robotics Lab',
          location: 'Rabat',
          durationMonths: 6,
          score: { overallScore: 81 },
        },
        {
          id: 'offer-genomics',
          title: 'Gene expression analysis',
          labName: 'Genomics Unit',
          location: 'Fès',
          durationMonths: 4,
          score: { overallScore: 74 },
        },
      ],
      weeklyActions: [
        {
          id: 'act-1',
          title: 'Confirm interview slot, Genomics Unit',
          deadline: 'Wed 18',
          type: 'INTERVIEW',
          hasAlertDot: false,
          link: '/student/applications',
        },
        {
          id: 'act-2',
          title: 'Finish cover letter, Vision models',
          deadline: 'Mon 30',
          type: 'DRAFT',
          hasAlertDot: true,
          link: '/student/applications',
        },
        {
          id: 'act-3',
          title: 'Upload your transcript',
          deadline: 'anytime',
          type: 'REMINDER',
          hasAlertDot: true,
          link: '/student/cv-studio',
        },
      ],
    };

    globalThis.fetch = vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/student/onboarding/state')) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ onboardingCompleted: true, hasDraft: false }),
        } as Response);
      }
      if (url.includes('/api/student/home')) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve(mockHomeData),
        } as Response);
      }
      return Promise.reject(new Error('not found'));
    });

    render(
      <BrowserRouter>
        <AuthProvider>
          <StudentHomePage />
        </AuthProvider>
      </BrowserRouter>
    );

    // Check greeting with first name
    await waitFor(() => {
      expect(screen.getByText(/Good morning, Yassine/i)).toBeInTheDocument();
    });

    // Check search placeholder and Ctrl K shortcut pill
    expect(screen.getByPlaceholderText(/Search offers or ask Fitwell/i)).toBeInTheDocument();
    expect(screen.getByText('Ctrl K')).toBeInTheDocument();

    // Check "Best match today" tag and offer title
    expect(screen.getByText('Best match today')).toBeInTheDocument();
    expect(screen.getByText('Vision models for medical imaging')).toBeInTheDocument();
    expect(screen.getByText(/closes in 17 days/i)).toBeInTheDocument();

    // Check "Why Fitwell picked it" callout box
    expect(screen.getByText(/Why Fitwell picked it:/i)).toBeInTheDocument();
    expect(screen.getByText(/your PyTorch segmentation project covers the main task, and you already use OpenCV\./i)).toBeInTheDocument();

    // Check action buttons
    expect(screen.getByRole('button', { name: /View offer/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Start application/i })).toBeInTheDocument();

    // Check score number in circle & breakdown labels
    expect(screen.getByText('96')).toBeInTheDocument();
    expect(screen.getByText('skills')).toBeInTheDocument();
    expect(screen.getByText('interest')).toBeInTheDocument();
    expect(screen.getByText('dates')).toBeInTheDocument();

    // Check "This week" card with calendar days
    expect(screen.getByText('This week')).toBeInTheDocument();
    expect(screen.getByText('Confirm interview slot, Genomics Unit')).toBeInTheDocument();
    expect(screen.getByText('Finish cover letter, Vision models')).toBeInTheDocument();
    expect(screen.getByText('Upload your transcript')).toBeInTheDocument();

    // Check "New for you" section and cards
    expect(screen.getByText('New for you')).toBeInTheDocument();
    expect(screen.getByText('NLP for Arabic dialects')).toBeInTheDocument();
    expect(screen.getByText('88%')).toBeInTheDocument();
    expect(screen.getByText('Drone navigation for inspection')).toBeInTheDocument();
    expect(screen.getByText('81%')).toBeInTheDocument();
    expect(screen.getByText('Gene expression analysis')).toBeInTheDocument();
    expect(screen.getByText('74%')).toBeInTheDocument();
  });

  it('renders clean empty state for "This week" when user has no pending interviews', async () => {
    const mockEmptyWeeklyData = {
      studentName: 'Ibtissam Lagrouila',
      studentUniversity: 'ENSA Marrakech',
      studentLevel: 'Engineering cycle, Computer Science',
      applicationsCount: 0,
      inReviewCount: 0,
      interviewsCount: 0,
      bestMatchToday: {
        id: 'offer-web-1',
        title: 'Web Platform & Architecture for Medical Data',
        labName: 'Web & Digital Health Lab',
        location: 'Casablanca',
        durationMonths: 4,
        score: { overallScore: 76 },
      },
      recommendedOffers: [],
      weeklyActions: [], // EMPTY
    };

    globalThis.fetch = vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/student/onboarding/state')) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ onboardingCompleted: true, hasDraft: false }),
        } as Response);
      }
      if (url.includes('/api/student/home')) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve(mockEmptyWeeklyData),
        } as Response);
      }
      return Promise.reject(new Error('not found'));
    });

    render(
      <BrowserRouter>
        <AuthProvider>
          <StudentHomePage />
        </AuthProvider>
      </BrowserRouter>
    );

    // Check greeting with user's real first name
    await waitFor(() => {
      expect(screen.getByText(/Good morning, Ibtissam/i)).toBeInTheDocument();
    });

    // Check empty state in "This week"
    expect(screen.getByText('No pending interviews this week')).toBeInTheDocument();
    expect(screen.getByText(/When research laboratories invite you to an interview/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Explore offers/i })).toBeInTheDocument();
  });
});
