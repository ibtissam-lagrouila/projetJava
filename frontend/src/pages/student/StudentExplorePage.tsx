import React, { useState, useEffect, useTransition } from 'react';
import { useNavigate } from 'react-router-dom';
import { PageShell } from '../../components/common/PageShell';

export interface SkillItem {
  id: string;
  name: string;
  normalizedName: string;
  isRequired: boolean;
}

export interface GapSkillItem {
  name: string;
  isRequired: boolean;
  advice: string;
}

export interface ScoreBreakdown {
  overallScore: number;
  matchLabel: string;
  skillsScore: number;
  interestScore: number;
  datesScore: number;
  whyPicked: string;
  matchedSkills: string[];
  gapToClose: GapSkillItem[];
}

export interface OfferCard {
  id: string;
  labId: string;
  labName: string;
  labUniversity: string;
  labCity: string;
  title: string;
  domain: string;
  durationMonths: number;
  startDate: string | null;
  location: string;
  compensationType: string;
  compensationAmount: string | null;
  description: string;
  tasks: string[];
  places: number;
  status: string;
  origin: string;
  closesAt: string | null;
  daysRemaining: number;
  skills: SkillItem[];
  score: ScoreBreakdown;
}

export interface QueryPill {
  label: string;
  type: string;
}

export interface ParsedQuery {
  rawQuery: string;
  city: string | null;
  minDurationMonths: number | null;
  maxDurationMonths: number | null;
  durationLabel: string | null;
  domains: string[];
  skills: string[];
  compensation: string;
  pills: QueryPill[];
}

