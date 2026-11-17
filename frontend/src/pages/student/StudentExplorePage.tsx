import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

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
  sourceName?: string | null;
  sourceUrl?: string | null;
  closesAt: string | null;
  daysRemaining: number;
  skills: SkillItem[];
  score: ScoreBreakdown;
  displayDate?: string;
  isMayBeClosed?: boolean;
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
  const [searchParams, setSearchParams] = useSearchParams();

  const initialQuery =
    searchParams.get('q') ||
    'I want to work on medical images in Rabat for about four months';

  const [query, setQuery] = useState(initialQuery);
  const [sortBy, setSortBy] = useState<'bestMatch' | 'closesSoon'>('bestMatch');
  const [offers, setOffers] = useState<OfferCard[]>([]);
  const [parsedQuery, setParsedQuery] = useState<ParsedQuery | null>(null);
  const [externalSourcesConfigured, setExternalSourcesConfigured] = useState<boolean>(true);
  const [externalSourcesMissingReason, setExternalSourcesMissingReason] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchOffers = async (searchQuery: string, sortOrder: string) => {
    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams();
      if (searchQuery.trim()) {
        params.append('query', searchQuery.trim());
      }
      params.append('sort', sortOrder);

      const res = await fetch(`/api/student/offers?${params.toString()}`, {
        credentials: 'include',
      });
      if (!res.ok) {
        throw new Error('Failed to load offers from server');
      }
      const data = await res.json();
      setOffers(data.offers || []);
      if (typeof data.externalSourcesConfigured === 'boolean') {
        setExternalSourcesConfigured(data.externalSourcesConfigured);
      }
      if (data.externalSourcesMissingReason) {
        setExternalSourcesMissingReason(data.externalSourcesMissingReason);
      }
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
    fetchOffers(query, sortBy);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sortBy]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setSearchParams(query.trim() ? { q: query.trim() } : {});
    fetchOffers(query, sortBy);
  };

  const removePill = (pillToRemove: QueryPill) => {
    if (!parsedQuery) return;
    const remainingPills = parsedQuery.pills.filter(
      (p) => p.label !== pillToRemove.label
    );
    setParsedQuery({
      ...parsedQuery,
      pills: remainingPills,
    });

    let updatedQuery = query;
    const re = new RegExp(`\\b${pillToRemove.label}\\b`, 'gi');
    updatedQuery = updatedQuery.replace(re, '').replace(/\s{2,}/g, ' ').trim();
    setQuery(updatedQuery);
    fetchOffers(updatedQuery, sortBy);
  };

  const handleExampleClick = (example: string) => {
    setQuery(example);
    setSearchParams({ q: example });
    fetchOffers(example, sortBy);
  };

  const linkedInSearchUrl = `https://www.linkedin.com/jobs/search/?keywords=${encodeURIComponent(
    query.trim() || 'internship morocco'
  )}`;

  return (
    <div className="space-y-6 pb-12 max-w-6xl mx-auto">
      {/* Top Breadcrumb & Actions */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <span className="text-xs font-normal text-[#66554D] tracking-wide">
            Explore
          </span>
          <h1 className="font-serif text-3xl md:text-4xl text-[#241814] font-normal tracking-tight mt-1">
            Tell Fitwell what you want
          </h1>
        </div>

        {/* Live Search on LinkedIn link (Always explicit search in new tab, never fake card) */}
        <div>
          <a
            href={linkedInSearchUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-[#0A66C2] hover:bg-[#084e96] text-white text-xs font-semibold shadow-xs transition"
          >
            <svg className="w-3.5 h-3.5 fill-current" viewBox="0 0 24 24">
              <path d="M19 3a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h14m-.5 15.5v-5.3a3.26 3.26 0 0 0-3.26-3.26c-.85 0-1.84.52-2.28 1.3v-1.11h-2.79v8.37h2.79v-4.93c0-.77.62-1.4 1.39-1.4a1.4 1.4 0 0 1 1.4 1.4v4.93h2.75M6.46 8.76a1.64 1.64 0 1 0 0-3.28 1.64 1.64 0 0 0 0 3.28m1.4 9.74v-8.37H5.06v8.37h2.8z" />
            </svg>
            <span>Search on LinkedIn</span>
            <span className="text-white/80 text-xs">↗</span>
          </a>
        </div>
      </div>

      {/* External source status banner if not connected */}
      {!externalSourcesConfigured && (
        <div className="p-4 rounded-xl bg-[#FBF3E4] border border-[#E2CEB0] text-[#78350F] text-xs flex flex-col sm:flex-row sm:items-center justify-between gap-3 shadow-2xs">
          <div className="flex items-start gap-2.5">
            <span className="text-base leading-none">ℹ️</span>
            <div>
              <span className="font-bold">No live external source connected. </span>
              <span className="text-[#92400E]">
                {externalSourcesMissingReason || 'JOOBLE_API_KEY is not configured and no approved CareerPageSource is active.'}
                {' '}Displaying verified internal laboratory opportunities.
              </span>
            </div>
          </div>
          <a
            href={linkedInSearchUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="px-3.5 py-1.5 rounded-lg bg-[#0A66C2] text-white font-semibold text-xs shrink-0 hover:bg-[#084e96] inline-flex items-center gap-1.5 self-start sm:self-auto"
          >
            <span>Search on LinkedIn</span>
            <span>↗</span>
          </a>
        </div>
      )}

      {/* Dark Maroon Prompt Box */}
      <div className="p-6 md:p-7 rounded-2xl bg-[#5B1421] text-white shadow-sm border border-[#48101A]">
        <form onSubmit={handleSearchSubmit} className="space-y-3">
          <div className="relative">
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Tell Fitwell what you want (e.g. medical images in Rabat for about four months)..."
              className="w-full bg-transparent text-[#F5EFEB] text-base md:text-lg font-normal placeholder-[#DFCAAC]/50 focus:outline-none border-b border-white/15 pb-3 transition"
            />
          </div>
        </form>

        {/* Parsed Pills inside Maroon Box */}
        {parsedQuery && parsedQuery.pills && parsedQuery.pills.length > 0 && (
          <div className="mt-4 flex flex-wrap items-center gap-2.5">
            {parsedQuery.pills.map((pill, idx) => (
              <span
                key={idx}
                className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-full bg-[#FDFBF7] text-[#5B1421] text-xs font-medium shadow-xs"
              >
                <span>{pill.label}</span>
                <button
                  type="button"
                  onClick={() => removePill(pill)}
                  className="hover:text-red-700 font-bold ml-1 text-xs text-[#5B1421]/60 leading-none"
                  title="Remove filter"
                >
                  ×
                </button>
              </span>
            ))}
          </div>
        )}

        {/* Realistic Research Suggestion Prompts */}
        <div className="mt-4 pt-3 border-t border-white/10 flex flex-wrap items-center gap-2 text-xs text-[#EFE7D8]/75">
          <span className="font-medium text-[#DFCAAC]">Search suggestions:</span>
          <button
            type="button"
            onClick={() =>
              handleExampleClick(
                'Stage Full Stack React et Spring Boot à Casablanca'
              )
            }
            className="underline hover:text-white transition font-medium text-[#DFCAAC]"
          >
            Mifactory (Full Stack React)
          </button>
          <span>•</span>
          <button
            type="button"
            onClick={() =>
              handleExampleClick(
                'Stage Recherche IA et TALN à Kénitra Université Ibn Tofail'
              )
            }
            className="underline hover:text-white transition font-medium text-[#DFCAAC]"
          >
            Univ. Ibn Tofail (IA & TALN)
          </button>
          <span>•</span>
          <button
            type="button"
            onClick={() =>
              handleExampleClick(
                'Stage Computer Vision et Deep Learning AgriTech à Rabat DeepLeaf'
              )
            }
            className="underline hover:text-white transition font-medium text-[#DFCAAC]"
          >
            DeepLeaf (AgriTech AI)
          </button>
          <span>•</span>
          <button
            type="button"
            onClick={() =>
              handleExampleClick(
                'I want to work on medical images in Rabat for about four months'
              )
            }
            className="underline hover:text-white transition font-medium text-[#DFCAAC]"
          >
            Medical Imaging Rabat (4 months)
          </button>
        </div>
      </div>

      {/* Subheader Bar: Offers Count + Sort By Dropdown */}
      <div className="flex items-center justify-between pt-1">
        <div className="flex items-center gap-2.5">
          <span className="text-xs font-normal text-[#66554D]">
            {offers.length} offer{offers.length !== 1 ? 's' : ''} found
          </span>
          {parsedQuery && parsedQuery.domains && parsedQuery.domains.some((d) => d.toLowerCase().includes('machine learning') || d.toLowerCase().includes('ia') || d.toLowerCase().includes('ai')) && (
            <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold bg-[#5B1421]/15 text-[#5B1421] border border-[#5B1421]/20">
              AI Matching
            </span>
          )}
        </div>

        <div className="flex items-center gap-2 text-xs text-[#66554D]">
          <span>Sort by</span>
          <select
            value={sortBy}
            onChange={(e) =>
              setSortBy(e.target.value as 'bestMatch' | 'closesSoon')
            }
            className="bg-[#DFCAAC] border border-[#C5AE90] rounded-xl px-3 py-1.5 text-xs font-semibold text-[#241814] focus:outline-none focus:ring-1 focus:ring-[#791E2D] cursor-pointer shadow-2xs"
          >
            <option value="bestMatch">Best match</option>
            <option value="closesSoon">Closes soonest</option>
          </select>
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
        <div className="p-12 text-center text-[#66554D] font-serif text-base">
          Finding matching research and internship offers...
        </div>
      )}

      {/* Empty State with Helpful Suggestions */}
      {!loading && offers.length === 0 && (
        <div className="bg-[#DFCAAC]/40 border border-[#C5AE90] p-8 md:p-12 rounded-2xl text-center space-y-4">
          <div className="w-12 h-12 rounded-full bg-[#5B1421]/10 text-[#5B1421] flex items-center justify-center mx-auto text-xl">
            🔍
          </div>
          <h2 className="font-serif text-xl font-bold text-[#241814]">
            No matching offers found
          </h2>
          <p className="text-xs text-[#66554D] max-w-lg mx-auto leading-relaxed">
            We could not find active offers matching all your filters. Try widening your criteria to see more research and internship opportunities:
          </p>
          <div className="flex flex-wrap items-center justify-center gap-2 pt-2 text-xs">
            <button
              type="button"
              onClick={() => handleExampleClick('internship')}
              className="px-3 py-1.5 rounded-lg bg-[#5B1421] text-white font-medium hover:bg-[#791E2D] transition"
            >
              Show all available offers
            </button>
            <a
              href={linkedInSearchUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="px-3 py-1.5 rounded-lg bg-[#0A66C2] text-white font-medium hover:bg-[#084e96] transition inline-flex items-center gap-1.5"
            >
              <span>Search "{query}" on LinkedIn</span>
              <span>↗</span>
            </a>
          </div>
        </div>
      )}

      {/* Grid of Offer Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
        {offers.map((offer) => {
          const scoreVal = offer.score?.overallScore ?? 0;
          const isDemo = offer.origin === 'DEMO';
          const isExternal = offer.origin === 'SOURCED';
          const isMayBeClosed = offer.isMayBeClosed || offer.status === 'MAY_BE_CLOSED';

          return (
            <div
              key={offer.id}
              onClick={() => navigate(`/student/offers/${offer.id}`)}
              className={`p-6 rounded-2xl border transition duration-200 cursor-pointer flex flex-col justify-between ${isMayBeClosed
                ? 'bg-[#EAE4DC] border-amber-300 opacity-80'
                : 'bg-[#DFCAAC] border-[#C5AE90] hover:border-[#5B1421] hover:shadow-md'
                }`}
            >
              <div>
                {/* Top row: Source Badge + Company + Match Score */}
                <div className="flex items-start justify-between gap-3 mb-2">
                  <div className="flex flex-col gap-1.5 max-w-[75%]">
                    <div className="flex items-center gap-1.5 flex-wrap">
                      {isDemo ? (
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-md text-[10px] font-bold bg-[#854D0E]/20 text-[#854D0E] border border-[#854D0E]/40 shadow-2xs">
                          DEMO · fictional
                        </span>
                      ) : isExternal ? (
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-md text-[10px] font-bold bg-[#1E3A8A]/10 text-[#1E3A8A] border border-[#1E3A8A]/30 shadow-2xs truncate max-w-[180px]">
                          via {offer.sourceName || 'External source'}
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-md text-[10px] font-semibold bg-[#5B1421]/10 text-[#5B1421] border border-[#5B1421]/20">
                          {offer.sourceName || 'Internal Lab'}
                        </span>
                      )}

                      {isMayBeClosed && (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10px] font-bold bg-amber-200 text-amber-900 border border-amber-400">
                          This offer may be closed
                        </span>
                      )}
                    </div>

                    <span className="text-xs text-[#66554D] font-medium leading-relaxed truncate">
                      {offer.labName || 'Research Laboratory'}
                    </span>
                  </div>

                  <span className="font-serif text-3xl font-normal text-[#791E2D] leading-none shrink-0">
                    {scoreVal}
                  </span>
                </div>

                {/* Offer Title */}
                <h2 className="font-serif font-bold text-base md:text-lg text-[#241814] leading-snug mt-2 mb-4 line-clamp-2">
                  {offer.title}
                </h2>

                {/* Attribute Pills (Location, Duration, Compensation) */}
                <div className="flex flex-wrap items-center gap-1.5 mb-3">
                  {offer.location && (
                    <span className="rounded-full px-2.5 py-1 text-xs bg-[#EFE7D8] text-[#241814] border border-[#C5AE90]/60 font-medium">
                      {offer.location}
                    </span>
                  )}
                  {offer.durationMonths > 0 && (
                    <span className="rounded-full px-2.5 py-1 text-xs bg-[#EFE7D8] text-[#241814] border border-[#C5AE90]/60 font-medium">
                      {offer.durationMonths} months
                    </span>
                  )}
                  {offer.compensationType && (
                    <span
                      className={`rounded-full px-2.5 py-1 text-xs border font-medium ${offer.compensationType === 'Funded' ||
                        offer.compensationType.toLowerCase().includes('remun') ||
                        offer.compensationType.toLowerCase().includes('rémun')
                        ? 'bg-[#5B1421]/10 text-[#5B1421] border-[#5B1421]/30 font-semibold'
                        : 'bg-[#EFE7D8] text-[#241814] border-[#C5AE90]/60'
                        }`}
                    >
                      {offer.compensationAmount || offer.compensationType}
                    </span>
                  )}
                </div>

                {/* Matching Skills row */}
                {offer.score?.matchedSkills && offer.score.matchedSkills.length > 0 && (
                  <div className="text-xs text-[#241814] mb-4 flex items-center gap-1.5 flex-wrap">
                    <span className="text-[#66554D] font-medium">Matching skills:</span>
                    <span className="font-semibold text-[#5B1421]">
                      {offer.score.matchedSkills.join(', ')}.
                    </span>
                  </div>
                )}
              </div>

              {/* Bottom Footer Row: Source + Date with Year + Details Arrow */}
              <div className="flex items-center justify-between text-xs text-[#66554D] pt-3 border-t border-[#C5AE90]/50 mt-auto">
                <div className="flex items-center gap-1.5 truncate max-w-[70%]">
                  <span className="font-normal text-xs text-[#66554D]">
                    {isExternal ? `via ${offer.sourceName || 'External source'}` : 'Posted by the lab'}
                  </span>
                  <span>•</span>
                  <span className="font-normal text-xs text-[#66554D]">
                    {offer.displayDate || (offer.closesAt ? `closes ${new Date(offer.closesAt).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })} ${new Date(offer.closesAt).getFullYear()}` : 'Open')}
                  </span>
                </div>

                <span className="font-semibold text-xs text-[#5B1421] inline-flex items-center gap-1 shrink-0">
                  View details & apply →
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
