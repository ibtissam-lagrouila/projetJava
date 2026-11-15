import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';

export const StudentOnboardingInterestsPage: React.FC = () => {
  const navigate = useNavigate();

  const [interests, setInterests] = useState<string[]>([
    'Medical imaging',
    'Machine Learning',
    'Computer Vision',
    'Bioinformatics',
  ]);

  const availableTopics = [
    'Medical imaging',
    'Machine Learning',
    'Computer Vision',
    'Bioinformatics',
    'Natural Language Processing',
    'Robotics',
    'Genomics',
    'Materials Science',
    'Climate Modeling',
    'Renewable Energy',
  ];

  const handleSkip = async () => {
    try {
      await fetch('/api/student/onboarding/skip', {
        method: 'POST',
        credentials: 'include',
      });
    } catch {
      // Proceed even on network error
    }
    navigate('/student/home');
  };

  const handleContinue = () => {
    navigate('/onboarding/availability');
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
            className="pb-1 border-b-2 border-[#791E2D] text-[#791E2D] transition-colors font-bold"
          >
            Interests
          </button>
          <button
            onClick={() => navigate('/onboarding/availability')}
            className="pb-1 border-b-2 border-transparent text-[#66554D] hover:text-[#241814] transition-colors"
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
          <div className="text-xs text-[#66554D] font-semibold mb-2">Step 3 of 4</div>
          <h1 className="font-serif text-4xl font-bold text-[#241814] mb-3">
            What research domains excite you?
          </h1>
          <p className="text-sm text-[#66554D]">
            Fitwell uses these topics to prioritize laboratory offers and compute your interest compatibility score.
          </p>
        </div>

        <div className="flex flex-wrap gap-2.5">
          {availableTopics.map((topic) => {
            const selected = interests.includes(topic);
            return (
              <button
                key={topic}
                type="button"
                onClick={() => {
                  if (selected) {
                    setInterests(interests.filter((i) => i !== topic));
                  } else {
                    setInterests([...interests, topic]);
                  }
                }}
                className={`px-4 py-2 rounded-xl text-xs font-semibold transition-all border ${
                  selected
                    ? 'bg-[#5B1421] text-white border-[#5B1421] shadow-sm'
                    : 'bg-[#E2CEB0] text-[#241814] border-[#BFA789] hover:bg-[#D6C1A2]'
                }`}
              >
                {topic}
              </button>
            );
          })}
        </div>

        <div className="flex justify-between items-center pt-6">
          <button
            type="button"
            onClick={() => navigate('/onboarding/cv')}
            className="text-xs font-semibold text-[#66554D] hover:text-[#241814]"
          >
            ← Back to CV
          </button>
          <button
            type="button"
            onClick={handleContinue}
            className="px-6 py-2.5 rounded-xl bg-[#5B1421] text-white text-xs font-semibold shadow-sm hover:bg-[#48101A]"
          >
            Continue to Availability
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
