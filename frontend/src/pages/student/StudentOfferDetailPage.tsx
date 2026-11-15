import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { PageShell } from '../../components/common/PageShell';
import { OfferCard } from './StudentExplorePage';

export const StudentOfferDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();

  const [offer, setOffer] = useState<OfferCard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [applied, setApplied] = useState(false);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    fetch(`/api/student/offers/${id}`, { credentials: 'include' })
      .then((res) => {
        if (!res.ok) {
          throw new Error('Offer not found or not published');
        }
        return res.json();
      })
      .then((data) => {
        setOffer(data);
        setLoading(false);
      })
      .catch((err) => {
        setError(err.message);
        setLoading(false);
      });
  }, [id]);

  if (loading) {
    return (
      <PageShell
        title="Loading Offer..."
        subtitle="Computing deterministic match and fetching research details"
        phase="Phase 3 (Offer Detail & Score Engine)"
        pdfPage={4}
      >
        <div className="p-12 text-center text-[#66554D] font-medium">
          Loading offer details...
        </div>
      </PageShell>
    );
  }

  if (error || !offer) {
    return (
      <PageShell
        title="Offer Not Found"
        subtitle="The requested research internship could not be retrieved"
        phase="Phase 3 (Offer Detail & Score Engine)"
        pdfPage={4}
      >
        <div className="bg-[#E2CEB0] p-8 rounded-2xl border border-[#C5AE90] text-center space-y-4">
          <p className="text-sm text-red-800">{error || 'This offer does not exist or has been closed.'}</p>
          <Link
            to="/student/explore"
            className="inline-block px-5 py-2.5 rounded-xl bg-[#5B1421] text-white text-xs font-semibold hover:bg-[#48101A]"
          >
            ← Return to Explore
          </Link>
        </div>
      </PageShell>
    );
  }

  const score = offer.score;
  const overall = score?.overallScore ?? 0;
  const skillsScore = score?.skillsScore ?? 0;
  const interestScore = score?.interestScore ?? 0;
  const datesScore = score?.datesScore ?? 0;

  return (
    <PageShell
      title={offer.title}
      subtitle={`${offer.labName || 'Research Laboratory'} • ${offer.location || 'Morocco'} • ${offer.durationMonths} months`}
      phase="Phase 3 (Offer Detail & Score Engine)"
      pdfPage={4}
      badgeLabel="Detailed Fit Analysis"
    >
      <div className="space-y-6">
        {/* Back Link */}
        <div>
          <Link
            to="/student/explore"
            className="text-xs font-semibold text-[#5B1421] hover:underline inline-flex items-center gap-1"
          >
            ← Back to Explore
          </Link>
        </div>

        {/* Hero Score Box - Exact Mockup Page 4 */}
        <div className="bg-[#DFCAAC] p-6 rounded-2xl border border-[#C5AE90] grid grid-cols-1 md:grid-cols-3 gap-6 items-center">
          {/* Circular Score Indicator */}
          <div className="flex flex-col items-center justify-center text-center md:border-r md:border-[#C5AE90]/80 pr-4">
            <div className="w-24 h-24 rounded-full border-4 border-[#5B1421] flex items-center justify-center font-serif text-3xl font-bold text-[#5B1421] mb-2 shadow-xs">
              {overall}%
            </div>
            <div className="font-serif text-lg font-bold text-[#241814]">
              {score?.matchLabel || 'Calculated match'}
            </div>
            <div className="text-[11px] text-[#66554D] italic mt-0.5">
              Deterministic SE weighting
            </div>
          </div>

          {/* The 3 Score Breakdown Bars: Skills, Interest, Dates */}
          <div className="md:col-span-2 space-y-3.5 pl-2">
            <div>
              <div className="flex justify-between text-xs font-semibold text-[#241814] mb-1">
                <span>Skills match</span>
                <span className="font-bold text-[#5B1421]">{skillsScore}%</span>
              </div>
              <div className="w-full h-2.5 bg-[#C5AE90]/50 rounded-full overflow-hidden">
                <div
                  className="h-full bg-[#5B1421] rounded-full transition-all duration-500"
                  style={{ width: `${skillsScore}%` }}
                />
              </div>
            </div>

            <div>
              <div className="flex justify-between text-xs font-semibold text-[#241814] mb-1">
                <span>Interest & Domain</span>
                <span className="font-bold text-[#5B1421]">{interestScore}%</span>
              </div>
              <div className="w-full h-2.5 bg-[#C5AE90]/50 rounded-full overflow-hidden">
                <div
                  className="h-full bg-[#5B1421] rounded-full transition-all duration-500"
                  style={{ width: `${interestScore}%` }}
                />
              </div>
            </div>

            <div>
              <div className="flex justify-between text-xs font-semibold text-[#241814] mb-1">
                <span>Dates & Availability</span>
                <span className="font-bold text-[#5B1421]">{datesScore}%</span>
              </div>
              <div className="w-full h-2.5 bg-[#C5AE90]/50 rounded-full overflow-hidden">
                <div
                  className="h-full bg-[#5B1421] rounded-full transition-all duration-500"
                  style={{ width: `${datesScore}%` }}
                />
              </div>
            </div>
          </div>
        </div>

        {/* Why Fitwell picked it - Mockup Page 4 */}
        {score?.whyPicked && (
          <div className="p-4 rounded-xl bg-[#5B1421] text-[#F5EFEB] text-xs leading-relaxed shadow-xs">
            <span className="font-semibold text-white">Why Fitwell picked it: </span>
            <span>{score.whyPicked}</span>
          </div>
        )}

        {/* Gap to close section */}
        {score?.gapToClose && score.gapToClose.length > 0 && (
          <div className="bg-[#E2CEB0] p-5 rounded-2xl border border-[#C5AE90] space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="font-serif text-lg font-bold text-[#241814]">
                Gap to close before applying
              </h3>
              <span className="text-xs text-[#66554D]">
                {score.gapToClose.length} skill{score.gapToClose.length > 1 ? 's' : ''} to note
              </span>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {score.gapToClose.map((gap, idx) => (
                <div
                  key={idx}
                  className="p-3 rounded-xl bg-[#DFCAAC] border border-[#C5AE90] space-y-1"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-[#791E2D]">{gap.name}</span>
                    <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-[#E2CEB0] text-[#66554D]">
                      {gap.isRequired ? 'Required' : 'Optional'}
                    </span>
                  </div>
                  <p className="text-[11px] text-[#66554D] leading-tight">{gap.advice}</p>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Research Internship Overview & Tasks */}
        <div className="bg-[#E2CEB0] p-6 rounded-2xl border border-[#C5AE90] space-y-5">
          <div>
            <h3 className="font-serif text-xl font-bold text-[#241814] mb-2">
              Research Objective & Context
            </h3>
            <p className="text-xs text-[#241814] leading-relaxed">
              {offer.description}
            </p>
          </div>

          {/* Key Tasks */}
          {offer.tasks && offer.tasks.length > 0 && (
            <div>
              <h4 className="font-serif text-base font-bold text-[#241814] mb-2">
                Planned Tasks & Deliverables
              </h4>
              <ul className="space-y-2">
                {offer.tasks.map((task, idx) => (
                  <li key={idx} className="flex items-start gap-2.5 text-xs text-[#241814]">
                    <span className="w-4 h-4 rounded-full bg-[#5B1421] text-white flex items-center justify-center text-[10px] font-bold mt-0.5 shrink-0">
                      ✓
                    </span>
                    <span>{task}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {/* Details & Working Conditions */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 pt-3 border-t border-[#C5AE90]/60 text-xs">
            <div>
              <span className="text-[#66554D] block">Duration</span>
              <span className="font-bold text-[#241814]">{offer.durationMonths} months</span>
            </div>
            <div>
              <span className="text-[#66554D] block">Location</span>
              <span className="font-bold text-[#241814]">{offer.location}</span>
            </div>
            <div>
              <span className="text-[#66554D] block">Compensation</span>
              <span className="font-bold text-[#241814]">
                {offer.compensationType} {offer.compensationAmount ? `(${offer.compensationAmount})` : ''}
              </span>
            </div>
            <div>
              <span className="text-[#66554D] block">Positions</span>
              <span className="font-bold text-[#241814]">{offer.places} position{offer.places > 1 ? 's' : ''}</span>
            </div>
          </div>
        </div>

        {/* Laboratory Information */}
        <div className="bg-[#DFCAAC] p-6 rounded-2xl border border-[#C5AE90] flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div>
            <span className="text-[11px] font-semibold uppercase tracking-wider text-[#791E2D]">
              Hosting Research Unit
            </span>
            <h4 className="font-serif text-lg font-bold text-[#241814] mt-0.5">
              {offer.labName}
            </h4>
            <p className="text-xs text-[#66554D]">
              {offer.labUniversity} • {offer.labCity}
            </p>
          </div>

          <div className="flex items-center gap-3">
            <span className="px-3 py-1 rounded-full bg-[#2D6A4F] text-white text-xs font-semibold">
              Verified Laboratory
            </span>
          </div>
        </div>

        {/* Application CTA Bar */}
        <div className="bg-[#5B1421] p-6 rounded-2xl text-white flex flex-col sm:flex-row items-center justify-between gap-4 shadow-sm">
          <div className="space-y-1">
            <div className="font-serif text-lg font-bold">
              Ready to apply to this laboratory?
            </div>
            <p className="text-xs text-[#EFE7D8]/80 max-w-xl">
              Moroccan Law 09-08 Safeguard: Your identity, photo, and profile are protected and will only be transmitted to the laboratory supervisor when you confirm your application.
            </p>
          </div>

          <div className="flex items-center gap-3 shrink-0">
            {applied ? (
              <span className="px-5 py-2.5 rounded-xl bg-[#2D6A4F] text-white text-xs font-semibold">
                ✓ Application Submitted
              </span>
            ) : (
              <button
                type="button"
                onClick={() => setApplied(true)}
                className="px-6 py-3 rounded-xl bg-[#791E2D] hover:bg-[#631724] text-white text-xs font-semibold transition shadow-md"
              >
                Start application
              </button>
            )}
          </div>
        </div>
      </div>
    </PageShell>
  );
};
