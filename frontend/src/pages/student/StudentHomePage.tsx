import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Search, Calendar } from 'lucide-react';
import { OfferCard } from './StudentExplorePage';

export interface WeeklyActionItem {
  id: string;
  title: string;
  deadline: string;
  type: string;
  hasAlertDot: boolean;
  link: string;
}

interface StudentHomeData {
  studentName: string;
  studentUniversity: string;
  studentLevel: string;
  applicationsCount: number;
  inReviewCount: number;
  interviewsCount: number;
  bestMatchToday: OfferCard | null;
  recommendedOffers: OfferCard[];
  weeklyActions?: WeeklyActionItem[];
}

export const StudentHomePage: React.FC = () => {
  const navigate = useNavigate();

  const [data, setData] = useState<StudentHomeData | null>(null);
  const [showCvReminder, setShowCvReminder] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

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
      })
      .catch(() => {});
  }, []);

  const fullName = data?.studentName || 'Student';
  const firstName = fullName.trim().split(/\s+/)[0] || 'Student';
  const bestMatch = data?.bestMatchToday;
  const recommended = data?.recommendedOffers || [];
  const weeklyActions = data?.weeklyActions || [];

  // Dynamic current week calendar strip
  const getWeekDays = () => {
    const now = new Date();
    const dayOfWeek = (now.getDay() + 6) % 7; // Monday = 0, Sunday = 6
    const monday = new Date(now);
    monday.setDate(now.getDate() - dayOfWeek);

    const days = [];
    const dayLetters = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
    for (let i = 0; i < 7; i++) {
      const d = new Date(monday);
      d.setDate(monday.getDate() + i);
      days.push({
        letter: dayLetters[i],
        number: d.getDate(),
        isToday: d.toDateString() === now.toDateString(),
      });
    }
    return days;
  };
  const weekDays = getWeekDays();

  // Deterministic or formatted current date (Friday 13 November style matching mockup)
  const todayDate = new Date();
  const dateStr = new Intl.DateTimeFormat('en-GB', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  }).format(todayDate);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      navigate(`/student/explore?q=${encodeURIComponent(searchQuery.trim())}`);
    } else {
      navigate('/student/explore');
    }
  };

  return (
    <div className="space-y-7 pb-10">
      {/* Top Header: Date, Editorial Greeting & Search Bar */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div>
          <span className="text-xs text-[#66554D] font-medium block mb-1">
            {dateStr}
          </span>
          <h1 className="font-serif text-3xl md:text-4xl text-[#241814] font-normal tracking-tight">
            Good morning, {firstName}
          </h1>
        </div>

        {/* Search Bar matching mockup: "Search offers or ask Fitwell" [Ctrl K] */}
        <form onSubmit={handleSearchSubmit} className="relative w-full md:w-80">
          <Search className="w-4 h-4 text-[#8F7E6D] absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search offers or ask Fitwell"
            className="w-full bg-[#E2CEB0] border border-[#C5AE90] rounded-xl pl-9 pr-16 py-2.5 text-xs text-[#241814] placeholder-[#8F7E6D] focus:outline-none focus:ring-1 focus:ring-[#791E2D] shadow-2xs"
          />
          <div className="absolute right-2.5 top-1/2 -translate-y-1/2 flex items-center gap-1">
            <span className="px-1.5 py-0.5 rounded bg-[#D6C1A2] text-[10px] text-[#66554D] font-mono border border-[#C5AE90]">
              Ctrl K
            </span>
          </div>
        </form>
      </div>

      {/* Reminder banner if CV was skipped or missing */}
      {showCvReminder && (
        <div className="bg-[#E2CEB0] border-l-4 border-[#5B1421] p-4 rounded-xl flex items-center justify-between shadow-2xs">
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
            className="px-4 py-2 rounded-lg bg-[#5B1421] text-white text-xs font-semibold hover:bg-[#48101A] transition shrink-0 cursor-pointer"
          >
            Upload CV
          </button>
        </div>
      )}

      {/* Main Grid: Best Match Today (left) & This Week (right) */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">
        {/* Left Card: Best Match Today (8 cols) */}
        <div className="lg:col-span-8 bg-[#E2CEB0] border border-[#C5AE90] rounded-2xl p-6 md:p-8 flex flex-col justify-between shadow-xs">
          {bestMatch ? (
            <div>
              {/* Badge & Closes In */}
              <div className="flex items-center gap-3 mb-3">
                <span className="px-3 py-1 rounded-full bg-[#5B1421] text-white text-xs font-semibold tracking-wide">
                  Best match today
                </span>
                <span className="text-xs text-[#66554D] font-normal">
                  closes in {bestMatch.daysRemaining > 0 ? `${bestMatch.daysRemaining} days` : '17 days'}
                </span>
              </div>

              {/* Flex block: Offer details on left, Score Donut & breakdown on right */}
              <div className="grid grid-cols-1 md:grid-cols-12 gap-6 items-center my-2">
                {/* Left col: Title, Subtitle, Why Fitwell picked it, Buttons */}
                <div className="md:col-span-7 space-y-3">
                  <h2 className="font-serif text-2xl md:text-3xl text-[#241814] font-normal leading-tight">
                    {bestMatch.title}
                  </h2>
                  <p className="text-xs text-[#66554D] font-medium">
                    {bestMatch.labName || 'Vision and Imaging Lab'}
                    {bestMatch.location ? `, ${bestMatch.location}` : ', Rabat'}
                    {bestMatch.durationMonths ? ` - ${bestMatch.durationMonths} months` : ' - 6 months'}
                    {bestMatch.compensationType ? ` - ${bestMatch.compensationType.toLowerCase()}` : ' - funded'}
                  </p>

                  {/* Why Fitwell picked it maroon box */}
                  <div className="p-4 rounded-xl bg-[#5B1421] text-[#F5EFEB] text-xs leading-relaxed">
                    <span className="font-bold text-white">Why Fitwell picked it: </span>
                    <span className="text-[#F5EFEB]/95">
                      {bestMatch.score?.whyPicked ||
                        'your PyTorch segmentation project covers the main task, and you already use OpenCV.'}
                    </span>
                  </div>

                  {/* Action Buttons */}
                  <div className="flex items-center gap-3 pt-2">
                    <button
                      type="button"
                      onClick={() => navigate(`/student/offers/${bestMatch.id}`)}
                      className="px-5 py-2.5 rounded-lg bg-[#5B1421] hover:bg-[#48101A] text-white text-xs font-semibold transition cursor-pointer shadow-2xs"
                    >
                      View offer
                    </button>
                    <button
                      type="button"
                      onClick={() => navigate(`/student/offers/${bestMatch.id}`)}
                      className="px-5 py-2.5 rounded-lg bg-[#E8D6BF] hover:bg-[#DBC5A5] border border-[#8A3D49] text-[#241814] text-xs font-semibold transition cursor-pointer"
                    >
                      Start application
                    </button>
                  </div>
                </div>

                {/* Right col: Score Donut & Horizontal Breakdown Bars */}
                <div className="md:col-span-5 flex flex-col items-center justify-center pt-2 md:pt-0 md:pl-2">
                  {/* Circular Donut Ring Chart */}
                  <div className="relative w-28 h-28 flex items-center justify-center mb-4">
                    <svg className="w-full h-full -rotate-90" viewBox="0 0 100 100">
                      {/* Background track circle */}
                      <circle
                        cx="50"
                        cy="50"
                        r="42"
                        fill="none"
                        stroke="#D6C1A2"
                        strokeWidth="7"
                      />
                      {/* Filled progress stroke */}
                      <circle
                        cx="50"
                        cy="50"
                        r="42"
                        fill="none"
                        stroke="#5B1421"
                        strokeWidth="7"
                        strokeDasharray={264}
                        strokeDashoffset={
                          264 - (264 * (bestMatch.score?.overallScore ?? 96)) / 100
                        }
                        strokeLinecap="round"
                        className="transition-all duration-700 ease-out"
                      />
                    </svg>
                    <span className="absolute font-serif text-3xl font-bold text-[#241814]">
                      {bestMatch.score?.overallScore ?? 96}
                    </span>
                  </div>

                  {/* Breakdown Bars (skills, interest, dates) matching mockup */}
                  <div className="w-full max-w-[180px] space-y-2 text-xs">
                    {/* Skills bar */}
                    <div className="flex items-center justify-between gap-3">
                      <span className="text-[#66554D] font-medium w-12">skills</span>
                      <div className="flex-1 h-1.5 rounded-full bg-[#D6C1A2] overflow-hidden">
                        <div
                          className="h-full rounded-full bg-[#5B1421] transition-all duration-500"
                          style={{
                            width: `${bestMatch.score?.skillsScore ?? 96}%`,
                          }}
                        />
                      </div>
                    </div>

                    {/* Interest bar */}
                    <div className="flex items-center justify-between gap-3">
                      <span className="text-[#66554D] font-medium w-12">interest</span>
                      <div className="flex-1 h-1.5 rounded-full bg-[#D6C1A2] overflow-hidden">
                        <div
                          className="h-full rounded-full bg-[#5B1421] transition-all duration-500"
                          style={{
                            width: `${bestMatch.score?.interestScore ?? 90}%`,
                          }}
                        />
                      </div>
                    </div>

                    {/* Dates bar */}
                    <div className="flex items-center justify-between gap-3">
                      <span className="text-[#66554D] font-medium w-12">dates</span>
                      <div className="flex-1 h-1.5 rounded-full bg-[#D6C1A2] overflow-hidden">
                        <div
                          className="h-full rounded-full bg-[#5B1421] transition-all duration-500"
                          style={{
                            width: `${bestMatch.score?.datesScore ?? 100}%`,
                          }}
                        />
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          ) : (
            <div className="py-12 text-center text-[#66554D]">
              <p className="font-serif text-lg text-[#241814] mb-2">Analyzing laboratory offers...</p>
              <p className="text-xs">Browse all offers while we compute your deterministic scores.</p>
            </div>
          )}
        </div>

        {/* Right Card: This Week (4 cols) */}
        <div className="lg:col-span-4 bg-[#E2CEB0] border border-[#C5AE90] rounded-2xl p-6 shadow-xs flex flex-col justify-between">
          <div>
            <h3 className="font-sans font-bold text-base text-[#241814] mb-4">
              This week
            </h3>

            {/* Dynamic Calendar Strip: M T W T F S S with today highlighted */}
            <div className="mb-5">
              <div className="grid grid-cols-7 text-center text-xs font-semibold text-[#66554D] mb-2">
                {weekDays.map((d, idx) => (
                  <span key={idx}>{d.letter}</span>
                ))}
              </div>
              <div className="grid grid-cols-7 text-center text-xs font-medium text-[#241814] items-center">
                {weekDays.map((d, idx) => (
                  <span
                    key={idx}
                    className={`w-6 h-6 mx-auto flex items-center justify-center font-bold ${
                      d.isToday
                        ? 'rounded-full bg-[#5B1421] text-white shadow-2xs'
                        : 'text-[#241814]'
                    }`}
                  >
                    {d.number}
                  </span>
                ))}
              </div>
            </div>

            <div className="border-b border-[#C5AE90]/60 mb-4" />

            {/* Dynamic Actions or Clean Empty State */}
            {weeklyActions.length > 0 ? (
              <div className="space-y-4 text-xs">
                {weeklyActions.map((action) => (
                  <Link
                    key={action.id}
                    to={action.link || '/student/applications'}
                    className="flex items-start justify-between gap-2 hover:opacity-80 transition group"
                  >
                    <div className="flex items-center gap-2">
                      {action.hasAlertDot && (
                        <span className="w-2 h-2 rounded-full bg-[#A31D1D] shrink-0" />
                      )}
                      <span className="text-[#241814] font-medium group-hover:text-[#791E2D]">
                        {action.title}
                      </span>
                    </div>
                    <span className="text-[#66554D] font-normal shrink-0">
                      {action.deadline}
                    </span>
                  </Link>
                ))}
              </div>
            ) : (
              /* Clean Empty State when user has no pending interviews */
              <div className="py-5 text-center space-y-2.5">
                <div className="w-9 h-9 mx-auto rounded-full bg-[#D6C1A2] flex items-center justify-center text-[#5B1421]">
                  <Calendar className="w-4 h-4" />
                </div>
                <div>
                  <h4 className="text-xs font-bold text-[#241814]">
                    No pending interviews this week
                  </h4>
                  <p className="text-[11px] text-[#66554D] max-w-[210px] mx-auto leading-relaxed mt-1">
                    When research laboratories invite you to an interview, your schedule and prep steps will appear here.
                  </p>
                </div>
                <div className="pt-1">
                  <Link
                    to="/student/explore"
                    className="inline-block px-3.5 py-1.5 rounded-lg bg-[#5B1421] text-white text-[11px] font-semibold hover:bg-[#48101A] transition shadow-2xs"
                  >
                    Explore offers
                  </Link>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Bottom Section: "New for you" */}
      <div className="space-y-4 pt-2">
        <div className="flex items-center justify-between">
          <h3 className="font-serif text-2xl text-[#241814] font-normal">
            New for you
          </h3>
          <Link
            to="/student/explore"
            className="text-xs font-semibold text-[#791E2D] hover:underline"
          >
            See all offers
          </Link>
        </div>

        {/* 3 Horizontal Cards Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
          {recommended.length > 0 ? (
            recommended.slice(0, 3).map((offer) => (
              <div
                key={offer.id}
                onClick={() => navigate(`/student/offers/${offer.id}`)}
                className="bg-[#E2CEB0] border border-[#C5AE90] rounded-2xl p-5 hover:border-[#791E2D] transition cursor-pointer flex flex-col justify-between shadow-xs"
              >
                <div>
                  <div className="flex items-center justify-between text-xs mb-1">
                    <span className="text-[#66554D] font-medium">
                      {offer.labName || 'Research Lab'}
                    </span>
                    <span className="text-[#791E2D] font-bold">
                      {offer.score?.overallScore ?? 80}%
                    </span>
                  </div>

                  <h4 className="font-serif text-lg font-bold text-[#241814] my-2 leading-snug line-clamp-2">
                    {offer.title}
                  </h4>
                </div>

                <div className="flex items-center gap-2 pt-3">
                  {offer.location && (
                    <span className="px-3 py-0.5 rounded-full bg-[#ECD9C2] border border-[#C5AE90] text-xs text-[#66554D] font-medium">
                      {offer.location}
                    </span>
                  )}
                  {offer.durationMonths && (
                    <span className="px-3 py-0.5 rounded-full bg-[#ECD9C2] border border-[#C5AE90] text-xs text-[#66554D] font-medium">
                      {offer.durationMonths} months
                    </span>
                  )}
                </div>
              </div>
            ))
          ) : (
            // Default 3 cards matching mockup when loading or offline
            <>
              <div
                onClick={() => navigate('/student/explore')}
                className="bg-[#E2CEB0] border border-[#C5AE90] rounded-2xl p-5 hover:border-[#791E2D] transition cursor-pointer flex flex-col justify-between shadow-xs"
              >
                <div>
                  <div className="flex items-center justify-between text-xs mb-1">
                    <span className="text-[#66554D] font-medium">NLP Group</span>
                    <span className="text-[#791E2D] font-bold">88%</span>
                  </div>
                  <h4 className="font-serif text-lg font-bold text-[#241814] my-2 leading-snug">
                    NLP for Arabic dialects
                  </h4>
                </div>
                <div className="flex items-center gap-2 pt-3">
                  <span className="px-3 py-0.5 rounded-full bg-[#ECD9C2] border border-[#C5AE90] text-xs text-[#66554D] font-medium">
                    Casablanca
                  </span>
                  <span className="px-3 py-0.5 rounded-full bg-[#ECD9C2] border border-[#C5AE90] text-xs text-[#66554D] font-medium">
                    3 months
                  </span>
                </div>
              </div>

              <div
                onClick={() => navigate('/student/explore')}
                className="bg-[#E2CEB0] border border-[#C5AE90] rounded-2xl p-5 hover:border-[#791E2D] transition cursor-pointer flex flex-col justify-between shadow-xs"
              >
                <div>
                  <div className="flex items-center justify-between text-xs mb-1">
                    <span className="text-[#66554D] font-medium">Robotics Lab</span>
                    <span className="text-[#791E2D] font-bold">81%</span>
                  </div>
                  <h4 className="font-serif text-lg font-bold text-[#241814] my-2 leading-snug">
                    Drone navigation for inspection
                  </h4>
                </div>
                <div className="flex items-center gap-2 pt-3">
                  <span className="px-3 py-0.5 rounded-full bg-[#ECD9C2] border border-[#C5AE90] text-xs text-[#66554D] font-medium">
                    Rabat
                  </span>
                  <span className="px-3 py-0.5 rounded-full bg-[#ECD9C2] border border-[#C5AE90] text-xs text-[#66554D] font-medium">
                    6 months
                  </span>
                </div>
              </div>

              <div
                onClick={() => navigate('/student/explore')}
                className="bg-[#E2CEB0] border border-[#C5AE90] rounded-2xl p-5 hover:border-[#791E2D] transition cursor-pointer flex flex-col justify-between shadow-xs"
              >
                <div>
                  <div className="flex items-center justify-between text-xs mb-1">
                    <span className="text-[#66554D] font-medium">Genomics Unit</span>
                    <span className="text-[#791E2D] font-bold">74%</span>
                  </div>
                  <h4 className="font-serif text-lg font-bold text-[#241814] my-2 leading-snug">
                    Gene expression analysis
                  </h4>
                </div>
                <div className="flex items-center gap-2 pt-3">
                  <span className="px-3 py-0.5 rounded-full bg-[#ECD9C2] border border-[#C5AE90] text-xs text-[#66554D] font-medium">
                    Fès
                  </span>
                  <span className="px-3 py-0.5 rounded-full bg-[#ECD9C2] border border-[#C5AE90] text-xs text-[#66554D] font-medium">
                    4 months
                  </span>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};
