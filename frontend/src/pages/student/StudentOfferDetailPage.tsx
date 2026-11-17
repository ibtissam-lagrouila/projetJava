import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { PageShell } from '../../components/common/PageShell';
import { OfferCard } from './StudentExplorePage';

export const StudentOfferDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();

  const [offer, setOffer] = useState<OfferCard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Verification state for external links
  const [verifyingLink, setVerifyingLink] = useState(false);
  const [linkWarning, setLinkWarning] = useState<string | null>(null);

  // Internal application modal states (4-step flow: Documents, Letter, Review, Confirmation)
  const [isApplyModalOpen, setIsApplyModalOpen] = useState(false);
  const [modalStep, setModalStep] = useState<1 | 2 | 3 | 4>(1);
  const [coverLetter, setCoverLetter] = useState(
    'I am writing to express my strong interest in this research internship position. My academic background and verified skills closely match your laboratory needs.'
  );
  const [explicitConfirmed, setExplicitConfirmed] = useState(false);
  const [submittingInternal, setSubmittingInternal] = useState(false);
  const [internalSuccess, setInternalSuccess] = useState(false);

  // External manual tracking state ("I applied")
  const [submittingManual, setSubmittingManual] = useState(false);
  const [manualTracked, setManualTracked] = useState(false);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    fetch(`/api/student/offers/${id}`, { credentials: 'include' })
      .then((res) => {
        if (!res.ok) {
          throw new Error('Offer not found or no longer available');
        }
        return res.json();
      })
      .then((data: OfferCard) => {
        setOffer(data);
        if (data.isMayBeClosed || data.status === 'MAY_BE_CLOSED') {
          setLinkWarning('This offer may be closed or expired on the original website.');
        }
        setLoading(false);
      })
      .catch((err) => {
        setError(err.message);
        setLoading(false);
      });
  }, [id]);

  const handleVerifyBeforeOpen = async () => {
    if (!offer || !offer.sourceUrl) return;
    setVerifyingLink(true);
    try {
      const res = await fetch(`/api/student/offers/${offer.id}/verify-link`, {
        method: 'POST',
        credentials: 'include',
      });
      if (res.ok) {
        const check = await res.json();
        if (!check.alive) {
          setLinkWarning('Notice: Verification indicates this offer may be closed or no longer accepting applications.');
        }
      }
    } catch {
      // verification non-blocking
    } finally {
      setVerifyingLink(false);
    }
  };

  const handleConfirmFitwellApply = async () => {
    if (!offer || !explicitConfirmed) return;
    setSubmittingInternal(true);
    try {
      const res = await fetch('/api/student/applications', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('token') || ''}`,
        },
        credentials: 'include',
        body: JSON.stringify({
          offerId: offer.id,
          coverLetter: coverLetter.trim(),
          isManualExternal: false,
          confirmed: true,
        }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.message || 'Failed to submit application');
      }
      setInternalSuccess(true);
      setIsApplyModalOpen(false);
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Error submitting application');
    } finally {
      setSubmittingInternal(false);
    }
  };

  const handleManualExternalApplied = async () => {
    if (!offer) return;
    setSubmittingManual(true);
    try {
      const res = await fetch('/api/student/applications', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('token') || ''}`,
        },
        credentials: 'include',
        body: JSON.stringify({
          offerId: offer.id,
          coverLetter: 'Applied directly on original external website.',
          isManualExternal: true,
          confirmed: true,
        }),
      });
      if (res.ok) {
        setManualTracked(true);
      }
    } catch {
      setManualTracked(true);
    } finally {
      setSubmittingManual(false);
    }
  };

  if (loading) {
    return (
      <PageShell
        title="Loading Offer Details..."
        subtitle="Computing deterministic match and fetching research criteria"
        phase="Explore & Matching Engine"
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
        subtitle="The requested opportunity could not be retrieved"
        phase="Explore & Matching Engine"
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

  const isExternal = offer.origin === 'SOURCED';
  const isDemo = offer.origin === 'DEMO';
  const score = offer.score;
  const overall = score?.overallScore ?? 0;
  const skillsScore = score?.skillsScore ?? 0;
  const interestScore = score?.interestScore ?? 0;
  const datesScore = score?.datesScore ?? 0;

  return (
    <PageShell
      title={offer.title}
      subtitle={`${offer.labName || 'Research Laboratory'} • ${offer.location || 'Morocco'} • ${offer.durationMonths} months`}
      phase="Explore & Matching Engine"
      pdfPage={4}
      badgeLabel={isDemo ? 'DEMO · fictional' : isExternal ? `via ${offer.sourceName || 'External source'}` : 'Verified Lab Opportunity'}
    >
      <div className="space-y-6">
        {/* Back Link */}
        <div className="flex items-center justify-between">
          <Link
            to="/student/explore"
            className="text-xs font-semibold text-[#5B1421] hover:underline inline-flex items-center gap-1"
          >
            ← Back to Explore
          </Link>
          <span className="text-xs text-[#66554D] font-medium">
            {offer.displayDate || (offer.closesAt ? `Closes ${new Date(offer.closesAt).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })}` : 'Open')}
          </span>
        </div>

        {/* Link Warning Banner if dead or expired */}
        {linkWarning && (
          <div className="p-4 rounded-xl bg-amber-50 border border-amber-300 text-amber-900 text-xs flex items-center justify-between gap-3 shadow-2xs">
            <div className="flex items-center gap-2">
              <span className="text-base">⚠️</span>
              <span>{linkWarning}</span>
            </div>
            <span className="text-[11px] text-amber-700 font-semibold uppercase">Notice</span>
          </div>
        )}

        {/* Hero Score Box */}
        <div className="bg-[#DFCAAC] p-6 rounded-2xl border border-[#C5AE90] grid grid-cols-1 md:grid-cols-3 gap-6 items-center">
          <div className="flex flex-col items-center justify-center text-center md:border-r md:border-[#C5AE90]/80 pr-4">
            <div className="w-24 h-24 rounded-full border-4 border-[#5B1421] flex items-center justify-center font-serif text-3xl font-bold text-[#5B1421] mb-2 shadow-xs">
              {overall}%
            </div>
            <div className="font-serif text-lg font-bold text-[#241814]">
              {score?.matchLabel || 'Calculated match'}
            </div>
            <div className="text-[11px] text-[#66554D] italic mt-0.5">
              Deterministic scoring
            </div>
          </div>

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

        {/* Why Fitwell picked it */}
        {score?.whyPicked && (
          <div className="p-4 rounded-xl bg-[#5B1421] text-[#F5EFEB] text-xs leading-relaxed shadow-xs">
            <span className="font-semibold text-white">Why Fitwell picked it: </span>
            <span>{score.whyPicked}</span>
          </div>
        )}

        {/* Common Skills with CV */}
        {score?.matchedSkills && score.matchedSkills.length > 0 && (
          <div className="bg-[#E2CEB0] p-5 rounded-2xl border border-[#C5AE90] space-y-2">
            <h3 className="font-serif text-base font-bold text-[#241814]">
              Common skills found in your CV
            </h3>
            <div className="flex flex-wrap gap-2">
              {score.matchedSkills.map((sk, idx) => (
                <span
                  key={idx}
                  className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#DFCAAC] border border-[#C5AE90] text-xs font-semibold text-[#5B1421]"
                >
                  <span>✓</span>
                  <span>{sk}</span>
                </span>
              ))}
            </div>
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

        {/* Opportunity Overview & Details */}
        <div className="bg-[#E2CEB0] p-6 rounded-2xl border border-[#C5AE90] space-y-5">
          <div>
            <h3 className="font-serif text-xl font-bold text-[#241814] mb-2">
              Description & Context
            </h3>
            <p className="text-xs text-[#241814] leading-relaxed whitespace-pre-line">
              {offer.description}
            </p>
          </div>

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
              <span className="text-[#66554D] block">Source</span>
              <span className="font-bold text-[#241814]">{offer.sourceName || 'Fitwell Lab'}</span>
            </div>
          </div>
        </div>

        {/* Recruiter / Laboratory Information */}
        <div className="bg-[#DFCAAC] p-6 rounded-2xl border border-[#C5AE90] flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div>
            <span className="text-[11px] font-semibold uppercase tracking-wider text-[#791E2D]">
              Organization
            </span>
            <h4 className="font-serif text-lg font-bold text-[#241814] mt-0.5">
              {offer.labName}
            </h4>
            <p className="text-xs text-[#66554D]">
              {offer.labUniversity} • {offer.labCity}
            </p>
          </div>

          <div className="flex items-center gap-2">
            <span className="px-3 py-1 rounded-full bg-[#2D6A4F] text-white text-xs font-semibold">
              {isExternal ? 'External Connector' : 'Verified Partner'}
            </span>
          </div>
        </div>

        {/* Application Action Section */}
        {isExternal ? (
          /* EXTERNAL OFFER: Apply on original site + Manual tracking */
          <div className="bg-[#5B1421] p-6 rounded-2xl text-white space-y-4 shadow-sm">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div className="space-y-1">
                <h3 className="font-serif text-lg font-bold">
                  Apply on the original employer website
                </h3>
                <p className="text-xs text-[#EFE7D8]/80 max-w-xl">
                  This offer was sourced from an external website. Applications must be completed directly on the source platform.
                </p>
              </div>

              {offer.sourceUrl && (
                <a
                  href={offer.sourceUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  onClick={handleVerifyBeforeOpen}
                  className="px-6 py-3 rounded-xl bg-[#2D6A4F] hover:bg-[#24543E] text-white text-xs font-bold transition shadow-md inline-flex items-center gap-2 shrink-0"
                >
                  <span>{verifyingLink ? 'Checking...' : 'Apply on the original site'}</span>
                  <span>↗</span>
                </a>
              )}
            </div>

            {/* Manual Tracking Section: "I applied" */}
            <div className="pt-4 border-t border-white/15 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
              <div className="text-[#EFE7D8]/85">
                <span>Already submitted your application on the original site? </span>
                <span className="text-white/60">Track your progress in Applications (self-reported, nothing sent by Fitwell).</span>
              </div>

              {manualTracked ? (
                <span className="px-4 py-2 rounded-xl bg-[#2D6A4F] text-white font-semibold text-xs shrink-0 inline-flex items-center gap-1.5">
                  ✓ Tracked in Applications (Sent)
                </span>
              ) : (
                <button
                  type="button"
                  onClick={handleManualExternalApplied}
                  disabled={submittingManual}
                  className="px-5 py-2.5 rounded-xl bg-white/15 hover:bg-white/25 text-white font-semibold text-xs transition shrink-0"
                >
                  {submittingManual ? 'Saving...' : 'I applied'}
                </button>
              )}
            </div>
          </div>
        ) : (
          /* INTERNAL OFFER: Apply with Fitwell (4-step modal flow) */
          <div className="bg-[#5B1421] p-6 rounded-2xl text-white flex flex-col sm:flex-row items-center justify-between gap-4 shadow-sm">
            <div className="space-y-1">
              <div className="font-serif text-lg font-bold">
                Apply directly with Fitwell
              </div>
              <p className="text-xs text-[#EFE7D8]/80 max-w-xl">
                Moroccan Law 09-08 Safeguard: Your verified student profile and CV will be transmitted to the recruiter only upon your explicit confirmation.
              </p>
            </div>

            <div className="shrink-0">
              {internalSuccess ? (
                <span className="px-5 py-2.5 rounded-xl bg-[#2D6A4F] text-white text-xs font-semibold inline-flex items-center gap-1.5">
                  ✓ Application Submitted to Lab
                </span>
              ) : (
                <button
                  type="button"
                  onClick={() => setIsApplyModalOpen(true)}
                  className="px-6 py-3 rounded-xl bg-[#DFCAAC] hover:bg-[#C5AE90] text-[#241814] text-xs font-bold transition shadow-md"
                >
                  Apply with Fitwell
                </button>
              )}
            </div>
          </div>
        )}

        {/* 4-Step Fitwell Application Modal */}
        {isApplyModalOpen && (
          <div className="fixed inset-0 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4 z-50">
            <div className="bg-[#FDFBF7] rounded-2xl max-w-xl w-full border border-[#C5AE90] shadow-2xl p-6 md:p-8 space-y-6">
              {/* Header & Step Indicator */}
              <div className="flex items-center justify-between pb-3 border-b border-[#C5AE90]/60">
                <div>
                  <h3 className="font-serif text-xl font-bold text-[#241814]">
                    Apply with Fitwell
                  </h3>
                  <p className="text-xs text-[#66554D]">
                    {offer.title} · {offer.labName}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setIsApplyModalOpen(false)}
                  className="text-[#66554D] hover:text-[#241814] text-lg font-bold"
                >
                  ×
                </button>
              </div>

              {/* Progress Steps Header */}
              <div className="grid grid-cols-4 gap-2 text-center text-xs">
                <button
                  type="button"
                  onClick={() => setModalStep(1)}
                  className={`py-1.5 rounded-lg font-semibold transition ${
                    modalStep === 1 ? 'bg-[#5B1421] text-white' : 'bg-[#EFE7D8] text-[#66554D]'
                  }`}
                >
                  1. Documents
                </button>
                <button
                  type="button"
                  onClick={() => setModalStep(2)}
                  className={`py-1.5 rounded-lg font-semibold transition ${
                    modalStep === 2 ? 'bg-[#5B1421] text-white' : 'bg-[#EFE7D8] text-[#66554D]'
                  }`}
                >
                  2. Letter
                </button>
                <button
                  type="button"
                  onClick={() => setModalStep(3)}
                  className={`py-1.5 rounded-lg font-semibold transition ${
                    modalStep === 3 ? 'bg-[#5B1421] text-white' : 'bg-[#EFE7D8] text-[#66554D]'
                  }`}
                >
                  3. Review
                </button>
                <button
                  type="button"
                  onClick={() => setModalStep(4)}
                  className={`py-1.5 rounded-lg font-semibold transition ${
                    modalStep === 4 ? 'bg-[#5B1421] text-white' : 'bg-[#EFE7D8] text-[#66554D]'
                  }`}
                >
                  4. Confirm
                </button>
              </div>

              {/* Step Content */}
              {modalStep === 1 && (
                <div className="space-y-4 text-xs">
                  <h4 className="font-serif text-sm font-bold text-[#241814]">
                    Step 1: Verified Documents
                  </h4>
                  <div className="p-4 rounded-xl bg-[#EFE7D8] border border-[#C5AE90] space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-[#241814]">Active CV Profile:</span>
                      <span className="text-[#2D6A4F] font-semibold">✓ Verified & Attached</span>
                    </div>
                    <p className="text-[#66554D] text-[11px]">
                      Your parsed academic background, skills, and projects from CV Studio will be shared with the lab.
                    </p>
                  </div>
                  <div className="flex justify-end pt-2">
                    <button
                      type="button"
                      onClick={() => setModalStep(2)}
                      className="px-5 py-2 rounded-xl bg-[#5B1421] text-white font-semibold text-xs hover:bg-[#791E2D]"
                    >
                      Next: Cover Letter →
                    </button>
                  </div>
                </div>
              )}

              {modalStep === 2 && (
                <div className="space-y-4 text-xs">
                  <h4 className="font-serif text-sm font-bold text-[#241814]">
                    Step 2: Cover Letter
                  </h4>
                  <p className="text-[#66554D]">
                    Customize your statement of motivation for this research topic:
                  </p>
                  <textarea
                    rows={4}
                    value={coverLetter}
                    onChange={(e) => setCoverLetter(e.target.value)}
                    className="w-full p-3 rounded-xl bg-[#F5EFEB] border border-[#C5AE90] text-xs text-[#241814] focus:outline-none focus:ring-1 focus:ring-[#5B1421]"
                  />
                  <div className="flex justify-between pt-2">
                    <button
                      type="button"
                      onClick={() => setModalStep(1)}
                      className="px-4 py-2 rounded-xl bg-[#EFE7D8] text-[#241814] font-semibold text-xs"
                    >
                      ← Back
                    </button>
                    <button
                      type="button"
                      onClick={() => setModalStep(3)}
                      className="px-5 py-2 rounded-xl bg-[#5B1421] text-white font-semibold text-xs hover:bg-[#791E2D]"
                    >
                      Next: Review →
                    </button>
                  </div>
                </div>
              )}

              {modalStep === 3 && (
                <div className="space-y-4 text-xs">
                  <h4 className="font-serif text-sm font-bold text-[#241814]">
                    Step 3: Review Application
                  </h4>
                  <div className="p-4 rounded-xl bg-[#EFE7D8] border border-[#C5AE90] space-y-2">
                    <p><strong>Target Offer:</strong> {offer.title}</p>
                    <p><strong>Recruiter / Lab:</strong> {offer.labName} ({offer.location})</p>
                    <p><strong>Duration:</strong> {offer.durationMonths} months</p>
                    <p><strong>Match Score:</strong> {overall}%</p>
                  </div>
                  <div className="flex justify-between pt-2">
                    <button
                      type="button"
                      onClick={() => setModalStep(2)}
                      className="px-4 py-2 rounded-xl bg-[#EFE7D8] text-[#241814] font-semibold text-xs"
                    >
                      ← Back
                    </button>
                    <button
                      type="button"
                      onClick={() => setModalStep(4)}
                      className="px-5 py-2 rounded-xl bg-[#5B1421] text-white font-semibold text-xs hover:bg-[#791E2D]"
                    >
                      Next: Explicit Confirmation →
                    </button>
                  </div>
                </div>
              )}

              {modalStep === 4 && (
                <div className="space-y-4 text-xs">
                  <h4 className="font-serif text-sm font-bold text-[#241814]">
                    Step 4: Explicit Confirmation
                  </h4>
                  <div className="p-4 rounded-xl bg-[#FBF3E4] border border-[#E2CEB0] text-[#78350F] text-xs space-y-2">
                    <p className="font-bold">Moroccan Law 09-08 Consent:</p>
                    <p>
                      In compliance with Moroccan data protection laws, Fitwell will never submit your application automatically without your conscious confirmation.
                    </p>
                  </div>

                  <label className="flex items-start gap-3 p-3 rounded-xl border border-[#C5AE90] bg-[#F5EFEB] cursor-pointer">
                    <input
                      type="checkbox"
                      checked={explicitConfirmed}
                      onChange={(e) => setExplicitConfirmed(e.target.checked)}
                      className="mt-0.5 accent-[#5B1421] w-4 h-4 cursor-pointer"
                    />
                    <span className="text-xs text-[#241814] font-medium leading-relaxed">
                      I explicitly confirm that I want to submit this application to the laboratory recruiter and authorize Fitwell to transmit my profile.
                    </span>
                  </label>

                  <div className="flex justify-between pt-3">
                    <button
                      type="button"
                      onClick={() => setModalStep(3)}
                      className="px-4 py-2 rounded-xl bg-[#EFE7D8] text-[#241814] font-semibold text-xs"
                    >
                      ← Back
                    </button>
                    <button
                      type="button"
                      onClick={handleConfirmFitwellApply}
                      disabled={!explicitConfirmed || submittingInternal}
                      className={`px-6 py-2.5 rounded-xl font-bold text-xs transition shadow-md ${
                        explicitConfirmed && !submittingInternal
                          ? 'bg-[#5B1421] hover:bg-[#791E2D] text-white cursor-pointer'
                          : 'bg-[#C5AE90] text-white/70 cursor-not-allowed'
                      }`}
                    >
                      {submittingInternal ? 'Submitting...' : 'Confirm & Submit Application'}
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </PageShell>
  );
};
