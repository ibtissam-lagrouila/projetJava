import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LabCandidatesPage } from '../pages/lab/LabCandidatesPage';
import { AuthProvider } from '../context/AuthContext';
import { api } from '../services/api';

vi.mock('../services/api', () => ({
  api: {
    getCurrentUser: vi.fn().mockResolvedValue({
      id: 'mock-recruiter-id',
      email: 's.elidrissi@univ-fes.ma',
      role: 'LAB',
      fullName: 'Dr. Salma El Idrissi',
    }),
    getBadges: vi.fn().mockResolvedValue({ candidates: 4 }),
    getLabDashboard: vi.fn().mockResolvedValue({
      labName: 'Genomics Unit, Fès',
      city: 'Fès',
      role: 'CEO',
    }),
    getCandidates: vi.fn(),
    updateApplicationStatus: vi.fn(),
    logout: vi.fn(),
  },
}));

const mockCandidatesResponse = {
  selectedOfferId: 'offer-1',
  selectedOfferTitle: 'Gene expression analysis in mouse cohorts',
  offers: [
    { offerId: 'offer-1', title: 'Gene expression analysis in mouse cohorts', candidateCount: 4 },
  ],
  columns: {
    new: [
      {
        id: 'c-1',
        studentId: 's-1',
        offerId: 'offer-1',
        offerTitle: 'Gene expression analysis in mouse cohorts',
        studentName: 'Imane Rafik',
        level: 'Licence 3',
        fieldOfStudy: 'statistics',
        university: 'UH2C',
        skills: ['R', 'Stats'],
        matchScore: 79,
        status: 'SENT',
        appliedAt: '2026-11-17T12:00:00Z',
        lastActivityAt: '2026-11-17T12:00:00Z',
        coverLetter: 'Hello, I love stats.',
      },
      {
        id: 'c-2',
        studentId: 's-2',
        offerId: 'offer-1',
        offerTitle: 'Gene expression analysis in mouse cohorts',
        studentName: 'Karim Tazi',
        level: 'Master 1',
        fieldOfStudy: 'biology',
        university: 'UIT',
        skills: ['Python', 'Lab work'],
        matchScore: 74,
        status: 'SENT',
        appliedAt: '2026-11-16T12:00:00Z',
        lastActivityAt: '2026-11-16T12:00:00Z',
      },
    ],
    shortlisted: [
      {
        id: 'c-3',
        studentId: 's-3',
        offerId: 'offer-1',
        offerTitle: 'Gene expression analysis in mouse cohorts',
        studentName: 'Hamza Kabbaj',
        level: 'Master 2',
        fieldOfStudy: 'biology',
        university: 'USMBA',
        skills: ['R', 'scRNA-seq'],
        matchScore: 84,
        status: 'SHORTLISTED',
        appliedAt: '2026-11-15T12:00:00Z',
        lastActivityAt: '2026-11-15T12:00:00Z',
      },
    ],
    interview: [
      {
        id: 'c-4',
        studentId: 's-4',
        offerId: 'offer-1',
        offerTitle: 'Gene expression analysis in mouse cohorts',
        studentName: 'Yassine Bennis',
        level: 'Master 1',
        fieldOfStudy: 'data science',
        university: 'UH2C',
        skills: ['DESeq2', 'Nextflow'],
        matchScore: 91,
        status: 'INTERVIEW',
        appliedAt: '2026-11-14T12:00:00Z',
        lastActivityAt: '2026-11-14T12:00:00Z',
      },
    ],
    decision: [
      {
        id: 'c-5',
        studentId: 's-5',
        offerId: 'offer-1',
        offerTitle: 'Gene expression analysis in mouse cohorts',
        studentName: 'Oumaima Sefrioui',
        level: 'Master 2',
        fieldOfStudy: 'genomics',
        university: 'USMBA',
        skills: ['R', 'Bioconductor'],
        matchScore: 86,
        status: 'DECISION_ACCEPTED',
        appliedAt: '2026-11-12T12:00:00Z',
        lastActivityAt: '2026-11-12T12:00:00Z',
      },
    ],
  },
  counts: {
    new: 2,
    shortlisted: 1,
    interview: 1,
    decision: 1,
    total: 5,
  },
};

describe('LabCandidatesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (api.getCandidates as any).mockResolvedValue(mockCandidatesResponse);
  });

  it('renders Kanban board with 4 columns and real candidate cards', async () => {
    render(
      <MemoryRouter>
        <AuthProvider>
          <LabCandidatesPage />
        </AuthProvider>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Gene expression analysis in mouse cohorts')).toBeInTheDocument();
      expect(screen.getByText('New')).toBeInTheDocument();
      expect(screen.getByText('Shortlisted')).toBeInTheDocument();
      expect(screen.getAllByText('Interview').length).toBeGreaterThan(0);
      expect(screen.getByText('Decision')).toBeInTheDocument();
    });

    // Check candidate cards
    expect(screen.getByText('Imane Rafik')).toBeInTheDocument();
    expect(screen.getByText('79%')).toBeInTheDocument();
    expect(screen.getByText('Hamza Kabbaj')).toBeInTheDocument();
    expect(screen.getByText('84%')).toBeInTheDocument();
    expect(screen.getByText('Yassine Bennis')).toBeInTheDocument();
    expect(screen.getByText('91%')).toBeInTheDocument();
    expect(screen.getByText('Oumaima Sefrioui')).toBeInTheDocument();
    expect(screen.getByText('86%')).toBeInTheDocument();
  });

  it('toggles Rank by fit and Sort by date sorting mode', async () => {
    render(
      <MemoryRouter>
        <AuthProvider>
          <LabCandidatesPage />
        </AuthProvider>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Rank by fit')).toBeInTheDocument();
    });

    const sortButton = screen.getByRole('button', { name: /Rank by fit/i });
    fireEvent.click(sortButton);

    expect(screen.getByText('Sort by date')).toBeInTheDocument();
  });
});
