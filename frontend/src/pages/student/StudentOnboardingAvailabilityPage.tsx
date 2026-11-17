import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

export const StudentOnboardingAvailabilityPage: React.FC = () => {
  const navigate = useNavigate();
  const { user } = useAuth();

  const [availabilityStart, setAvailabilityStart] = useState<string>('2027-02-01');
  const [durationMonths, setDurationMonths] = useState<number>(5);
  const [loading, setLoading] = useState<boolean>(false);

  const handleFinish = async () => {
    setLoading(true);
    try {
      await fetch('/api/student/onboarding/complete', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('fitwell_token') || ''}`,
        },
        credentials: 'include',
        body: JSON.stringify({
          fullName: user?.fullName,
          availabilityStart,
          availabilityDurationMonths: durationMonths,
        }),
      });
    } catch {
      // Proceed even on error
    } finally {
      setLoading(false);
      navigate('/student/home');
    }
  };

  const handleSkip = async () => {
    try {
      await fetch('/api/student/onboarding/skip', {
        method: 'POST',
        credentials: 'include',
      });
    } catch {
      // Proceed
    }
    navigate('/student/home');
  };

  return (
    <div className="min-h-screen bg-[#D3B996] text-[#241814] flex flex-col justify-between p-8 md:p-12 font-sans select-none">
      {/* Top Header Navigation */}
      <div className="flex items-center justify-between pb-8">
        <div className="flex items-center gap-3">
          <div className="w-7 h-7 bg-[#791E2D] rounded flex items-center justify-center font-serif text-white font-bold text-lg shadow-sm">
            F
          </div>
          <span className="font-serif text-2xl tracking-tight text-[#241814]">Fitwell</span>
        </div>

        {/* 4 Steps Stepper */}
        <div className="flex items-center gap-8 text-xs font-semibold">
          <button
            onClick={() => navigate('/onboarding/cv')}
            className="pb-1 border-b-2 border-[#791E2D] text-[#791E2D] transition-colors"
          >
            Account
          </button>
          <button
            onClick={() => navigate('/onboarding/cv')}
            className="pb-1 border-b-2 border-[#791E2D] text-[#791E2D] transition-colors"
          >
            Your CV
          </button>
          <button
            onClick={() => navigate('/onboarding/interests')}
            className="pb-1 border-b-2 border-[#791E2D] text-[#791E2D] transition-colors"
          >
            Interests
          </button>
          <button
            className="pb-1 border-b-2 border-[#791E2D] text-[#791E2D] transition-colors font-bold"
          >
            Availability
          </button>
        </div>

        <button
          onClick={handleSkip}
          className="text-xs font-medium text-[#66554D] hover:text-[#241814]"
        >
          Skip for now
        </button>
      </div>

      {/* Main Content */}
      <div className="max-w-2xl mx-auto w-full space-y-6 my-auto">
        <div>
          <div className="text-xs text-[#66554D] font-semibold mb-2">Step 4 of 4</div>
          <h1 className="font-serif text-4xl font-bold text-[#241814] mb-3">
            When can you start your internship?
          </h1>
          <p className="text-sm text-[#66554D]">
            Match offers based on your academic calendar and internship semester.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-semibold text-[#241814] mb-1.5">
              Earliest start date
            </label>
            <input
              type="date"
              value={availabilityStart}
              onChange={(e) => setAvailabilityStart(e.target.value)}
              className="w-full px-4 py-3 rounded-xl bg-[#DFCAAC] border border-[#BFA789] text-sm text-[#241814] focus:ring-2 focus:ring-[#791E2D]"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-[#241814] mb-1.5">
              Desired duration (months)
            </label>
            <select
              value={durationMonths}
              onChange={(e) => setDurationMonths(Number(e.target.value))}
              className="w-full px-4 py-3 rounded-xl bg-[#DFCAAC] border border-[#BFA789] text-sm text-[#241814] focus:ring-2 focus:ring-[#791E2D]"
            >
              <option value={3}>3 months</option>
              <option value={4}>4 months</option>
              <option value={5}>5 months</option>
              <option value={6}>6 months</option>
            </select>
          </div>
        </div>

        <div className="p-4 rounded-2xl bg-[#E2CEB0] border border-[#BFA789] text-xs text-[#66554D] leading-relaxed">
          <span className="font-bold text-[#241814]">Privacy note:</span> Dates and skills are used solely for calculating compatibility scores. Name, photo, gender, and origin are never factored into rankings.
        </div>

        <div className="flex justify-between items-center pt-6">
          <button
            type="button"
            onClick={() => navigate('/onboarding/interests')}
            className="text-xs font-semibold text-[#66554D] hover:text-[#241814]"
          >
            ← Back to Interests
          </button>
          <button
            type="button"
            onClick={handleFinish}
            disabled={loading}
            className="px-6 py-3 rounded-xl bg-[#791E2D] hover:bg-[#631724] text-white text-xs font-semibold shadow-sm disabled:opacity-70"
          >
            {loading ? 'Saving profile...' : 'Save & explore offers'}
          </button>
        </div>
      </div>

      {/* Footer */}
      <div className="text-center text-xs text-[#8D7A68] pt-8">
        Fitwell — Academic Research Internships in Morocco
      </div>
    </div>
  );
};

