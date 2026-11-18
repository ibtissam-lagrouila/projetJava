import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Sparkles,
  CheckCircle,
  Plus,
  ExternalLink,
  AlertCircle
} from 'lucide-react';
import { api } from '../../services/api';

export const LabPostOfferPage: React.FC = () => {
  const navigate = useNavigate();

  // Lab metadata
  const [labName, setLabName] = useState<string>('Genomics Unit');
  const [city, setCity] = useState<string>('Fès');
  const [activeLabId, setActiveLabId] = useState<string | undefined>(undefined);

  // Idea drafting box
  const [ideaText, setIdeaText] = useState<string>(
    'We need a student to analyse RNA-seq data from our mouse cohort and build a small pipeline.'
  );

  // Form fields
  const [title, setTitle] = useState<string>('Gene expression analysis in mouse cohorts');
  const [domain, setDomain] = useState<string>('Biology, bioinformatics');
  const [duration, setDuration] = useState<string>('5 months');
  const [startDate, setStartDate] = useState<string>('February 2027');
  const [location, setLocation] = useState<string>('Fès, on site');
  const [compensation, setCompensation] = useState<string>('Paid');
  const [skills, setSkills] = useState<string[]>([
    'Python',
    'R',
    'Bioconductor',
    'Nextflow',
    'Statistics'
  ]);
  const [newSkill, setNewSkill] = useState<string>('');
  const [tasksText, setTasksText] = useState<string>(
    'Analyse differential gene expression across 24 samples, build a reproducible Nextflow pipeline and present results at the lab meeting.'
  );

  // Submission & Feedback
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [publishedOfferId, setPublishedOfferId] = useState<string | null>(null);
  const [labOffers, setLabOffers] = useState<any[]>([]);

  const fetchLabOffers = (labId?: string) => {
    api.getLabOffers(labId).then(setLabOffers).catch(() => {});
  };

  // Load lab info
  useEffect(() => {
    api.getLabDashboard().then((dash) => {
      if (dash) {
        if (dash.labName) setLabName(dash.labName);
        if (dash.city) setCity(dash.city);
        if (dash.activeLabId) {
          setActiveLabId(dash.activeLabId);
          fetchLabOffers(dash.activeLabId);
        }
      }
    }).catch(() => { });
  }, []);

  // Compute offer quality score dynamically (0 - 100) matching design mockup (92/100 without supervisor photo)
  const computeQualityScore = () => {
    let score = 0;
    if (title.trim().length >= 5) score += 18;
    if (domain.trim().length >= 3) score += 14;
    if (duration.trim().length >= 2) score += 10;
    if (startDate.trim().length >= 3) score += 10;
    if (location.trim().length >= 3) score += 10;
    if (compensation.trim().length >= 2) score += 10;
    if (skills.length >= 3) score += 10;
    else if (skills.length >= 1) score += 5;
    if (tasksText.trim().length >= 30) score += 10;
    else if (tasksText.trim().length >= 10) score += 5;

    return Math.min(score, 100);
  };

  const qualityScore = computeQualityScore();

  const getQualityAdvice = (score: number) => {
    if (score >= 90) return 'Clear goals and skills. Add a supervisor photo.';
    if (score >= 75) return 'Good description. Add more specific required skills to attract top matches.';
    if (score >= 50) return 'Add detailed student tasks and start date to boost student applications.';
    return 'Complete all required fields (title, domain, duration, skills) to publish.';
  };

  // Draft from idea handler
  const handleDraftFromIdea = () => {
    const raw = ideaText.trim().toLowerCase();
    if (raw.includes('rna-seq') || raw.includes('gene') || raw.includes('mouse')) {
      setTitle('Gene expression analysis in mouse cohorts');
      setDomain('Biology, bioinformatics');
      setDuration('5 months');
      setStartDate('February 2027');
      setLocation(city ? `${city}, on site` : 'Fès, on site');
      setCompensation('Paid');
      setSkills(['Python', 'R', 'Bioconductor', 'Nextflow', 'Statistics']);
      setTasksText(
        'Analyse differential gene expression across 24 samples, build a reproducible Nextflow pipeline and present results at the lab meeting.'
      );
    } else {
      // Generic smart draft
      const words = ideaText.trim().split(/\s+/);
      const generatedTitle = words.length > 3 ? words.slice(0, 6).join(' ') : ideaText.trim();
      setTitle(generatedTitle.charAt(0).toUpperCase() + generatedTitle.slice(1));
      setDomain('Research & Development');
      setDuration('6 months');
      setStartDate('March 2027');
      setLocation(city ? `${city}, on site` : 'On site');
      setCompensation('Paid');
      setTasksText(ideaText.trim());
      if (skills.length === 0) {
        setSkills(['Data Analysis', 'Python', 'Documentation']);
      }
    }
  };

  const handleAddSkill = (e: React.KeyboardEvent | React.MouseEvent) => {
    if ('key' in e && e.key !== 'Enter') return;
    e.preventDefault();
    const trimmed = newSkill.trim();
    if (trimmed && !skills.some(s => s.toLowerCase() === trimmed.toLowerCase())) {
      setSkills([...skills, trimmed]);
      setNewSkill('');
    }
  };

  const handleRemoveSkill = (skillToRemove: string) => {
    setSkills(skills.filter(s => s !== skillToRemove));
  };

  const handlePublish = async (isDraft: boolean) => {
    if (!title.trim()) {
      setErrorMessage('Please provide an offer title.');
      return;
    }
    if (!domain.trim()) {
      setErrorMessage('Please provide a domain.');
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);

    // Parse duration months number
    let durationMonths = 5;
    const durMatch = duration.match(/\d+/);
    if (durMatch) {
      durationMonths = parseInt(durMatch[0], 10);
    }

    // Parse start date to YYYY-MM-DD
    let isoStartDate = '2027-02-01';
    if (startDate.match(/^\d{4}-\d{2}-\d{2}$/)) {
      isoStartDate = startDate;
    } else {
      const yearMatch = startDate.match(/\d{4}/);
      const year = yearMatch ? yearMatch[0] : '2027';
      const monthLower = startDate.toLowerCase();
      let month = '02';
      if (monthLower.includes('jan')) month = '01';
      else if (monthLower.includes('feb') || monthLower.includes('fév')) month = '02';
      else if (monthLower.includes('mar')) month = '03';
      else if (monthLower.includes('apr') || monthLower.includes('avr')) month = '04';
      else if (monthLower.includes('may') || monthLower.includes('mai')) month = '05';
      else if (monthLower.includes('jun') || monthLower.includes('juin')) month = '06';
      else if (monthLower.includes('jul') || monthLower.includes('juil')) month = '07';
      else if (monthLower.includes('aug') || monthLower.includes('aoû')) month = '08';
      else if (monthLower.includes('sep')) month = '09';
      else if (monthLower.includes('oct')) month = '10';
      else if (monthLower.includes('nov')) month = '11';
      else if (monthLower.includes('dec') || monthLower.includes('déc')) month = '12';
      isoStartDate = `${year}-${month}-01`;
    }

    const tasksList = tasksText
      .split('\n')
      .map(t => t.trim())
      .filter(t => t.length > 0);

    if (tasksList.length === 0 && tasksText.trim()) {
      tasksList.push(tasksText.trim());
    }

    const payload = {
      title: title.trim(),
      domain: domain.trim(),
      durationMonths,
      duration: duration.trim(),
      startDate: isoStartDate,
      location: location.trim() || 'On site',
      compensationType: compensation.trim() || 'Paid',
      compensationAmount: compensation.trim() || 'Paid',
      description: tasksText.trim() || title.trim(),
      tasks: tasksList,
      skills,
      isDraft,
    };

    try {
      const res = await api.createLabOffer(payload, activeLabId);
      setToastMessage(isDraft ? 'Draft saved successfully!' : 'Offer published successfully!');
      fetchLabOffers(activeLabId);
      if (!isDraft) {
        setPublishedOfferId(res.id);
      }
    } catch (err: any) {
      setErrorMessage(err?.message || 'Failed to save offer. Please check your connection.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#F5EFEB] text-[#241814] p-6 lg:p-10 font-sans select-none">
      {/* Toast Notification */}
      {toastMessage && (
        <div
          aria-live="polite"
          className="fixed top-6 right-6 z-50 bg-[#791E2D] text-white px-5 py-3 rounded-xl shadow-lg flex items-center gap-2.5 text-sm font-medium animate-in fade-in"
        >
          <CheckCircle className="w-4 h-4 text-[#E2CEB0]" />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* Success Modal upon publishing */}
      {publishedOfferId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
          <div className="bg-[#EADBC8] border border-[#C5AE90] p-6 sm:p-8 rounded-3xl max-w-lg w-full shadow-2xl text-center space-y-5 animate-in zoom-in-95">
            <div className="w-16 h-16 bg-[#791E2D] text-[#E2CEB0] rounded-full mx-auto flex items-center justify-center shadow-inner">
              <CheckCircle className="w-8 h-8" />
            </div>
            <div>
              <h2 className="font-serif text-2xl font-bold text-[#241814]">
                Offer published live!
              </h2>
              <p className="text-sm text-[#66554D] mt-2">
                Your research offer <strong className="text-[#241814]">"{title}"</strong> is now live. Students can explore and apply to it immediately in their catalog.
              </p>
            </div>
            <div className="pt-2 flex flex-col sm:flex-row gap-3 justify-center">
              <button
                onClick={() => navigate('/student/explore')}
                className="px-5 py-2.5 bg-[#791E2D] hover:bg-[#601622] text-white rounded-xl text-sm font-semibold flex items-center justify-center gap-2 transition-colors cursor-pointer"
              >
                <span>View in Student Explore</span>
                <ExternalLink className="w-4 h-4" />
              </button>
              <button
                onClick={() => navigate('/lab/dashboard')}
                className="px-5 py-2.5 bg-[#DFCAAC] hover:bg-[#D5BF9F] text-[#241814] rounded-xl text-sm font-semibold transition-colors cursor-pointer"
              >
                Back to Lab Dashboard
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Page Header */}
      <header className="mb-6">
        <span className="text-xs uppercase tracking-wider text-[#8A756D] font-semibold">Post an offer</span>
        <h1 className="font-serif text-3xl md:text-4xl font-bold text-[#241814] mt-1 tracking-tight">
          Describe the internship
        </h1>
      </header>

      {/* Error alert if any */}
      {errorMessage && (
        <div className="mb-6 p-4 rounded-xl bg-red-100 border border-red-300 text-red-900 text-sm flex items-center gap-3">
          <AlertCircle className="w-5 h-5 flex-shrink-0 text-red-700" />
          <span>{errorMessage}</span>
          <button
            onClick={() => setErrorMessage(null)}
            className="ml-auto text-red-700 hover:text-red-900 font-bold"
          >
            ×
          </button>
        </div>
      )}

      {/* Main Grid: Form (Left) & Preview + Quality Score (Right) */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        {/* Left Side: Form Content (7 Cols) */}
        <div className="lg:col-span-7 space-y-6">
          {/* Start from an idea burgundy banner */}
          <div className="p-5 md:p-6 rounded-2xl bg-[#5B1421] text-white shadow-sm">
            <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-[#E2CEB0] mb-2">
              <Sparkles className="w-3.5 h-3.5" />
              <span>Start from an idea</span>
            </div>
            <textarea
              value={ideaText}
              onChange={(e) => setIdeaText(e.target.value)}
              rows={2}
              className="w-full bg-white/10 hover:bg-white/15 focus:bg-white/20 border border-[#8C3A48] focus:border-[#E2CEB0] rounded-xl p-3 text-sm text-[#F9F5F0] placeholder-[#D8B4B9] focus:outline-none transition-colors resize-none leading-relaxed"
              placeholder="Describe your research idea or project goals in plain language..."
            />
            <div className="mt-3 flex justify-start">
              <button
                type="button"
                onClick={handleDraftFromIdea}
                className="px-4 py-2 rounded-xl bg-[#E2CEB0] hover:bg-white text-[#5B1421] text-xs font-bold transition-colors cursor-pointer shadow-sm"
              >
                Draft the offer
              </button>
            </div>
          </div>

          {/* Form Fields Container */}
          <div className="space-y-4">
            {/* Row 1: Title & Domain */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-[#66554D] mb-1.5">
                  Title
                </label>
                <input
                  type="text"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="e.g. Gene expression analysis"
                  className="w-full bg-[#EADBC8] border border-[#D8C4AC] rounded-xl px-4 py-2.5 text-sm font-semibold text-[#241814] focus:outline-none focus:ring-2 focus:ring-[#791E2D]"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-[#66554D] mb-1.5">
                  Domain
                </label>
                <input
                  type="text"
                  value={domain}
                  onChange={(e) => setDomain(e.target.value)}
                  placeholder="e.g. Biology, bioinformatics"
                  className="w-full bg-[#EADBC8] border border-[#D8C4AC] rounded-xl px-4 py-2.5 text-sm font-semibold text-[#241814] focus:outline-none focus:ring-2 focus:ring-[#791E2D]"
                />
              </div>
            </div>

            {/* Row 2: Duration & Start date */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-[#66554D] mb-1.5">
                  Duration
                </label>
                <input
                  type="text"
                  value={duration}
                  onChange={(e) => setDuration(e.target.value)}
                  placeholder="e.g. 5 months"
                  className="w-full bg-[#EADBC8] border border-[#D8C4AC] rounded-xl px-4 py-2.5 text-sm font-semibold text-[#241814] focus:outline-none focus:ring-2 focus:ring-[#791E2D]"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-[#66554D] mb-1.5">
                  Start date
                </label>
                <input
                  type="text"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                  placeholder="e.g. February 2027"
                  className="w-full bg-[#EADBC8] border border-[#D8C4AC] rounded-xl px-4 py-2.5 text-sm font-semibold text-[#241814] focus:outline-none focus:ring-2 focus:ring-[#791E2D]"
                />
              </div>
            </div>

            {/* Row 3: Location & Compensation */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-[#66554D] mb-1.5">
                  Location
                </label>
                <input
                  type="text"
                  value={location}
                  onChange={(e) => setLocation(e.target.value)}
                  placeholder="e.g. Fès, on site"
                  className="w-full bg-[#EADBC8] border border-[#D8C4AC] rounded-xl px-4 py-2.5 text-sm font-semibold text-[#241814] focus:outline-none focus:ring-2 focus:ring-[#791E2D]"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-[#66554D] mb-1.5">
                  Compensation
                </label>
                <input
                  type="text"
                  value={compensation}
                  onChange={(e) => setCompensation(e.target.value)}
                  placeholder="e.g. Paid"
                  className="w-full bg-[#EADBC8] border border-[#D8C4AC] rounded-xl px-4 py-2.5 text-sm font-semibold text-[#241814] focus:outline-none focus:ring-2 focus:ring-[#791E2D]"
                />
              </div>
            </div>

            {/* Row 4: Skills you are looking for */}
            <div>
              <label className="block text-xs font-semibold text-[#66554D] mb-1.5">
                Skills you are looking for
              </label>
              <div className="bg-[#EADBC8] border border-[#D8C4AC] rounded-xl p-3 flex flex-wrap items-center gap-2">
                {skills.map((skill) => (
                  <span
                    key={skill}
                    className="inline-flex items-center gap-1.5 bg-[#DFCEB9] border border-[#CDB59E] text-xs font-medium text-[#241814] px-3 py-1 rounded-full shadow-2xs"
                  >
                    <span>{skill}</span>
                    <button
                      type="button"
                      onClick={() => handleRemoveSkill(skill)}
                      className="text-[#8A756D] hover:text-[#791E2D] font-bold text-xs cursor-pointer"
                      title={`Remove ${skill}`}
                    >
                      ×
                    </button>
                  </span>
                ))}

                {/* Inline Add Skill Input */}
                <div className="inline-flex items-center gap-1">
                  <input
                    type="text"
                    value={newSkill}
                    onChange={(e) => setNewSkill(e.target.value)}
                    onKeyDown={handleAddSkill}
                    placeholder="+ Add skill"
                    className="bg-transparent text-xs text-[#241814] placeholder-[#8A756D] focus:outline-none px-2 py-1 w-24 sm:w-32"
                  />
                  {newSkill.trim() && (
                    <button
                      type="button"
                      onClick={handleAddSkill}
                      className="p-1 rounded-full bg-[#791E2D] text-white hover:bg-[#601622] cursor-pointer"
                    >
                      <Plus className="w-3 h-3" />
                    </button>
                  )}
                </div>
              </div>
            </div>

            {/* Row 5: What the student will do */}
            <div>
              <label className="block text-xs font-semibold text-[#66554D] mb-1.5">
                What the student will do
              </label>
              <textarea
                value={tasksText}
                onChange={(e) => setTasksText(e.target.value)}
                rows={4}
                placeholder="Analyse differential gene expression across 24 samples, build a reproducible Nextflow pipeline and present results at the lab meeting."
                className="w-full bg-[#EADBC8] border border-[#D8C4AC] rounded-xl p-4 text-sm text-[#241814] font-normal focus:outline-none focus:ring-2 focus:ring-[#791E2D] leading-relaxed resize-y"
              />
            </div>
          </div>
        </div>

        {/* Right Side: Live Preview & Offer Quality (5 Cols) */}
        <div className="lg:col-span-5 space-y-6">
          {/* Live Preview Card */}
          <div>
            <div className="text-xs font-semibold text-[#8A756D] mb-2">Live preview</div>
            <div className="bg-[#EADBC8] border border-[#D8C4AC] rounded-2xl p-5 shadow-sm space-y-3">
              <div className="text-xs text-[#66554D] font-medium">
                {labName}, {city} · <span className="text-[#791E2D] font-semibold">Posted by the lab</span>
              </div>
              <h3 className="font-serif text-xl font-bold text-[#241814] leading-snug">
                {title || 'Gene expression analysis in mouse cohorts'}
              </h3>

              {/* Badges */}
              <div className="flex flex-wrap items-center gap-2 pt-1">
                <span className="bg-[#DFCEB9] border border-[#CDB59E] text-xs font-medium text-[#241814] px-3 py-1 rounded-full">
                  {duration || '5 months'}
                </span>
                <span className="bg-[#DFCEB9] border border-[#CDB59E] text-xs font-medium text-[#241814] px-3 py-1 rounded-full">
                  Starts {startDate || 'Feb 2027'}
                </span>
                <span className="bg-[#DFCEB9] border border-[#CDB59E] text-xs font-medium text-[#241814] px-3 py-1 rounded-full">
                  {compensation || 'Paid'}
                </span>
              </div>

              {/* Tasks preview excerpt */}
              <p className="text-xs text-[#4E3D35] leading-relaxed pt-1">
                {tasksText || 'Analyse differential gene expression across samples and build a reproducible pipeline.'}
              </p>
            </div>
          </div>

          {/* Offer Quality Meter Card */}
          <div className="bg-[#EADBC8] border border-[#D8C4AC] rounded-2xl p-5 shadow-sm flex items-center gap-5">
            {/* Circular Gauge */}
            <div className="relative w-16 h-16 flex-shrink-0 flex items-center justify-center">
              <svg className="w-16 h-16 transform -rotate-90" viewBox="0 0 36 36">
                <path
                  className="text-[#D8C4AC]"
                  strokeWidth="3.2"
                  stroke="currentColor"
                  fill="none"
                  d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                />
                <path
                  className="text-[#791E2D]"
                  strokeDasharray={`${qualityScore}, 100`}
                  strokeWidth="3.2"
                  strokeLinecap="round"
                  stroke="currentColor"
                  fill="none"
                  d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                />
              </svg>
              <span data-testid="quality-score" className="absolute font-serif text-xl font-bold text-[#241814]">
                {qualityScore}
              </span>
            </div>

            {/* Quality Advice */}
            <div className="flex-1">
              <div className="font-bold text-sm text-[#241814]">Offer quality</div>
              <p className="text-xs text-[#66554D] mt-0.5 leading-relaxed">
                {getQualityAdvice(qualityScore)}
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Action Footer Buttons */}
      <div className="mt-10 flex items-center justify-end gap-3 pt-4 border-t border-[#D8C4AC]/60">
        <button
          type="button"
          disabled={submitting}
          onClick={() => handlePublish(false)}
          className="px-6 py-2.5 bg-[#791E2D] hover:bg-[#601622] text-white rounded-xl text-sm font-semibold transition-colors disabled:opacity-50 cursor-pointer shadow-sm"
        >
          {submitting ? 'Publishing...' : 'Publish offer'}
        </button>
        <button
          type="button"
          disabled={submitting}
          onClick={() => handlePublish(true)}
          className="px-6 py-2.5 bg-[#EADBC8] hover:bg-[#DFCDB8] border border-[#C5AE90] text-[#241814] rounded-xl text-sm font-semibold transition-colors disabled:opacity-50 cursor-pointer"
        >
          Save draft
        </button>
      </div>

      {/* Published Offers List for this Lab */}
      <section className="mt-12 pt-8 border-t border-[#D8C4AC]/60">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-6">
          <div>
            <h2 className="font-serif text-2xl font-bold text-[#241814]">
              Active Offers from {labName}
            </h2>
            <p className="text-xs text-[#66554D] mt-0.5">
              These offers are published and visible to students in the explore catalog
            </p>
          </div>
          <button
            onClick={() => navigate('/student/explore')}
            className="px-4 py-2 bg-[#EADBC8] hover:bg-[#DFCDB8] border border-[#C5AE90] rounded-xl text-xs font-semibold text-[#241814] flex items-center gap-1.5 transition-colors cursor-pointer self-start sm:self-auto"
          >
            <span>View all in Explore Catalog</span>
            <ExternalLink className="w-3.5 h-3.5" />
          </button>
        </div>

        {labOffers.length === 0 ? (
          <div className="p-8 rounded-2xl bg-[#EADBC8]/50 border border-[#D8C4AC] text-center text-sm text-[#66554D]">
            No offers published yet. Fill out the form above and click "Publish offer".
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {labOffers.map((off) => (
              <div 
                key={off.id}
                className="bg-[#EADBC8] border border-[#D8C4AC] p-5 rounded-2xl shadow-sm flex flex-col justify-between"
              >
                <div>
                  <div className="flex items-center justify-between text-[11px] text-[#66554D] mb-1.5">
                    <span className="font-semibold text-[#791E2D] bg-[#DFCEB9] px-2 py-0.5 rounded-md">
                      {off.status}
                    </span>
                    <span>{off.applicationsCount} applicant(s)</span>
                  </div>
                  <h4 className="font-serif text-base font-bold text-[#241814] leading-snug line-clamp-2">
                    {off.title}
                  </h4>
                  <p className="text-xs text-[#66554D] mt-1">{off.domain}</p>
                  
                  <div className="flex flex-wrap gap-1.5 mt-3">
                    <span className="text-[11px] bg-[#DFCEB9] px-2 py-0.5 rounded-full text-[#241814]">
                      {off.durationMonths} months
                    </span>
                    <span className="text-[11px] bg-[#DFCEB9] px-2 py-0.5 rounded-full text-[#241814]">
                      {off.location}
                    </span>
                    <span className="text-[11px] bg-[#DFCEB9] px-2 py-0.5 rounded-full text-[#241814]">
                      {off.compensationType}
                    </span>
                  </div>
                </div>

                <div className="mt-4 pt-3 border-t border-[#D8C4AC]/60 flex items-center justify-between text-xs text-[#66554D]">
                  <span>Closes: {off.closesAt || 'Open'}</span>
                  <button
                    onClick={() => navigate(`/student/offers/${off.id}`)}
                    className="text-[#791E2D] hover:underline font-semibold flex items-center gap-1 cursor-pointer"
                  >
                    <span>View offer</span>
                    <ExternalLink className="w-3 h-3" />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
};
