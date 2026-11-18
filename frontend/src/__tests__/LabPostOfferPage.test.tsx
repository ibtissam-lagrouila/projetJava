import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LabPostOfferPage } from '../pages/lab/LabPostOfferPage';
import { api } from '../services/api';

vi.mock('../services/api', () => ({
  api: {
    getLabDashboard: vi.fn().mockResolvedValue({
      labName: 'Genomics Unit',
      city: 'Fès',
      activeLabId: 'lab-123',
    }),
    createLabOffer: vi.fn().mockResolvedValue({
      id: 'mock-offer-uuid',
      message: 'Offer published successfully',
    }),
    getLabOffers: vi.fn().mockResolvedValue([]),
  },
}));

describe('LabPostOfferPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders all form fields, start from idea banner, live preview card and quality gauge faithfully matching mockup page 12', async () => {
    render(
      <MemoryRouter>
        <LabPostOfferPage />
      </MemoryRouter>
    );

    // Title and banner
    expect(screen.getByText('Post an offer')).toBeInTheDocument();
    expect(screen.getByText('Describe the internship')).toBeInTheDocument();
    expect(screen.getByText('Start from an idea')).toBeInTheDocument();
    expect(screen.getByText('Draft the offer')).toBeInTheDocument();

    // Inputs
    expect(screen.getByPlaceholderText(/e\.g\. Gene expression analysis/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/e\.g\. Biology, bioinformatics/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/e\.g\. 5 months/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/e\.g\. February 2027/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/e\.g\. Fès, on site/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/e\.g\. Paid/i)).toBeInTheDocument();

    // Skills pills
    expect(screen.getByText('Python')).toBeInTheDocument();
    expect(screen.getByText('Nextflow')).toBeInTheDocument();
    expect(screen.getByText('Statistics')).toBeInTheDocument();

    // Live preview and quality score (92 matching page-12 mockup)
    expect(screen.getByText('Live preview')).toBeInTheDocument();
    expect(screen.getByText('Offer quality')).toBeInTheDocument();
    expect(screen.getByTestId('quality-score')).toHaveTextContent('92');
    expect(screen.getByText(/Clear goals and skills\. Add a supervisor photo\./i)).toBeInTheDocument();

    // Buttons
    expect(screen.getByRole('button', { name: /Publish offer/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Save draft/i })).toBeInTheDocument();
  });

  it('submits published offer to backend API when clicking Publish offer', async () => {
    render(
      <MemoryRouter>
        <LabPostOfferPage />
      </MemoryRouter>
    );

    // Wait for lab info to load
    await waitFor(() => {
      expect(api.getLabDashboard).toHaveBeenCalled();
    });

    const publishBtn = screen.getByRole('button', { name: /Publish offer/i });
    fireEvent.click(publishBtn);

    await waitFor(() => {
      expect(api.createLabOffer).toHaveBeenCalledWith(
        expect.objectContaining({
          title: expect.stringContaining('Gene expression analysis'),
          domain: expect.stringContaining('Biology'),
          durationMonths: 5,
          location: expect.stringContaining('Fès'),
          compensationType: 'Paid',
          isDraft: false,
        }),
        'lab-123'
      );
    });

    // Check success modal appears
    await waitFor(() => {
      expect(screen.getByText('Offer published live!')).toBeInTheDocument();
      expect(screen.getByText('View in Student Explore')).toBeInTheDocument();
    });
  });

  it('allows adding and removing skills dynamically', async () => {
    render(
      <MemoryRouter>
        <LabPostOfferPage />
      </MemoryRouter>
    );

    // Remove Python
    const removePython = screen.getByTitle('Remove Python');
    fireEvent.click(removePython);
    expect(screen.queryByText('Python')).not.toBeInTheDocument();

    // Add Docker
    const addInput = screen.getByPlaceholderText('+ Add skill');
    fireEvent.change(addInput, { target: { value: 'Docker' } });
    fireEvent.keyDown(addInput, { key: 'Enter', code: 'Enter' });

    expect(screen.getByText('Docker')).toBeInTheDocument();
  });
});
