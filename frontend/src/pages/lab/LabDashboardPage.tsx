import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { 
  Users, 
  Clock, 
  HelpCircle, 
  CheckCircle, 
  Building2, 
  Eye, 
  Calendar,
  X,
  Lock
} from 'lucide-react';
import { api } from '../../services/api';
import { LabDashboardData, TopCandidateItem, SuggestionItem } from '../../types';

export const LabDashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<LabDashboardData | null>(null);
  const [selectedLabId, setSelectedLabId] = useState<string | undefined>(undefined);

  // Modals & Drawers state
  const [activeTooltipId, setActiveTooltipId] = useState<string | null>(null);
  const [selectedCandidate, setSelectedCandidate] = useState<TopCandidateItem | null>(null);
  const [shortlistModalSuggestion, setShortlistModalSuggestion] = useState<SuggestionItem | null>(null);
  const [shortlistConfirmed, setShortlistConfirmed] = useState<boolean>(false);
  const [shortlistSubmitting, setShortlistSubmitting] = useState<boolean>(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const fetchDashboard = async (labId?: string) => {
    setLoading(true);
    setError(null);
    try {
      const resp = await api.getLabDashboard(labId);
      if (!resp) {
        setError('Failed to load lab dashboard. Please try again.');
      } else {
        setData(resp);
        if (!selectedLabId && resp.activeLabId) {
          setSelectedLabId(resp.activeLabId);
        }
      }
    } catch (err: any) {
      setError(err?.message || 'Unexpected error occurred while fetching dashboard');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboard(selectedLabId);
  }, [selectedLabId]);

  const handleSwitchLab = (newLabId: string) => {
    setSelectedLabId(newLabId);
  };

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 4000);
  };

  const handleExecuteShortlist = async () => {
    if (!shortlistModalSuggestion || !shortlistConfirmed) return;
    setShortlistSubmitting(true);
    try {
      const appIds = shortlistModalSuggestion.candidateIds || [];
      const res = await api.shortlistCandidates(appIds, true, selectedLabId);
      if (res) {
        showToast(`Successfully shortlisted ${res.shortlistedCount} candidate(s)!`);
        setShortlistModalSuggestion(null);
        setShortlistConfirmed(false);
        fetchDashboard(selectedLabId);
      } else {
        alert('Failed to shortlist candidates. Please try again.');
      }
    } catch {
      alert('Network error while shortlisting candidates.');
    } finally {
      setShortlistSubmitting(false);
    }
  };

  if (loading && !data) {
    return (
      <div className="min-h-screen bg-[#F5EFEB] p-8 flex items-center justify-center">
        <div className="flex flex-col items-center gap-3">
          <div className="w-8 h-8 border-4 border-[#791E2D] border-t-transparent rounded-full animate-spin"></div>
          <p className="text-sm text-[#66554D] font-medium">Loading laboratory dashboard...</p>
        </div>
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="min-h-screen bg-[#F5EFEB] p-8 flex items-center justify-center">
        <div className="max-w-md w-full bg-[#DFCAAC] border border-[#C5AE90] p-6 rounded-2xl text-center shadow-sm">
          <Building2 className="w-12 h-12 text-[#791E2D] mx-auto mb-3" />
          <h2 className="font-serif text-xl font-bold text-[#241814] mb-2">Access Notice</h2>
          <p className="text-sm text-[#66554D] mb-5">{error || 'Dashboard data is unavailable'}</p>
          <button
            onClick={() => fetchDashboard(selectedLabId)}
            className="px-5 py-2.5 bg-[#791E2D] text-white rounded-lg text-sm font-semibold hover:bg-[#601622] transition-colors"
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

  // Handle Pending or Rejected state
  if (data.status === 'PENDING') {
    return (
      <div className="min-h-screen bg-[#F5EFEB] p-8 flex items-center justify-center">
        <div className="max-w-lg w-full bg-[#EFE3D3] border border-[#D5C0A7] p-8 rounded-3xl text-center shadow-sm">
          <Clock className="w-12 h-12 text-[#B3782E] mx-auto mb-4" />
          <h1 className="font-serif text-2xl font-bold text-[#241814] mb-2">Verification Pending</h1>
          <p className="text-sm text-[#5B4C46] leading-relaxed mb-6">
            Your laboratory profile for <strong className="text-[#241814]">{data.labName}</strong> is currently pending review by the Fitwell university administration.
            You will receive full access to publish offers and review candidate profiles as soon as verification is confirmed.
          </p>
          <div className="p-3 bg-[#DFCAAC]/60 rounded-xl text-xs text-[#66554D] mb-6">
            City: {data.city} · Role: {data.role}
          </div>
          <button
            onClick={() => navigate('/login')}
            className="px-5 py-2.5 bg-[#791E2D] text-white rounded-xl text-sm font-semibold hover:bg-[#601622] transition-colors"
          >
            Sign in with another account
          </button>
        </div>
      </div>
    );
  }

  if (data.status === 'REJECTED') {
    return (
      <div className="min-h-screen bg-[#F5EFEB] p-8 flex items-center justify-center">
        <div className="max-w-lg w-full bg-[#EFE3D3] border border-[#D5C0A7] p-8 rounded-3xl text-center shadow-sm">
          <Lock className="w-12 h-12 text-[#791E2D] mx-auto mb-4" />
          <h1 className="font-serif text-2xl font-bold text-[#241814] mb-2">Laboratory Access Rejected</h1>
          <p className="text-sm text-[#5B4C46] leading-relaxed mb-6">
            The registration or claim request for <strong className="text-[#241814]">{data.labName}</strong> was not approved by the administration.
          </p>
          <button
            onClick={() => navigate('/login')}
            className="px-5 py-2.5 bg-[#791E2D] text-white rounded-xl text-sm font-semibold hover:bg-[#601622] transition-colors"
          >
            Back to login
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#F5EFEB] text-[#241814] p-6 lg:p-10 select-none font-sans">
      {/* Toast Notification */}
      {toastMessage && (
        <div 
          aria-live="polite"
          className="fixed top-6 right-6 z-50 bg-[#791E2D] text-white px-5 py-3 rounded-xl shadow-lg flex items-center gap-2.5 text-sm font-medium animate-in fade-in slide-in-from-top-4"
        >
          <CheckCircle className="w-4 h-4 text-[#E2CEB0]" />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* Top Header & Lab Switcher */}
      <header className="mb-8 flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div>
          <span className="text-xs uppercase tracking-wider text-[#8A756D] font-semibold">Dashboard</span>
          <h1 className="font-serif text-3xl md:text-4xl font-bold text-[#241814] mt-1 tracking-tight">
            {data.greeting}
          </h1>
          <p className="text-sm text-[#66554D] mt-1">
            {data.labName}, {data.city} · <span className="font-medium text-[#791E2D]">{data.role}</span>
          </p>
        </div>

        {/* Multi-lab selector if user belongs to more than 1 lab */}
        {data.availableLabs && data.availableLabs.length > 1 && (
          <div className="flex items-center gap-2 bg-[#E7D6C1] p-1.5 rounded-xl border border-[#D5C0A7]">
            <Building2 className="w-4 h-4 text-[#791E2D] ml-2" />
            <label htmlFor="lab-select" className="text-xs font-semibold text-[#66554D] sr-only">Active Laboratory</label>
            <select
              id="lab-select"
              value={selectedLabId}
              onChange={(e) => handleSwitchLab(e.target.value)}
              className="bg-transparent text-sm font-semibold text-[#241814] focus:outline-none pr-3 py-1 cursor-pointer"
            >
              {data.availableLabs.map((lab) => (
                <option key={lab.labId} value={lab.labId}>
                  {lab.labName} ({lab.city})
                </option>
              ))}
            </select>
          </div>
        )}
      </header>

      {/* 4 Stat Metric Cards */}
      <section 
        aria-label="Overview statistics"
        aria-live="polite"
        className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8"
      >
        {/* Open offers */}
        <button
          onClick={() => navigate('/lab/post-offer')}
          className="text-left p-5 rounded-2xl bg-[#EADBC8] border border-[#D8C4AC] hover:bg-[#E3D2BE] hover:shadow-sm transition-all focus:outline-none focus:ring-2 focus:ring-[#791E2D]"
        >
          <div className="text-xs font-medium text-[#66554D]">Open offers</div>
          <div className="font-serif text-4xl font-bold text-[#241814] mt-1.5">{data.counters.openOffers}</div>
        </button>

        {/* New applicants */}
        <button
          onClick={() => navigate('/lab/candidates?filter=new')}
          className="text-left p-5 rounded-2xl bg-[#EADBC8] border border-[#D8C4AC] hover:bg-[#E3D2BE] hover:shadow-sm transition-all focus:outline-none focus:ring-2 focus:ring-[#791E2D]"
        >
          <div className="text-xs font-medium text-[#66554D]">New applicants</div>
          <div className="font-serif text-4xl font-bold text-[#241814] mt-1.5">{data.counters.newApplicants}</div>
        </button>

        {/* To review */}
        <button
          onClick={() => navigate('/lab/candidates?status=SENT')}
          className="text-left p-5 rounded-2xl bg-[#EADBC8] border border-[#D8C4AC] hover:bg-[#E3D2BE] hover:shadow-sm transition-all focus:outline-none focus:ring-2 focus:ring-[#791E2D]"
        >
          <div className="text-xs font-medium text-[#66554D]">To review</div>
          <div className="font-serif text-4xl font-bold text-[#791E2D] mt-1.5">{data.counters.toReview}</div>
        </button>

        {/* Interviews this week */}
        <button
          onClick={() => navigate('/lab/interviews')}
          className="text-left p-5 rounded-2xl bg-[#EADBC8] border border-[#D8C4AC] hover:bg-[#E3D2BE] hover:shadow-sm transition-all focus:outline-none focus:ring-2 focus:ring-[#791E2D]"
        >
          <div className="text-xs font-medium text-[#66554D]">Interviews this week</div>
          <div className="font-serif text-4xl font-bold text-[#241814] mt-1.5">{data.counters.interviewsThisWeek}</div>
          <div className="text-[11px] text-[#7A6961] mt-1">
            {data.counters.awaitingConfirmation} awaiting confirmation
          </div>
        </button>
      </section>

      {/* Main Content: 2-Column Split */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        {/* Left Column: Top candidates to review (7 cols) */}
        <section aria-label="Top candidates to review" className="lg:col-span-7">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-serif text-xl font-bold text-[#241814]">
              Top candidates to review
            </h2>
            <button
              onClick={() => navigate('/lab/candidates')}
              className="text-xs font-semibold text-[#791E2D] hover:underline"
            >
              View all
            </button>
          </div>

          {data.topCandidates.length === 0 ? (
            <div className="p-8 rounded-2xl bg-[#EADBC8]/70 border border-[#D8C4AC] text-center text-sm text-[#66554D]">
              <Users className="w-8 h-8 text-[#A89287] mx-auto mb-2" />
              <p className="font-medium text-[#241814]">No applications to review yet</p>
              <p className="text-xs text-[#7A6961] mt-1">When students apply to your open offers, their match scores will appear here.</p>
            </div>
          ) : (
            <div className="space-y-3">
              {data.topCandidates.map((cand) => (
                <div
                  key={cand.applicationId}
                  onClick={() => setSelectedCandidate(cand)}
                  className="group relative p-4 rounded-2xl bg-[#EADBC8] border border-[#D8C4AC] hover:bg-[#E3D2BE] hover:border-[#C5AF94] cursor-pointer transition-all flex items-center justify-between gap-4 shadow-sm"
                >
                  {/* Left: Initials Avatar + Info */}
                  <div className="flex items-center gap-3.5 min-w-0">
                    <div className="w-10 h-10 rounded-full bg-[#5B1421] text-white flex items-center justify-center font-bold text-xs shrink-0 shadow-sm border border-[#791E2D]">
                      {cand.initials}
                    </div>
                    <div className="min-w-0">
                      <div className="font-semibold text-sm text-[#241814] truncate group-hover:text-[#791E2D] transition-colors">
                        {cand.studentName}
                      </div>
                      <div className="text-xs text-[#66554D] truncate">
                        {cand.educationLevel}, {cand.fieldOfStudy} · <span className="font-medium text-[#4A3B35]">{cand.offerTitle}</span>
                      </div>
                    </div>
                  </div>

                  {/* Right: Score Badge & Tooltip Trigger */}
                  <div className="flex items-center gap-2 shrink-0">
                    {cand.isLimitedData ? (
                      <span className="px-2.5 py-1 rounded-full bg-[#D5C2AA] text-[#55453E] text-xs font-semibold">
                        Limited data
                      </span>
                    ) : (
                      <span className="font-serif text-sm font-bold text-[#791E2D]">
                        {cand.matchPercentage}% match
                      </span>
                    )}

                    {/* Info Tooltip */}
                    <button
                      type="button"
                      aria-label="Score breakdown details"
                      onClick={(e) => {
                        e.stopPropagation();
                        setActiveTooltipId(activeTooltipId === cand.applicationId ? null : cand.applicationId);
                      }}
                      className="text-[#8A756D] hover:text-[#241814] p-1 rounded-full hover:bg-[#D5C2AA]/50 transition-colors"
                    >
                      <HelpCircle className="w-4 h-4" />
                    </button>

                    {/* Floating Tooltip Bubble */}
                    {activeTooltipId === cand.applicationId && (
                      <div 
                        onClick={(e) => e.stopPropagation()}
                        className="absolute right-4 top-16 z-30 w-72 p-4 bg-[#241814] text-[#F5EFEB] text-xs rounded-xl shadow-xl border border-[#4A3B35] animate-in fade-in"
                      >
                        <div className="flex items-center justify-between mb-2 pb-1.5 border-b border-[#4A3B35]">
                          <span className="font-bold text-[#E2CEB0]">Deterministic Score</span>
                          <button 
                            onClick={() => setActiveTooltipId(null)}
                            className="text-[#BCA29F] hover:text-white"
                          >
                            <X className="w-3.5 h-3.5" />
                          </button>
                        </div>
                        <div className="space-y-1.5">
                          <div>
                            <span className="text-[#BCA29F]">Matched skills: </span>
                            <span className="font-medium text-emerald-400">
                              {cand.scoreBreakdown.matchedSkills.length > 0 ? cand.scoreBreakdown.matchedSkills.join(', ') : 'None'}
                            </span>
                          </div>
                          {cand.scoreBreakdown.missingSkills.length > 0 && (
                            <div>
                              <span className="text-[#BCA29F]">Missing skills: </span>
                              <span className="font-medium text-rose-300">
                                {cand.scoreBreakdown.missingSkills.join(', ') }
                              </span>
                            </div>
                          )}
                          <div className="text-[11px] text-[#A6938B] pt-1">
                            Skills: {cand.scoreBreakdown.skillsScore}% · Interests: {cand.scoreBreakdown.interestScore}% · Dates: {cand.scoreBreakdown.datesScore}%
                          </div>
                          <div className="text-[11px] text-[#C4B2AA] italic pt-1 border-t border-[#4A3B35]/60">
                            {cand.scoreBreakdown.explanation}
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>

        {/* Right Column: Fitwell suggests & This month (5 cols) */}
        <aside aria-label="Suggestions and monthly statistics" className="lg:col-span-5 space-y-6">
          {/* Fitwell Suggests */}
          <section aria-label="Fitwell suggestions">
            <div className="flex items-center gap-1.5 mb-3 text-xs font-bold uppercase tracking-wider text-[#791E2D]">
              <span>✦</span>
              <span>Fitwell suggests</span>
            </div>

            {data.suggestions.length === 0 ? (
              <div className="p-5 rounded-2xl bg-[#EADBC8] border border-[#D8C4AC] text-sm text-[#66554D] italic">
                Nothing needs your attention
              </div>
            ) : (
              <div className="space-y-3">
                {data.suggestions.map((sug) => (
                  <div
                    key={sug.id}
                    className="p-5 rounded-2xl bg-[#791E2D] text-white shadow-sm border border-[#601622]"
                  >
                    <h3 className="font-serif text-base font-bold text-white mb-1.5 leading-snug">
                      {sug.title}
                    </h3>
                    <p className="text-xs text-[#EADBC8] leading-relaxed mb-4">
                      {sug.description}
                    </p>

                    {sug.actionType === 'MODAL_SHORTLIST' ? (
                      <button
                        onClick={() => {
                          setShortlistModalSuggestion(sug);
                          setShortlistConfirmed(false);
                        }}
                        className="px-4 py-1.5 bg-white text-[#791E2D] rounded-full text-xs font-bold hover:bg-[#F5EFEB] transition-colors"
                      >
                        {sug.actionLabel}
                      </button>
                    ) : (
                      <button
                        onClick={() => sug.targetUrl && navigate(sug.targetUrl)}
                        className="px-4 py-1.5 bg-white text-[#791E2D] rounded-full text-xs font-bold hover:bg-[#F5EFEB] transition-colors"
                      >
                        {sug.actionLabel}
                      </button>
                    )}
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* This Month Activity Bars */}
          <section aria-label="Activity this month" className="p-6 rounded-2xl bg-[#EADBC8] border border-[#D8C4AC]">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-serif text-lg font-bold text-[#241814]">This month</h3>
              <span className="text-[11px] text-[#7A6961]">Calendar month</span>
            </div>

            {data.thisMonth.isAllZero ? (
              <div className="text-center py-6 text-xs text-[#7A6961] italic">
                No activity yet this month
              </div>
            ) : (
              <div className="space-y-3.5">
                {/* Views */}
                <div className="space-y-1">
                  <div className="flex justify-between text-xs text-[#52413A]">
                    <span className="font-medium flex items-center gap-1">
                      <Eye className="w-3.5 h-3.5 text-[#791E2D]" /> Views
                    </span>
                    <span className="font-bold text-[#241814]">{data.thisMonth.views}</span>
                  </div>
                  <div className="w-full bg-[#D5C2AA] h-2 rounded-full overflow-hidden">
                    <div 
                      className="bg-[#791E2D] h-full rounded-full transition-all duration-500" 
                      style={{ width: `${Math.max(4, data.thisMonth.viewsPct)}%` }} 
                    />
                  </div>
                </div>

                {/* Applied */}
                <div className="space-y-1">
                  <div className="flex justify-between text-xs text-[#52413A]">
                    <span className="font-medium flex items-center gap-1">
                      <Users className="w-3.5 h-3.5 text-[#791E2D]" /> Applied
                    </span>
                    <span className="font-bold text-[#241814]">{data.thisMonth.applied}</span>
                  </div>
                  <div className="w-full bg-[#D5C2AA] h-2 rounded-full overflow-hidden">
                    <div 
                      className="bg-[#791E2D] h-full rounded-full transition-all duration-500" 
                      style={{ width: `${Math.max(4, data.thisMonth.appliedPct)}%` }} 
                    />
                  </div>
                </div>

                {/* Shortlisted */}
                <div className="space-y-1">
                  <div className="flex justify-between text-xs text-[#52413A]">
                    <span className="font-medium flex items-center gap-1">
                      <CheckCircle className="w-3.5 h-3.5 text-[#791E2D]" /> Shortlisted
                    </span>
                    <span className="font-bold text-[#241814]">{data.thisMonth.shortlisted}</span>
                  </div>
                  <div className="w-full bg-[#D5C2AA] h-2 rounded-full overflow-hidden">
                    <div 
                      className="bg-[#791E2D] h-full rounded-full transition-all duration-500" 
                      style={{ width: `${Math.max(4, data.thisMonth.shortlistedPct)}%` }} 
                    />
                  </div>
                </div>

                {/* Interviews */}
                <div className="space-y-1">
                  <div className="flex justify-between text-xs text-[#52413A]">
                    <span className="font-medium flex items-center gap-1">
                      <Calendar className="w-3.5 h-3.5 text-[#791E2D]" /> Interviews
                    </span>
                    <span className="font-bold text-[#241814]">{data.thisMonth.interviews}</span>
                  </div>
                  <div className="w-full bg-[#D5C2AA] h-2 rounded-full overflow-hidden">
                    <div 
                      className="bg-[#791E2D] h-full rounded-full transition-all duration-500" 
                      style={{ width: `${Math.max(4, data.thisMonth.interviewsPct)}%` }} 
                    />
                  </div>
                </div>

                <p className="text-[10px] text-[#8C7A72] pt-2 border-t border-[#D5C2AA]/60">
                  Views are deduplicated daily per student. Individual student viewer identities are confidential and never disclosed.
                </p>
              </div>
            )}
          </section>
        </aside>
      </div>

      {/* Candidate Details Modal */}
      {selectedCandidate && (
        <div 
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4"
        >
          <div className="bg-[#F5EFEB] max-w-lg w-full rounded-3xl p-6 shadow-2xl border border-[#D8C4AC] max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between pb-4 border-b border-[#D8C4AC]">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-full bg-[#791E2D] text-white flex items-center justify-center font-bold text-sm">
                  {selectedCandidate.initials}
                </div>
                <div>
                  <h3 className="font-serif text-lg font-bold text-[#241814]">
                    {selectedCandidate.studentName}
                  </h3>
                  <p className="text-xs text-[#66554D]">
                    {selectedCandidate.educationLevel}, {selectedCandidate.fieldOfStudy}
                  </p>
                </div>
              </div>
              <button
                onClick={() => setSelectedCandidate(null)}
                className="p-1.5 text-[#8A756D] hover:text-[#241814] rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="py-4 space-y-4 text-xs">
              <div>
                <span className="text-[#8A756D] uppercase font-bold text-[10px] tracking-wider">Applied for offer</span>
                <p className="text-sm font-semibold text-[#241814] mt-0.5">{selectedCandidate.offerTitle}</p>
              </div>

              <div className="p-3.5 bg-[#EADBC8] rounded-2xl border border-[#D8C4AC]">
                <div className="flex items-center justify-between mb-2">
                  <span className="font-bold text-[#241814] text-xs">Matching Compatibility</span>
                  {selectedCandidate.isLimitedData ? (
                    <span className="px-2 py-0.5 rounded-full bg-[#D5C2AA] text-[#55453E] text-[11px] font-semibold">
                      Limited data
                    </span>
                  ) : (
                    <span className="font-serif text-base font-bold text-[#791E2D]">
                      {selectedCandidate.matchPercentage}% match
                    </span>
                  )}
                </div>
                <div className="space-y-1.5 text-[11px] text-[#52413A]">
                  <div>
                    <strong>Matched skills:</strong>{' '}
                    <span className="text-emerald-700 font-medium">
                      {selectedCandidate.scoreBreakdown.matchedSkills.length > 0 ? selectedCandidate.scoreBreakdown.matchedSkills.join(', ') : 'None'}
                    </span>
                  </div>
                  {selectedCandidate.scoreBreakdown.missingSkills.length > 0 && (
                    <div>
                      <strong>Skills gap:</strong>{' '}
                      <span className="text-rose-700 font-medium">
                        {selectedCandidate.scoreBreakdown.missingSkills.join(', ')}
                      </span>
                    </div>
                  )}
                  <div className="text-[10px] text-[#7A6961] pt-1">
                    Skills: {selectedCandidate.scoreBreakdown.skillsScore}% · Interests: {selectedCandidate.scoreBreakdown.interestScore}% · Dates: {selectedCandidate.scoreBreakdown.datesScore}%
                  </div>
                </div>
              </div>

              <div>
                <span className="text-[#8A756D] uppercase font-bold text-[10px] tracking-wider">Why Fitwell picked it</span>
                <p className="text-xs text-[#52413A] italic mt-1 bg-[#EADBC8]/40 p-2.5 rounded-xl border border-[#D8C4AC]">
                  "{selectedCandidate.scoreBreakdown.explanation}"
                </p>
              </div>
            </div>

            <div className="pt-4 border-t border-[#D8C4AC] flex items-center justify-end gap-2.5">
              <button
                onClick={() => setSelectedCandidate(null)}
                className="px-4 py-2 border border-[#D8C4AC] text-[#241814] rounded-xl text-xs font-semibold hover:bg-[#EADBC8]"
              >
                Close
              </button>
              <button
                onClick={() => {
                  setSelectedCandidate(null);
                  navigate('/lab/candidates?status=SENT');
                }}
                className="px-4 py-2 bg-[#791E2D] text-white rounded-xl text-xs font-semibold hover:bg-[#601622]"
              >
                Go to Candidate Profile
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Shortlist Confirmation Modal */}
      {shortlistModalSuggestion && (
        <div 
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4"
        >
          <div className="bg-[#F5EFEB] max-w-md w-full rounded-3xl p-6 shadow-2xl border border-[#D8C4AC]">
            <div className="flex items-center justify-between pb-3 border-b border-[#D8C4AC]">
              <h3 className="font-serif text-lg font-bold text-[#241814]">
                Confirm Shortlist Invitation
              </h3>
              <button
                onClick={() => setShortlistModalSuggestion(null)}
                className="text-[#8A756D] hover:text-[#241814]"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="py-4 space-y-3 text-xs text-[#52413A]">
              <p>
                You are about to shortlist the following top-matching candidates:
              </p>
              <div className="p-3 bg-[#EADBC8] rounded-xl border border-[#D8C4AC] font-medium text-[#241814] space-y-1">
                {shortlistModalSuggestion.candidateNames?.map((name, i) => (
                  <div key={i} className="flex items-center gap-2">
                    <CheckCircle className="w-3.5 h-3.5 text-[#791E2D]" />
                    <span>{name}</span>
                  </div>
                ))}
              </div>
              <p className="text-[11px] text-[#7A6961]">
                Shortlisting will update their status from <span className="font-bold">SENT</span> to <span className="font-bold">SHORTLISTED</span> and notify them that their application has advanced.
              </p>

              <label className="flex items-start gap-2.5 p-3 rounded-xl bg-[#E2CEB0]/60 border border-[#CBB393] cursor-pointer mt-4">
                <input
                  type="checkbox"
                  checked={shortlistConfirmed}
                  onChange={(e) => setShortlistConfirmed(e.target.checked)}
                  className="mt-0.5 rounded text-[#791E2D] focus:ring-[#791E2D]"
                />
                <span className="text-xs font-semibold text-[#241814]">
                  I explicitly confirm sending shortlist invitations to these candidates.
                </span>
              </label>
            </div>

            <div className="pt-3 border-t border-[#D8C4AC] flex items-center justify-end gap-2.5">
              <button
                onClick={() => setShortlistModalSuggestion(null)}
                className="px-4 py-2 border border-[#D8C4AC] text-[#241814] rounded-xl text-xs font-semibold hover:bg-[#EADBC8]"
              >
                Cancel
              </button>
              <button
                disabled={!shortlistConfirmed || shortlistSubmitting}
                onClick={handleExecuteShortlist}
                className="px-4 py-2 bg-[#791E2D] text-white rounded-xl text-xs font-semibold hover:bg-[#601622] disabled:opacity-50 disabled:cursor-not-allowed transition-all"
              >
                {shortlistSubmitting ? 'Shortlisting...' : 'Send Invitations'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
