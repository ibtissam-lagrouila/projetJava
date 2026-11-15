import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { PageShell } from '../../components/common/PageShell';
import { OfferCard } from './StudentExplorePage';

interface StudentHomeData {
  studentName: string;
  studentUniversity: string;
  studentLevel: string;
  applicationsCount: number;
  inReviewCount: number;
  interviewsCount: number;
  bestMatchToday: OfferCard | null;
  recommendedOffers: OfferCard[];
}

export const StudentHomePage: React.FC = () => {
  const navigate = useNavigate();

  const [data, setData] = useState<StudentHomeData | null>(null);
  const [loading, setLoading] = useState(true);
  const [showCvReminder, setShowCvReminder] = useState(false);

  useEffect(() => {
    fetch('/api/student/onboarding/state', { credentials: 'include' })
      .then((res) => (res.ok ? res.json() : null))
      .then((state) => {
        if (state && (state.onboardingSkipped || (!state.onboardingCompleted && !state.hasDraft))) {
          setShowCvReminder(true);
        }
      })
      .catch(() => {});

    fetch('/api/student/home', { credentials: 'include' })
      .then((res) => {
        if (!res.ok) throw new Error('Failed to fetch home data');
        return res.json();
      })
      .then((homeData: StudentHomeData) => {
        setData(homeData);
        setLoading(false);
      })
      .catch(() => {
        setLoading(false);
      });
  }, []);

  const studentName = data?.studentName || 'Student';
  const bestMatch = data?.bestMatchToday;
  const recommended = data?.recommendedOffers || [];

  const todayStr = new Intl.DateTimeFormat('en-GB', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(new Date());

  return (
    <PageShell
      title={`Good morning, ${studentName}`}
      subtitle={`${todayStr} — Check your best matches and weekly interview slots`}
      phase="Phase 3 (Home & Matching)"
      pdfPage={3}
      badgeLabel="Connected to Java SE API"
    >
      <div className="space-y-6">
        {showCvReminder && (
          <div className="bg-[#E2CEB0] border-l-4 border-[#5B1421] p-4 rounded-xl flex items-center justify-between shadow-xs">
            <div className="flex items-center gap-3">
              <span className="w-8 h-8 rounded-full bg-[#5B1421] text-white flex items-center justify-center font-bold text-xs shrink-0">
                !
              </span>
              <div>
                <h4 className="text-sm font-bold text-[#241814]">Upload your CV</h4>
                <p className="text-xs text-[#66554D]">
                  Complete your CV upload to unlock full matching accuracy with laboratory research offers.
                </p>
              </div>
            </div>
            <button
              onClick={() => navigate('/onboarding/cv')}
              className="px-4 py-2 rounded-lg bg-[#5B1421] text-white text-xs font-semibold hover:bg-[#48101A] transition-colors shrink-0 shadow-xs cursor-pointer"
            >
              Upload CV
            </button>
          </div>
        )}

        {/* Top Summary Metric Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div className="bg-[#E2CEB0] p-4 rounded-xl border border-[#C5AE90] flex items-center justify-between">
            <div>
              <span className="text-[11px] font-semibold text-[#66554D] uppercase tracking-wider block">
                Active Applications
              </span>
              <span className="font-serif text-2xl font-bold text-[#241814]">
                {data?.applicationsCount ?? 0}
              </span>
            </div>
            <Link
              to="/student/applications"
              className="text-xs font-semibold text-[#791E2D] hover:underline"
            >
              View all →
            </Link>
          </div>

          <div className="bg-[#E2CEB0] p-4 rounded-xl border border-[#C5AE90] flex items-center justify-between">
            <div>
              <span className="text-[11px] font-semibold text-[#66554D] uppercase tracking-wider block">
                In Lab Review
              </span>
              <span className="font-serif text-2xl font-bold text-[#241814]">
                {data?.inReviewCount ?? 0}
              </span>
            </div>
            <span className="text-xs text-[#66554D]">Law 09-08 secured</span>
          </div>

          <div className="bg-[#E2CEB0] p-4 rounded-xl border border-[#C5AE90] flex items-center justify-between">
            <div>
              <span className="text-[11px] font-semibold text-[#66554D] uppercase tracking-wider block">
                Weekly Interviews
              </span>
              <span className="font-serif text-2xl font-bold text-[#791E2D]">
                {data?.interviewsCount ?? 0}
              </span>
            </div>
            <span className="text-xs text-[#66554D]">Slots open</span>
          </div>
        </div>

        {/* Hero Section: Best Match Today & Score Indicator (Faithful to Mockup Page 3) */}
        {bestMatch ? (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Best Match Hero Card */}
            <div className="lg:col-span-2 bg-[#DFCAAC] p-6 rounded-2xl border border-[#C5AE90] flex flex-col justify-between shadow-sm">
              <div>
                <div className="flex items-center justify-between">
                  <span className="px-2.5 py-0.5 rounded-full bg-[#5B1421] text-white text-xs font-semibold">
                    Best match today
                  </span>
                  {bestMatch.daysRemaining > 0 ? (
                    <span className="text-xs text-[#66554D]">
                      closes in {bestMatch.daysRemaining} days
                    </span>
                  ) : (
                    <span className="text-xs text-[#66554D]">closing soon</span>
                  )}
                </div>

                <h2 className="font-serif text-2xl font-bold text-[#241814] mt-2 mb-1">
                  {bestMatch.title}
                </h2>
                <p className="text-xs text-[#66554D] mb-4">
                  {bestMatch.labName}
                  {bestMatch.location ? `, ${bestMatch.location}` : ''}
                  {bestMatch.durationMonths ? ` - ${bestMatch.durationMonths} months` : ''}
                  {bestMatch.compensationType ? ` - ${bestMatch.compensationType.toLowerCase()}` : ''}
                </p>

                {bestMatch.score?.whyPicked && (
                  <div className="p-3.5 rounded-xl bg-[#5B1421] text-[#F5EFEB] text-xs leading-relaxed mb-4">
                    <span className="font-semibold text-white">Why Fitwell picked it: </span>
                    <span>{bestMatch.score.whyPicked}</span>
                  </div>
                )}
              </div>

              <div className="flex items-center gap-3">
                <button
                  type="button"
                  onClick={() => navigate(`/student/offers/${bestMatch.id}`)}
                  className="px-4 py-2 rounded-xl bg-[#5B1421] text-white text-xs font-semibold hover:bg-[#48101A] transition"
                >
                  View offer
                </button>
                <button
                  type="button"
                  onClick={() => navigate(`/student/offers/${bestMatch.id}`)}
                  className="px-4 py-2 rounded-xl bg-[#E2CEB0] border border-[#791E2D] text-[#791E2D] text-xs font-semibold hover:bg-[#D6C1A2] transition"
                >
                  Start application
                </button>
              </div>
            </div>

            {/* Score indicator preview - Mockup Page 3 */}
            <div className="bg-[#DFCAAC] p-6 rounded-2xl border border-[#C5AE90] flex flex-col items-center justify-center text-center shadow-sm">
              <div className="w-24 h-24 rounded-full border-4 border-[#5B1421] flex items-center justify-center font-serif text-3xl font-bold text-[#5B1421] mb-3 shadow-xs">
                {bestMatch.score?.overallScore ?? 0}
              </div>
              <div className="font-serif text-sm font-bold text-[#241814] mb-3">
                {bestMatch.score?.matchLabel ?? 'Calculated match'}
              </div>
              <div className="space-y-1.5 w-full text-xs text-[#66554D]">
                <div className="flex justify-between">
                  <span>skills</span>
                  <span className="font-bold text-[#5B1421]">
                    {bestMatch.score?.skillsScore ?? 0}%
                  </span>
                </div>
                <div className="flex justify-between">
                  <span>interest</span>
                  <span className="font-bold text-[#5B1421]">
                    {bestMatch.score?.interestScore ?? 0}%
                  </span>
                </div>
                <div className="flex justify-between">
                  <span>dates</span>
                  <span className="font-bold text-[#5B1421]">
                    {bestMatch.score?.datesScore ?? 0}%
                  </span>
                </div>
              </div>
            </div>
          </div>
        ) : (
          !loading && (
            <div className="bg-[#E2CEB0] p-8 rounded-2xl border border-[#C5AE90] text-center">
              <p className="text-sm text-[#66554D]">
                No active offers matching your current profile. Visit the{' '}
                <Link to="/student/explore" className="text-[#5B1421] font-semibold underline">
                  Explore page
                </Link>{' '}
                to browse all published research opportunities.
              </p>
            </div>
          )
        )}

        {/* Other Recommended Research Internships */}
        {recommended.length > 0 && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-serif text-xl font-bold text-[#241814]">
                Other Recommended Research Internships
              </h3>
              <Link
                to="/student/explore"
                className="text-xs font-semibold text-[#5B1421] hover:underline"
              >
                Browse all offers →
              </Link>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {recommended.map((offer) => (
                <div
                  key={offer.id}
                  className="bg-[#E2CEB0] p-5 rounded-2xl border border-[#C5AE90] flex flex-col justify-between hover:border-[#5B1421] transition shadow-xs"
                >
                  <div>
                    <div className="flex items-center justify-between mb-2">
                      <span className="px-2.5 py-0.5 rounded-full bg-[#5B1421] text-white text-xs font-semibold">
                        {offer.score?.overallScore}% {offer.score?.matchLabel}
                      </span>
                      <span className="text-xs text-[#66554D]">
                        {offer.location} • {offer.durationMonths}m
                      </span>
                    </div>

                    <h4 className="font-serif text-lg font-bold text-[#241814] mb-1">
                      {offer.title}
                    </h4>
                    <p className="text-xs text-[#66554D] mb-3">
                      {offer.labName} {offer.labUniversity ? `• ${offer.labUniversity}` : ''}
                    </p>

                    {offer.score?.whyPicked && (
                      <p className="text-xs text-[#241814] bg-[#DFCAAC] p-2.5 rounded-lg mb-3 line-clamp-2">
                        <strong className="text-[#5B1421]">Why: </strong>
                        {offer.score.whyPicked}
                      </p>
                    )}
                  </div>

                  <div className="flex items-center justify-between pt-3 border-t border-[#C5AE90]/60">
                    <span className="text-[11px] text-[#66554D]">
                      {offer.compensationType || 'Funded'}
                    </span>
                    <button
                      type="button"
                      onClick={() => navigate(`/student/offers/${offer.id}`)}
                      className="text-xs font-semibold text-[#791E2D] hover:underline"
                    >
                      View details →
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </PageShell>
  );
};
