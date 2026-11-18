import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { PageShell } from '../../components/common/PageShell';
import { api } from '../../services/api';
import { KanbanCandidateItem, LabCandidatesResponse, LabOfferSummary } from '../../types';
import { 
  Sparkles, 
  ChevronRight, 
  FileText, 
  GraduationCap, 
  ArrowUpDown, 
  RefreshCw,
  AlertCircle
} from 'lucide-react';

export const LabCandidatesPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const offerIdParam = searchParams.get('offerId') || '';

  const [data, setData] = useState<LabCandidatesResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Sorting: 'fit' (deterministic Rank by fit) vs 'date' (most recent appliedAt)
  const [sortBy, setSortBy] = useState<'fit' | 'date'>('fit');
  const [updatingId, setUpdatingId] = useState<string | null>(null);
  const [selectedCandidate, setSelectedCandidate] = useState<KanbanCandidateItem | null>(null);

  const fetchCandidates = async (offerId?: string) => {
    try {
      setLoading(true);
      setError(null);
      const res: LabCandidatesResponse = await api.getCandidates(offerId || undefined);
      setData(res);
    } catch (err: any) {
      setError(err?.message || 'Failed to load candidates from server');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCandidates(offerIdParam);
  }, [offerIdParam]);

  const handleOfferChange = (newOfferId: string) => {
    if (newOfferId === 'all') {
      searchParams.delete('offerId');
    } else {
      searchParams.set('offerId', newOfferId);
    }
    setSearchParams(searchParams);
  };

  const handleStatusChange = async (candidateId: string, newStatus: string) => {
    try {
      setUpdatingId(candidateId);
      await api.updateApplicationStatus(candidateId, newStatus);
      // Refresh candidates list
      await fetchCandidates(data?.selectedOfferId || offerIdParam);
    } catch (err: any) {
      alert(err?.message || 'Failed to update candidate status');
    } finally {
      setUpdatingId(null);
    }
  };

  const sortItems = (items: KanbanCandidateItem[]) => {
    const list = [...items];
    if (sortBy === 'fit') {
      return list.sort((a, b) => (b.matchScore || 0) - (a.matchScore || 0));
    }
    return list.sort((a, b) => new Date(b.appliedAt).getTime() - new Date(a.appliedAt).getTime());
  };

  const columnsConfig = [
    {
      key: 'new' as const,
      title: 'New',
      items: data?.columns?.new || [],
      count: data?.counts?.new || 0,
    },
    {
      key: 'shortlisted' as const,
      title: 'Shortlisted',
      items: data?.columns?.shortlisted || [],
      count: data?.counts?.shortlisted || 0,
    },
    {
      key: 'interview' as const,
      title: 'Interview',
      items: data?.columns?.interview || [],
      count: data?.counts?.interview || 0,
    },
    {
      key: 'decision' as const,
      title: 'Decision',
      items: data?.columns?.decision || [],
      count: data?.counts?.decision || 0,
    },
  ];

  return (
    <PageShell
      title="Candidates"
      subtitle="Kanban board of candidates with deterministic 'Rank by fit' scoring"
      phase="Phase 5 (Candidates & Kanban)"
      pdfPage={13}
      badgeLabel="Kanban Active"
    >
      {/* Top Header section matching design mockup */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6">
        <div>
          <div className="text-xs uppercase tracking-wider text-[#66554D] font-medium mb-1">
            Candidates
          </div>
          <div className="flex items-center gap-3">
            <h1 className="font-serif text-3xl font-bold text-[#241814]">
              {data?.selectedOfferTitle || 'Candidates Kanban'}
            </h1>

            {/* Offer Selector if multiple offers exist */}
            {data?.offers && data.offers.length > 1 && (
              <select
                value={data.selectedOfferId || 'all'}
                onChange={(e) => handleOfferChange(e.target.value)}
                className="bg-[#EFE5D5] border border-[#C5AE90] text-[#241814] text-xs font-semibold rounded-lg px-2.5 py-1.5 focus:outline-none focus:ring-1 focus:ring-[#791E2D]"
              >
                <option value="all">All offers ({data.counts.total})</option>
                {data.offers.map((off: LabOfferSummary) => (
                  <option key={off.offerId} value={off.offerId}>
                    {off.title} ({off.candidateCount})
                  </option>
                ))}
              </select>
            )}
          </div>
        </div>

        {/* Actions on Top-Right */}
        <div className="flex items-center gap-3">
          <button
            onClick={() => setSortBy(sortBy === 'fit' ? 'date' : 'fit')}
            className={`px-4 py-2 rounded-full text-xs font-semibold transition shadow-sm flex items-center gap-2 ${
              sortBy === 'fit'
                ? 'bg-[#791E2D] hover:bg-[#631824] text-white'
                : 'bg-[#DFCAAC] hover:bg-[#D5BF9F] text-[#241814]'
            }`}
            title="Sort cards deterministically by Match % or Applied Date"
          >
            <Sparkles className="w-3.5 h-3.5" />
            <span>{sortBy === 'fit' ? 'Rank by fit' : 'Sort by date'}</span>
            <ArrowUpDown className="w-3 h-3 opacity-70 ml-1" />
          </button>

          <button
            onClick={() => fetchCandidates(data?.selectedOfferId || offerIdParam)}
            className="p-2 rounded-full bg-[#DFCAAC] hover:bg-[#D5BF9F] text-[#241814] transition"
            title="Refresh candidates"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* Error state */}
      {error && (
        <div className="mb-6 p-4 bg-red-100 border border-red-300 rounded-xl text-red-800 text-xs flex items-center gap-2">
          <AlertCircle className="w-4 h-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Loading skeleton */}
      {loading && !data && (
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4 animate-pulse">
          {[1, 2, 3, 4].map((col) => (
            <div key={col} className="bg-[#DFCAAC] h-96 rounded-2xl p-4">
              <div className="h-4 bg-[#C5AE90] rounded w-1/3 mb-4"></div>
              <div className="space-y-3">
                <div className="h-24 bg-[#E2CEB0] rounded-xl"></div>
                <div className="h-24 bg-[#E2CEB0] rounded-xl"></div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* 4-Column Kanban Board */}
      {data && (
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4 items-start">
          {columnsConfig.map((col) => {
            const sortedItems = sortItems(col.items);

            return (
              <div
                key={col.key}
                className="bg-[#DFCAAC] p-3.5 rounded-2xl border border-[#C5AE90] flex flex-col gap-3 min-h-[560px] shadow-sm"
              >
                {/* Column Header */}
                <div className="flex justify-between items-center px-1 font-bold text-xs text-[#241814]">
                  <span className="font-serif text-sm">{col.title}</span>
                  <span className="bg-[#EAE0D1] text-[#791E2D] px-2 py-0.5 rounded-full text-xs font-bold border border-[#C5AE90]">
                    {col.count}
                  </span>
                </div>

                {/* Cards Container */}
                <div className="flex flex-col gap-2.5">
                  {sortedItems.length === 0 ? (
                    <div className="p-4 border border-dashed border-[#C5AE90] rounded-xl text-center text-[11px] text-[#66554D] bg-[#EAE0D1]/50 mt-2">
                      No candidates in this stage
                    </div>
                  ) : (
                    sortedItems.map((c) => (
                      <div
                        key={c.id}
                        className="bg-[#EFE5D5] p-3.5 rounded-xl border border-[#C5AE90] shadow-sm hover:border-[#791E2D] hover:shadow-md transition flex flex-col gap-2 cursor-pointer group"
                        onClick={() => setSelectedCandidate(c)}
                      >
                        {/* Candidate Name & Match percentage */}
                        <div className="flex justify-between items-start gap-2">
                          <div className="font-bold text-xs text-[#241814] group-hover:text-[#791E2D] transition line-clamp-1">
                            {c.studentName}
                          </div>
                          <div className="text-xs font-bold text-[#791E2D] flex-shrink-0">
                            {c.matchScore}%
                          </div>
                        </div>

                        {/* Education / Field */}
                        <div className="text-[11px] text-[#66554D] line-clamp-1">
                          {c.level}{c.fieldOfStudy ? `, ${c.fieldOfStudy}` : ''}
                        </div>

                        {/* Skill Badges */}
                        {c.skills && c.skills.length > 0 && (
                          <div className="flex flex-wrap gap-1.5 pt-0.5">
                            {c.skills.slice(0, 4).map((sk, idx) => (
                              <span
                                key={idx}
                                className="px-2 py-0.5 rounded-full text-[10px] font-medium bg-[#DFCAAC] text-[#241814] border border-[#C5AE90]"
                              >
                                {sk}
                              </span>
                            ))}
                            {c.skills.length > 4 && (
                              <span className="text-[10px] text-[#66554D] self-center">
                                +{c.skills.length - 4}
                              </span>
                            )}
                          </div>
                        )}

                        {/* Quick Kanban Action Buttons */}
                        <div
                          className="pt-2 mt-1 border-t border-[#DFCAAC] flex items-center justify-between gap-1 text-[10px]"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <span className="text-[10px] text-[#806B62] font-mono">
                            {new Date(c.appliedAt).toLocaleDateString()}
                          </span>

                          <div className="flex items-center gap-1">
                            {col.key === 'new' && (
                              <button
                                disabled={updatingId === c.id}
                                onClick={() => handleStatusChange(c.id, 'SHORTLISTED')}
                                className="px-2 py-0.5 rounded bg-[#791E2D] hover:bg-[#631824] text-white font-medium transition flex items-center gap-0.5"
                                title="Move to Shortlisted"
                              >
                                <span>Shortlist</span>
                                <ChevronRight className="w-2.5 h-2.5" />
                              </button>
                            )}

                            {col.key === 'shortlisted' && (
                              <button
                                disabled={updatingId === c.id}
                                onClick={() => handleStatusChange(c.id, 'INTERVIEW')}
                                className="px-2 py-0.5 rounded bg-[#791E2D] hover:bg-[#631824] text-white font-medium transition flex items-center gap-0.5"
                                title="Invite to Interview"
                              >
                                <span>Interview</span>
                                <ChevronRight className="w-2.5 h-2.5" />
                              </button>
                            )}

                            {col.key === 'interview' && (
                              <div className="flex items-center gap-1">
                                <button
                                  disabled={updatingId === c.id}
                                  onClick={() => handleStatusChange(c.id, 'DECISION_ACCEPTED')}
                                  className="px-1.5 py-0.5 rounded bg-emerald-800 hover:bg-emerald-900 text-white font-medium transition"
                                  title="Accept Candidate"
                                >
                                  Accept
                                </button>
                                <button
                                  disabled={updatingId === c.id}
                                  onClick={() => handleStatusChange(c.id, 'DECISION_REJECTED')}
                                  className="px-1.5 py-0.5 rounded bg-[#806B62] hover:bg-[#631824] text-white font-medium transition"
                                  title="Reject Candidate"
                                >
                                  Reject
                                </button>
                              </div>
                            )}

                            {col.key === 'decision' && (
                              <span className={`px-1.5 py-0.5 rounded font-semibold text-[9px] ${
                                c.status === 'DECISION_ACCEPTED'
                                  ? 'bg-emerald-100 text-emerald-800 border border-emerald-300'
                                  : 'bg-red-100 text-red-800 border border-red-300'
                              }`}>
                                {c.status === 'DECISION_ACCEPTED' ? 'ACCEPTED' : 'REJECTED'}
                              </span>
                            )}
                          </div>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Candidate Details Slide-out / Modal */}
      {selectedCandidate && (
        <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-[#FAF6F0] border border-[#C5AE90] rounded-2xl max-w-lg w-full p-6 shadow-2xl flex flex-col gap-4 animate-in fade-in zoom-in-95 duration-150">
            {/* Modal Header */}
            <div className="flex justify-between items-start border-b border-[#DFCAAC] pb-3">
              <div>
                <h3 className="font-serif text-xl font-bold text-[#241814]">
                  {selectedCandidate.studentName}
                </h3>
                <div className="text-xs text-[#66554D] flex items-center gap-2 mt-0.5">
                  <GraduationCap className="w-3.5 h-3.5" />
                  <span>{selectedCandidate.level}</span>
                  <span>•</span>
                  <span>{selectedCandidate.university}</span>
                </div>
              </div>
              <div className="flex flex-col items-end">
                <span className="text-lg font-bold text-[#791E2D]">
                  {selectedCandidate.matchScore}%
                </span>
                <span className="text-[10px] text-[#806B62] uppercase tracking-wider font-semibold">
                  Match fit
                </span>
              </div>
            </div>

            {/* Offer applied to */}
            <div className="bg-[#EFE5D5] p-3 rounded-xl border border-[#DFCAAC] text-xs">
              <span className="font-semibold text-[#241814]">Offer: </span>
              <span className="text-[#66554D]">{selectedCandidate.offerTitle}</span>
            </div>

            {/* Skills */}
            <div>
              <div className="text-xs font-bold text-[#241814] mb-1.5">
                Skills & Technologies:
              </div>
              <div className="flex flex-wrap gap-1.5">
                {selectedCandidate.skills.map((sk, idx) => (
                  <span
                    key={idx}
                    className="px-2.5 py-1 rounded-full text-xs font-medium bg-[#DFCAAC] text-[#241814] border border-[#C5AE90]"
                  >
                    {sk}
                  </span>
                ))}
              </div>
            </div>

            {/* Cover letter / Statement */}
            <div>
              <div className="text-xs font-bold text-[#241814] mb-1 flex items-center gap-1.5">
                <FileText className="w-3.5 h-3.5 text-[#791E2D]" />
                <span>Application Message:</span>
              </div>
              <div className="p-3 bg-[#EAE0D1] rounded-xl border border-[#DFCAAC] text-xs text-[#4A3B32] leading-relaxed max-h-36 overflow-y-auto">
                {selectedCandidate.coverLetter || 'No cover letter provided.'}
              </div>
            </div>

            {/* Pipeline Stage Transitions */}
            <div className="pt-2 border-t border-[#DFCAAC] flex items-center justify-between">
              <div className="text-xs text-[#66554D]">
                Stage: <span className="font-bold text-[#241814]">{selectedCandidate.status}</span>
              </div>

              <div className="flex items-center gap-2">
                {selectedCandidate.status !== 'SHORTLISTED' && (
                  <button
                    onClick={() => {
                      handleStatusChange(selectedCandidate.id, 'SHORTLISTED');
                      setSelectedCandidate(null);
                    }}
                    className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-[#DFCAAC] hover:bg-[#D5BF9F] text-[#241814] transition"
                  >
                    Move to Shortlisted
                  </button>
                )}
                {selectedCandidate.status !== 'INTERVIEW' && (
                  <button
                    onClick={() => {
                      handleStatusChange(selectedCandidate.id, 'INTERVIEW');
                      setSelectedCandidate(null);
                    }}
                    className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-[#791E2D] hover:bg-[#631824] text-white transition"
                  >
                    Invite to Interview
                  </button>
                )}
                <button
                  onClick={() => setSelectedCandidate(null)}
                  className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-gray-200 hover:bg-gray-300 text-gray-800 transition"
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </PageShell>
  );
};