export const StudentExplorePage: React.FC = () => {
  const navigate = useNavigate();
  const [, startTransition] = useTransition();

  const [query, setQuery] = useState('I want to work on medical images in Rabat for about four months');
  const [cityFilter, setCityFilter] = useState('all');
  const [domainFilter, setDomainFilter] = useState('all');
  const [durationFilter, setDurationFilter] = useState('all');
  const [minScoreFilter, setMinScoreFilter] = useState('0');

  const [offers, setOffers] = useState<OfferCard[]>([]);
  const [parsedQuery, setParsedQuery] = useState<ParsedQuery | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchOffers = async (currentQuery?: string) => {
    setLoading(true);
    setError(null);
    try {
      const q = currentQuery !== undefined ? currentQuery : query;
      const params = new URLSearchParams();
      if (q.trim()) params.append('query', q.trim());
      if (cityFilter !== 'all') params.append('city', cityFilter);
      if (domainFilter !== 'all') params.append('domain', domainFilter);
      if (durationFilter !== 'all') params.append('duration', durationFilter);
      if (minScoreFilter !== '0') params.append('minScore', minScoreFilter);

      const res = await fetch(`/api/student/offers?${params.toString()}`, {
        credentials: 'include',
      });
      if (!res.ok) {
        throw new Error('Failed to load offers from server');
      }
      const data = await res.json();
      setOffers(data.offers || []);
      if (data.parsedQuery) {
        setParsedQuery(data.parsedQuery);
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Error loading offers');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOffers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cityFilter, domainFilter, durationFilter, minScoreFilter]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    fetchOffers(query);
  };

  const handleExampleClick = (example: string) => {
    setQuery(example);
    fetchOffers(example);
  };

  const removePill = (pillToRemove: QueryPill) => {
    if (pillToRemove.type === 'city') {
      setCityFilter('all');
    } else if (pillToRemove.type === 'domain') {
      setDomainFilter('all');
    } else if (pillToRemove.type === 'duration') {
      setDurationFilter('all');
    }
    // Re-fetch without this filter
    setTimeout(() => fetchOffers(), 50);
  };

  return (
    <PageShell
      title="Tell Fitwell what you want"
      subtitle="Explore research internships matched to your profile with natural language query parsing"
      phase="Phase 3 (Explore & Score Engine)"
      pdfPage={4}
      badgeLabel="Deterministic Matching Active"
    >
      <div className="space-y-6">
        {/* Natural Language Query Box - Styled as in Mockup Page 4 */}
        <div className="p-5 rounded-2xl bg-[#5B1421] text-white shadow-sm border border-[#48101A]">
          <form onSubmit={handleSearchSubmit} className="space-y-3">
            <div className="flex flex-col sm:flex-row gap-2">
              <input
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Describe your research interests in plain language (e.g. medical imaging in Rabat for 4 months)..."
                className="flex-1 px-4 py-2.5 rounded-xl bg-[#DFCAAC] text-[#241814] text-sm font-medium placeholder-[#66554D] border border-[#C5AE90] focus:outline-none focus:ring-2 focus:ring-[#791E2D]"
              />
              <button
                type="submit"
                disabled={loading}
                className="px-6 py-2.5 rounded-xl bg-[#791E2D] hover:bg-[#631724] text-white text-sm font-semibold transition"
              >
                {loading ? 'Analyzing...' : 'Parse & Match'}
              </button>
            </div>
          </form>

          {/* Understood as pills - Mockup Page 4 exact style */}
          {parsedQuery && parsedQuery.pills && parsedQuery.pills.length > 0 && (
            <div className="mt-4 pt-3 border-t border-white/10 flex flex-wrap items-center gap-2">
              <span className="text-xs text-[#EFE7D8]/70 font-medium">understood as:</span>
              {parsedQuery.pills.map((pill, idx) => (
                <span
                  key={idx}
                  className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#E2CEB0] text-[#5B1421] text-xs font-semibold shadow-xs"
                >
                  <span>{pill.label}</span>
                  <button
                    type="button"
                    onClick={() => removePill(pill)}
                    className="hover:text-red-700 font-bold ml-1 text-xs"
                    title="Remove filter"
                  >
                    ×
                  </button>
                </span>
              ))}
            </div>
          )}

          {/* Query suggestions */}
          <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-[#EFE7D8]/80">
            <span>Try asking:</span>
            <button
              type="button"
              onClick={() => handleExampleClick('I want to work on medical images in Rabat for about four months')}
              className="underline hover:text-white"
            >
              Medical images in Rabat (4 months)
            </button>
            <span>•</span>
            <button
              type="button"
              onClick={() => handleExampleClick('Bioinformatique à Fès 4 mois rémunéré')}
              className="underline hover:text-white"
            >
              Bioinformatique à Fès (4 mois)
            </button>
            <span>•</span>
            <button
              type="button"
              onClick={() => handleExampleClick('Deep learning in Casablanca 3 to 6 months')}
              className="underline hover:text-white"
            >
              Deep learning in Casablanca
            </button>
          </div>
        </div>

        {/* Filter Controls Bar */}
        <div className="bg-[#E2CEB0] p-4 rounded-xl border border-[#C5AE90] flex flex-wrap items-center justify-between gap-4">
          <div className="flex flex-wrap items-center gap-3 text-xs">
            <div className="flex items-center gap-1.5">
              <span className="font-semibold text-[#241814]">City:</span>
              <select
                value={cityFilter}
                onChange={(e) => setCityFilter(e.target.value)}
                className="px-2.5 py-1.5 rounded-lg bg-[#DFCAAC] border border-[#C5AE90] text-[#241814] font-medium"
              >
                <option value="all">All cities</option>
                <option value="Rabat">Rabat</option>
                <option value="Casablanca">Casablanca</option>
                <option value="Fès">Fès</option>
                <option value="Marrakech">Marrakech</option>
                <option value="Tangier">Tangier</option>
                <option value="Oujda">Oujda</option>
              </select>
            </div>

            <div className="flex items-center gap-1.5">
              <span className="font-semibold text-[#241814]">Domain:</span>
              <select
                value={domainFilter}
                onChange={(e) => setDomainFilter(e.target.value)}
                className="px-2.5 py-1.5 rounded-lg bg-[#DFCAAC] border border-[#C5AE90] text-[#241814] font-medium"
              >
                <option value="all">All domains</option>
                <option value="Medical imaging">Medical imaging</option>
                <option value="Computer Vision">Computer Vision</option>
                <option value="Bioinformatics">Bioinformatics</option>
                <option value="Genomics">Genomics</option>
                <option value="Natural Language Processing">NLP</option>
                <option value="Machine Learning">Machine Learning</option>
              </select>
            </div>

            <div className="flex items-center gap-1.5">
              <span className="font-semibold text-[#241814]">Duration:</span>
              <select
                value={durationFilter}
                onChange={(e) => setDurationFilter(e.target.value)}
                className="px-2.5 py-1.5 rounded-lg bg-[#DFCAAC] border border-[#C5AE90] text-[#241814] font-medium"
              >
                <option value="all">Any duration</option>
                <option value="3">3 months</option>
                <option value="4">4 months</option>
                <option value="5">5 months</option>
                <option value="6">6 months</option>
              </select>
            </div>

            <div className="flex items-center gap-1.5">
              <span className="font-semibold text-[#241814]">Min Fit:</span>
              <select
                value={minScoreFilter}
                onChange={(e) => setMinScoreFilter(e.target.value)}
                className="px-2.5 py-1.5 rounded-lg bg-[#DFCAAC] border border-[#C5AE90] text-[#241814] font-medium"
              >
                <option value="0">All matches</option>
                <option value="60">60%+ Good</option>
                <option value="75">75%+ Strong</option>
                <option value="90">90%+ Excellent</option>
              </select>
            </div>
          </div>

          <div className="text-xs font-semibold text-[#66554D]">
            {offers.length} research offer{offers.length !== 1 ? 's' : ''} found
          </div>
        </div>

        {/* Error state */}
        {error && (
          <div className="p-4 rounded-xl bg-red-100 text-red-800 text-sm border border-red-200">
            {error}
          </div>
        )}

        {/* Loading state */}
        {loading && offers.length === 0 && (
          <div className="p-8 text-center text-[#66554D] font-medium">
            Computing deterministic compatibility scores for research internships...
          </div>
        )}

        {/* Offers Feed Grid */}
        <div className="grid grid-cols-1 gap-6">
          {offers.map((offer) => {
            const scoreVal = offer.score?.overallScore ?? 0;
            const scoreLabel = offer.score?.matchLabel ?? 'Calculated match';

            return (
              <div
                key={offer.id}
                className="bg-[#E2CEB0] p-6 rounded-2xl border border-[#C5AE90] shadow-sm hover:border-[#5B1421] transition flex flex-col justify-between"
              >
                <div>
                  {/* Top Bar: Match Badge + Closes In */}
                  <div className="flex items-center justify-between mb-3">
                    <div className="flex items-center gap-2">
                      <span className="px-3 py-1 rounded-full bg-[#5B1421] text-white text-xs font-semibold flex items-center gap-1.5">
                        <span className="font-serif text-sm font-bold">{scoreVal}%</span>
                        <span>{scoreLabel}</span>
                      </span>
                      {offer.compensationType && (
                        <span className="px-2.5 py-0.5 rounded-full bg-[#DFCAAC] text-[#5B1421] text-xs font-medium border border-[#C5AE90]">
                          {offer.compensationType}
                        </span>
                      )}
                    </div>
                    {offer.daysRemaining > 0 ? (
                      <span className="text-xs text-[#66554D]">
                        closes in {offer.daysRemaining} days
                      </span>
                    ) : (
                      <span className="text-xs text-[#66554D]">open now</span>
                    )}
                  </div>

                  {/* Offer Title & Laboratory */}
                  <h2 className="font-serif text-2xl font-bold text-[#241814] mb-1">
                    {offer.title}
                  </h2>
                  <p className="text-xs text-[#66554D] mb-4">
                    {offer.labName || 'Research Laboratory'}
                    {offer.labUniversity ? ` • ${offer.labUniversity}` : ''}
                    {offer.location ? ` • ${offer.location}` : ''}
                    {offer.durationMonths ? ` • ${offer.durationMonths} months` : ''}
                    {offer.places ? ` • ${offer.places} places` : ''}
                  </p>

                  {/* Why Fitwell picked it - Mockup Page 3 & 4 */}
                  {offer.score?.whyPicked && (
                    <div className="p-3.5 rounded-xl bg-[#5B1421] text-[#F5EFEB] text-xs leading-relaxed mb-4">
                      <span className="font-semibold text-white">Why Fitwell picked it: </span>
                      <span>{offer.score.whyPicked}</span>
                    </div>
                  )}

                  {/* Skills match & Gap to close chips */}
                  <div className="space-y-2 mb-4">
                    {offer.score?.matchedSkills && offer.score.matchedSkills.length > 0 && (
                      <div className="flex flex-wrap items-center gap-1.5">
                        <span className="text-xs text-[#66554D] font-medium mr-1">Skills covered:</span>
                        {offer.score.matchedSkills.map((s, idx) => (
                          <span
                            key={idx}
                            className="px-2.5 py-0.5 rounded-full bg-[#DFCAAC] text-[#5B1421] text-xs font-semibold"
                          >
                            ✓ {s}
                          </span>
                        ))}
                      </div>
                    )}

                    {offer.score?.gapToClose && offer.score.gapToClose.length > 0 && (
                      <div className="flex flex-wrap items-center gap-1.5">
                        <span className="text-xs text-[#66554D] font-medium mr-1">Gap to close:</span>
                        {offer.score.gapToClose.map((g, idx) => (
                          <span
                            key={idx}
                            title={g.advice}
                            className="px-2.5 py-0.5 rounded-full border border-[#791E2D] text-[#791E2D] text-xs font-semibold bg-[#E2CEB0]"
                          >
                            {g.name} {g.isRequired ? '(Required)' : '(Optional)'}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                </div>

                {/* Bottom Actions */}
                <div className="pt-4 border-t border-[#C5AE90]/60 flex items-center justify-between">
                  <div className="text-xs text-[#66554D] italic">
                    Pure SE deterministic score • zero demographic bias
                  </div>
                  <div className="flex items-center gap-3">
                    <button
                      type="button"
                      onClick={() => startTransition(() => navigate(`/student/offers/${offer.id}`))}
                      className="px-4 py-2 rounded-xl bg-[#5B1421] text-white text-xs font-semibold hover:bg-[#48101A] transition"
                    >
                      View offer & Score breakdown
                    </button>
                    <button
                      type="button"
                      onClick={() => startTransition(() => navigate(`/student/offers/${offer.id}`))}
                      className="px-4 py-2 rounded-xl bg-[#E2CEB0] border border-[#791E2D] text-[#791E2D] text-xs font-semibold hover:bg-[#D6C1A2] transition"
                    >
                      Start application
                    </button>
                  </div>
                </div>
              </div>
            );
          })}

          {/* Empty State */}
          {!loading && offers.length === 0 && (
            <div className="bg-[#E2CEB0] p-10 rounded-2xl border border-[#C5AE90] text-center space-y-3">
              <h3 className="font-serif text-xl font-bold text-[#241814]">
                No matching internships found
              </h3>
              <p className="text-xs text-[#66554D] max-w-md mx-auto">
                No published offers match your current filter combination. Try selecting &quot;All cities&quot; or resetting your search query.
              </p>
              <button
                type="button"
                onClick={() => {
                  setCityFilter('all');
                  setDomainFilter('all');
                  setDurationFilter('all');
                  setMinScoreFilter('0');
                  setQuery('');
                  fetchOffers('');
                }}
                className="px-4 py-2 rounded-xl bg-[#5B1421] text-white text-xs font-semibold hover:bg-[#48101A]"
              >
                Reset all filters
              </button>
            </div>
          )}
        </div>
      </div>
    </PageShell>
  );
};
