import React, { useState, useRef, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Upload, AlertTriangle, Sparkles, X, Plus, Check } from 'lucide-react';

interface ExtractedProject {
  title: string;
  description: string;
  skills: string[];
  resultSummary: string;
}

export const StudentOnboardingPage: React.FC = () => {
  const navigate = useNavigate();
  const fileInputRef = useRef<HTMLInputElement>(null);

  // File state (starts with example values or loaded from draft)
  const [uploadedFile, setUploadedFile] = useState<{
    cvId?: string;
    name: string;
    sizeKb: number;
    path: string;
  } | null>({
    name: 'CV-Yassine.pdf',
    sizeKb: 214,
    path: '/uploads/cvs/sample.pdf',
  });

  const [loading, setLoading] = useState<boolean>(false);
  const [analyzing, setAnalyzing] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [noTextFound, setNoTextFound] = useState<boolean>(false);

  // Extracted fields
  const [education, setEducation] = useState<string>('Master 1, Data science');
  const [editingEducation, setEditingEducation] = useState<boolean>(false);
  const [educationInput, setEducationInput] = useState<string>('Master 1, Data science');
  const [onboardingCompleted, setOnboardingCompleted] = useState<boolean>(false);

  const [university] = useState<string>('Université Hassan II de Casablanca (UH2C)');

  const [skills, setSkills] = useState<string[]>(['Python', 'PyTorch', 'OpenCV', 'SQL', 'Git']);
  const [newSkillInput, setNewSkillInput] = useState<string>('');
  const [showAddSkill, setShowAddSkill] = useState<boolean>(false);
  const [editingSkillIndex, setEditingSkillIndex] = useState<number | null>(null);
  const [editingSkillText, setEditingSkillText] = useState<string>('');

  const [languages, setLanguages] = useState<string[]>(['Arabic', 'French', 'English']);
  const [newLanguageInput, setNewLanguageInput] = useState<string>('');
  const [showAddLanguage, setShowAddLanguage] = useState<boolean>(false);
  const [editingLangIndex, setEditingLangIndex] = useState<number | null>(null);
  const [editingLangText, setEditingLangText] = useState<string>('');

  const [projects, setProjects] = useState<ExtractedProject[]>([
    {
      title: 'Image segmentation',
      description: 'Biomedical scan segmentation with U-Net',
      skills: ['Python', 'PyTorch', 'OpenCV'],
      resultSummary: '', // Missing quantified result to trigger warning rule
    },
    {
      title: 'Research abstracts scraper',
      description: 'Indexed 12,000 papers with 94% precision',
      skills: ['Python', 'SQL'],
      resultSummary: '12,000 papers indexed',
    },
  ]);
  const [newProjectTitle, setNewProjectTitle] = useState<string>('');
  const [showAddProject, setShowAddProject] = useState<boolean>(false);
  const [editingProjectIndex, setEditingProjectIndex] = useState<number | null>(null);
  const [editingProjectTitle, setEditingProjectTitle] = useState<string>('');

  // Fetch initial onboarding draft state if available
  useEffect(() => {
    fetch('/api/student/onboarding/state', { credentials: 'include' })
      .then((res) => {
        if (!res.ok) return null;
        return res.json();
      })
      .then((data) => {
        if (data) {
          if (data.onboardingCompleted) {
            setOnboardingCompleted(true);
          }
          if (data.hasDraft && data.draft && data.draft.parsedJson) {
            try {
              const parsed = typeof data.draft.parsedJson === 'string'
                ? JSON.parse(data.draft.parsedJson)
                : data.draft.parsedJson;

              setUploadedFile({
                cvId: data.draft.cvId,
                name: data.draft.fileName || 'CV.pdf',
                sizeKb: parsed.fileSizeKb || 214,
                path: '',
              });

              if (parsed.education !== undefined) {
                setEducation(parsed.education);
                setEducationInput(parsed.education);
              }
              if (Array.isArray(parsed.skills)) setSkills(parsed.skills);
              if (Array.isArray(parsed.languages)) setLanguages(parsed.languages);
              if (Array.isArray(parsed.projects)) setProjects(parsed.projects);
              if (parsed.noTextFound !== undefined) setNoTextFound(parsed.noTextFound);
            } catch {
              // Ignore parse errors
            }
          }
        }
      })
      .catch(() => {});
  }, []);

  // Compute total real fields count: formations + compétences + langues + projets
  const fieldsReadCount = useMemo(() => {
    const eduCount = education && education.trim().length > 0 ? 1 : 0;
    return eduCount + skills.length + languages.length + projects.length;
  }, [education, skills, languages, projects]);

  // Compute "Check this" banner rule-based doubts:
  // ONLY displayed if there are real doubts: project without result, no education, no languages, or < 3 skills.
  const checkThisWarning = useMemo(() => {
    const projectWithoutResult = projects.some((p) => !p.resultSummary || !/\d/.test(p.resultSummary));
    if (projectWithoutResult) {
      return 'Check this: one project has no result. You can add it later in the CV studio.';
    }
    if (!education || !education.trim()) {
      return 'Check this: no education found in your CV. You can type it above.';
    }
    if (languages.length === 0) {
      return 'Check this: no language identified in your CV. You can add them above.';
    }
    if (skills.length < 3) {
      return 'Check this: fewer than 3 skills identified. Consider adding more skills.';
    }
    return null;
  }, [education, languages, skills, projects]);

  const handleFileUpload = async (file: File) => {
    if (!file.name.toLowerCase().endsWith('.pdf')) {
      setError('Only PDF files are supported.');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setError('File size exceeds the 5MB maximum limit.');
      return;
    }

    setError(null);
    setAnalyzing(true);

    try {
      const arrayBuffer = await file.arrayBuffer();
      const res = await fetch('/api/student/onboarding/cv', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/pdf',
          'X-File-Name': file.name,
          'Authorization': `Bearer ${localStorage.getItem('fitwell_token') || ''}`,
        },
        credentials: 'include',
        body: arrayBuffer,
      });

      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        throw new Error(errData.message || 'Failed to analyze CV');
      }

      const data = await res.json();
      setUploadedFile({
        cvId: data.cvId,
        name: data.originalFileName,
        sizeKb: data.fileSizeKb,
        path: data.savedStoragePath,
      });

      if (data.extracted) {
        setEducation(data.extracted.education || '');
        setEducationInput(data.extracted.education || '');
        setSkills(data.extracted.skills || []);
        setLanguages(data.extracted.languages || []);
        setProjects(data.extracted.projects || []);
        setNoTextFound(!!data.extracted.noTextFound);
      }
    } catch (err: any) {
      setError(err.message || 'Error processing CV file');
    } finally {
      setAnalyzing(false);
    }
  };

  // Keyboard and drag/drop handlers for dropzone
  const handleDropzoneKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      fileInputRef.current?.click();
    }
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      handleFileUpload(e.dataTransfer.files[0]);
    }
  };

  // Pill mutations
  const handleRemoveSkill = (skillToRemove: string) => {
    setSkills(skills.filter((s) => s !== skillToRemove));
  };

  const handleAddSkill = () => {
    const trimmed = newSkillInput.trim();
    if (trimmed && !skills.includes(trimmed)) {
      setSkills([...skills, trimmed]);
      setNewSkillInput('');
      setShowAddSkill(false);
    }
  };

  const handleSaveEditedSkill = (index: number) => {
    const trimmed = editingSkillText.trim();
    if (trimmed) {
      const next = [...skills];
      next[index] = trimmed;
      setSkills(next);
    }
    setEditingSkillIndex(null);
  };

  const handleRemoveLanguage = (langToRemove: string) => {
    setLanguages(languages.filter((l) => l !== langToRemove));
  };

  const handleAddLanguage = () => {
    const trimmed = newLanguageInput.trim();
    if (trimmed && !languages.includes(trimmed)) {
      setLanguages([...languages, trimmed]);
      setNewLanguageInput('');
      setShowAddLanguage(false);
    }
  };

  const handleSaveEditedLanguage = (index: number) => {
    const trimmed = editingLangText.trim();
    if (trimmed) {
      const next = [...languages];
      next[index] = trimmed;
      setLanguages(next);
    }
    setEditingLangIndex(null);
  };

  const handleRemoveProject = (indexToRemove: number) => {
    setProjects(projects.filter((_, idx) => idx !== indexToRemove));
  };

  const handleAddProject = () => {
    const trimmed = newProjectTitle.trim();
    if (trimmed) {
      setProjects([
        ...projects,
        {
          title: trimmed,
          description: '',
          skills: [],
          resultSummary: '',
        },
      ]);
      setNewProjectTitle('');
      setShowAddProject(false);
    }
  };

  const handleSaveEditedProject = (index: number) => {
    const trimmed = editingProjectTitle.trim();
    if (trimmed) {
      const next = [...projects];
      next[index] = { ...next[index], title: trimmed };
      setProjects(next);
    }
    setEditingProjectIndex(null);
  };

  // Actions
  const handleSkip = async () => {
    try {
      await fetch('/api/student/onboarding/skip', {
        method: 'POST',
        credentials: 'include',
      });
    } catch {
      // Proceed even on failure
    }
    navigate('/student/home');
  };

  const handleConfirmAndContinue = async () => {
    setLoading(true);
    try {
      if (uploadedFile?.cvId) {
        await fetch('/api/student/onboarding/cv/confirm', {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${localStorage.getItem('fitwell_token') || ''}`,
          },
          credentials: 'include',
          body: JSON.stringify({
            cvId: uploadedFile.cvId,
            education,
            university,
            skills,
            languages,
            projects,
          }),
        });
      }
      if (onboardingCompleted) {
        navigate('/student/home');
      } else {
        navigate('/onboarding/interests');
      }
    } catch (err: any) {
      setError('Failed to confirm CV: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#D3B996] text-[#241814] flex flex-col justify-between p-8 md:p-12 font-sans select-none">
      {/* Top Header Navigation matching mockup page-02 */}
      <div className="flex items-center justify-between pb-8">
        <div
          onClick={() => navigate('/student/home')}
          className="flex items-center gap-3 cursor-pointer"
          title="Go to Home"
        >
          <div className="w-7 h-7 bg-[#5B1421] rounded flex items-center justify-center font-serif text-white font-bold text-lg shadow-sm">
            F
          </div>
          <span className="font-serif text-2xl tracking-tight text-[#241814]">Fitwell</span>
        </div>

        {/* 4 Segment Stepper */}
        <div className="flex items-center gap-8 text-xs font-semibold">
          <div className="pb-1 border-b-2 border-[#5B1421] text-[#5B1421]">
            Account
          </div>
          <div className="pb-1 border-b-2 border-[#5B1421] text-[#5B1421] font-bold">
            Your CV
          </div>
          <button
            onClick={() => navigate('/onboarding/interests')}
            className="pb-1 border-b-2 border-[#BFA789]/60 text-[#66554D] hover:text-[#241814] transition-colors"
          >
            Interests
          </button>
          <button
            onClick={() => navigate('/onboarding/availability')}
            className="pb-1 border-b-2 border-[#BFA789]/60 text-[#66554D] hover:text-[#241814] transition-colors"
          >
            Availability
          </button>
        </div>

        <button
          onClick={handleSkip}
          className="text-xs font-medium text-[#66554D] hover:text-[#241814] transition-colors cursor-pointer"
        >
          {onboardingCompleted ? 'Back to Home' : 'Skip for now'}
        </button>
      </div>

      {error && (
        <div className="max-w-6xl mx-auto w-full mb-4 p-3 rounded-xl bg-[#5B1421]/10 border border-[#5B1421] text-[#5B1421] text-xs font-semibold flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Main 2-Column Layout */}
      <div className="max-w-6xl mx-auto w-full grid grid-cols-1 lg:grid-cols-2 gap-10 my-auto items-start">
        {/* Left Column: Dropzone & Actions */}
        <div className="space-y-6">
          <div>
            <div className="text-xs text-[#66554D] font-semibold mb-2">Step 2 of 4</div>
            <h1 className="font-serif text-4xl lg:text-5xl font-bold leading-tight text-[#241814] mb-3">
              Drop your CV and Fitwell will read it
            </h1>
            <p className="text-sm text-[#66554D] leading-relaxed">
              We pull out your education, skills and languages so you don't type them. You check everything before it is saved.
            </p>
          </div>

          {/* Accessible Drop Zone */}
          <div
            role="button"
            tabIndex={0}
            aria-label="Upload your PDF CV"
            onClick={() => fileInputRef.current?.click()}
            onKeyDown={handleDropzoneKeyDown}
            onDragOver={handleDragOver}
            onDrop={handleDrop}
            className="border-2 border-dashed border-[#BFA789] hover:border-[#5B1421] focus:border-[#5B1421] focus:outline-none focus:ring-2 focus:ring-[#5B1421]/30 rounded-2xl p-10 flex flex-col items-center justify-center bg-[#E2CEB0]/40 hover:bg-[#E2CEB0]/70 transition-all cursor-pointer text-center relative"
          >
            <input
              ref={fileInputRef}
              type="file"
              accept=".pdf,application/pdf"
              className="hidden"
              onChange={(e) => {
                if (e.target.files && e.target.files[0]) {
                  handleFileUpload(e.target.files[0]);
                }
              }}
            />

            {analyzing ? (
              <div aria-live="polite" className="flex flex-col items-center gap-3">
                <div className="w-10 h-10 border-3 border-[#5B1421] border-t-transparent rounded-full animate-spin" />
                <span className="font-semibold text-sm text-[#241814]">Analyzing your CV...</span>
                <span className="text-xs text-[#66554D]">Extracting education, skills and projects</span>
              </div>
            ) : uploadedFile ? (
              <div className="flex flex-col items-center">
                <div className="w-8 h-8 rounded-full bg-[#E2CEB0] flex items-center justify-center text-[#5B1421] mb-2 shadow-xs">
                  <Upload className="w-4 h-4" />
                </div>
                <div className="font-bold text-sm text-[#241814]">{uploadedFile.name}</div>
                <div className="text-xs text-[#66554D] mt-0.5">Uploaded - {uploadedFile.sizeKb} KB</div>
              </div>
            ) : (
              <div className="flex flex-col items-center">
                <div className="w-10 h-10 rounded-full bg-[#E2CEB0] flex items-center justify-center text-[#5B1421] mb-3 shadow-xs">
                  <Upload className="w-5 h-5" />
                </div>
                <div className="font-semibold text-sm text-[#241814]">Click or drag & drop your PDF CV here</div>
                <div className="text-xs text-[#66554D] mt-1">PDF format up to 5MB</div>
              </div>
            )}
          </div>

          {/* Action buttons */}
          <div className="flex items-center gap-4 pt-2">
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className="px-5 py-2.5 rounded-xl bg-[#E2CEB0] hover:bg-[#D6C1A2] text-[#241814] text-xs font-semibold border border-[#BFA789] transition-colors cursor-pointer"
            >
              Replace file
            </button>
            <button
              type="button"
              onClick={handleConfirmAndContinue}
              disabled={loading || analyzing}
              className="px-6 py-2.5 rounded-xl bg-[#5B1421] hover:bg-[#48101A] text-white text-xs font-semibold transition-colors shadow-sm disabled:opacity-50 cursor-pointer"
            >
              {loading ? 'Saving...' : 'Looks right, continue'}
            </button>
          </div>
        </div>

        {/* Right Column: What Fitwell Found (Live editable card) */}
        <div className="bg-[#E5D2BA] p-8 rounded-3xl border border-[#C5AE90] shadow-sm space-y-6">
          <div className="flex items-center justify-between pb-3 border-b border-[#C5AE90]/60">
            <div className="flex items-center gap-2 text-sm font-bold text-[#241814]">
              <Sparkles className="w-4 h-4 text-[#5B1421]" />
              <span>What Fitwell found</span>
            </div>
            <span className="text-xs text-[#791E2D] font-bold">
              {fieldsReadCount} fields read
            </span>
          </div>

          {/* Scanned PDF warning notice */}
          {noTextFound && (
            <div className="p-3.5 rounded-xl bg-[#5B1421]/10 border border-[#5B1421]/30 text-xs text-[#5B1421] leading-relaxed">
              <span className="font-bold">No selectable text found:</span> This document appears to be a scanned image. You can manually add your education, skills, languages and projects using the buttons below, or replace it with a text-based PDF.
            </div>
          )}

          {/* Education Section */}
          <div className="space-y-2 pb-4 border-b border-[#C5AE90]/60">
            <div className="text-xs font-semibold text-[#66554D]">Education</div>
            <div className="flex flex-wrap items-center gap-2">
              {education ? (
                editingEducation ? (
                  <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#DFCAAC] border border-[#5B1421]">
                    <input
                      type="text"
                      value={educationInput}
                      onChange={(e) => setEducationInput(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault();
                          setEducation(educationInput.trim());
                          setEditingEducation(false);
                        }
                      }}
                      className="text-xs bg-transparent focus:outline-none text-[#241814]"
                      autoFocus
                    />
                    <button
                      type="button"
                      onClick={() => {
                        setEducation(educationInput.trim());
                        setEditingEducation(false);
                      }}
                      className="text-[#5B1421]"
                    >
                      <Check className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ) : (
                  <span
                    onClick={() => {
                      setEducationInput(education);
                      setEditingEducation(true);
                    }}
                    className="inline-flex items-center gap-2 px-3.5 py-1 rounded-full bg-[#DFCAAC] border border-[#BFA789] text-xs font-medium text-[#241814] cursor-pointer hover:border-[#5B1421]"
                  >
                    <span>{education}</span>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setEducation('');
                      }}
                      className="hover:text-[#5B1421]"
                      aria-label="Remove education"
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </span>
                )
              ) : (
                <button
                  type="button"
                  onClick={() => {
                    setEducationInput('');
                    setEditingEducation(true);
                  }}
                  className="inline-flex items-center gap-1 px-3 py-1 rounded-full border border-dashed border-[#8D7A68] text-xs text-[#66554D] hover:text-[#241814]"
                >
                  <Plus className="w-3 h-3" />
                  <span>Add education</span>
                </button>
              )}
            </div>
          </div>

          {/* Skills Section */}
          <div className="space-y-2 pb-4 border-b border-[#C5AE90]/60">
            <div className="text-xs font-semibold text-[#66554D]">Skills</div>
            <div className="flex flex-wrap items-center gap-2">
              {skills.map((s, idx) =>
                editingSkillIndex === idx ? (
                  <div key={idx} className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-[#F5E6E8] border border-[#5B1421]">
                    <input
                      type="text"
                      value={editingSkillText}
                      onChange={(e) => setEditingSkillText(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault();
                          handleSaveEditedSkill(idx);
                        }
                      }}
                      className="text-xs bg-transparent focus:outline-none text-[#5B1421] w-20"
                      autoFocus
                    />
                    <button type="button" onClick={() => handleSaveEditedSkill(idx)} className="text-[#5B1421]">
                      <Check className="w-3 h-3" />
                    </button>
                  </div>
                ) : (
                  <span
                    key={s}
                    onClick={() => {
                      setEditingSkillIndex(idx);
                      setEditingSkillText(s);
                    }}
                    className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#F5E6E8] border border-[#E4BFC6] text-xs font-medium text-[#5B1421] cursor-pointer hover:border-[#5B1421]"
                  >
                    <span>{s}</span>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleRemoveSkill(s);
                      }}
                      className="hover:text-red-700"
                      aria-label={`Remove skill ${s}`}
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </span>
                )
              )}

              {showAddSkill ? (
                <div className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-[#DFCAAC] border border-[#5B1421]">
                  <input
                    type="text"
                    value={newSkillInput}
                    onChange={(e) => setNewSkillInput(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        handleAddSkill();
                      }
                    }}
                    placeholder="Skill name"
                    className="w-20 px-1 py-0.5 text-xs bg-transparent focus:outline-none text-[#241814]"
                    autoFocus
                  />
                  <button type="button" onClick={handleAddSkill} className="text-[#5B1421]">
                    <Check className="w-3 h-3" />
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  onClick={() => setShowAddSkill(true)}
                  className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full border border-dashed border-[#8D7A68] text-xs text-[#66554D] hover:text-[#241814] cursor-pointer"
                >
                  <Plus className="w-3 h-3" />
                  <span>Add</span>
                </button>
              )}
            </div>
          </div>

          {/* Languages Section */}
          <div className="space-y-2 pb-4 border-b border-[#C5AE90]/60">
            <div className="text-xs font-semibold text-[#66554D]">Languages</div>
            <div className="flex flex-wrap items-center gap-2">
              {languages.map((l, idx) =>
                editingLangIndex === idx ? (
                  <div key={idx} className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-[#DFCAAC] border border-[#5B1421]">
                    <input
                      type="text"
                      value={editingLangText}
                      onChange={(e) => setEditingLangText(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault();
                          handleSaveEditedLanguage(idx);
                        }
                      }}
                      className="text-xs bg-transparent focus:outline-none text-[#241814] w-20"
                      autoFocus
                    />
                    <button type="button" onClick={() => handleSaveEditedLanguage(idx)} className="text-[#5B1421]">
                      <Check className="w-3 h-3" />
                    </button>
                  </div>
                ) : (
                  <span
                    key={l}
                    onClick={() => {
                      setEditingLangIndex(idx);
                      setEditingLangText(l);
                    }}
                    className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#DFCAAC] border border-[#BFA789] text-xs font-medium text-[#241814] cursor-pointer hover:border-[#5B1421]"
                  >
                    <span>{l}</span>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleRemoveLanguage(l);
                      }}
                      className="hover:text-[#5B1421]"
                      aria-label={`Remove language ${l}`}
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </span>
                )
              )}

              {showAddLanguage ? (
                <div className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-[#DFCAAC] border border-[#5B1421]">
                  <input
                    type="text"
                    value={newLanguageInput}
                    onChange={(e) => setNewLanguageInput(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        handleAddLanguage();
                      }
                    }}
                    placeholder="Language"
                    className="w-20 px-1 py-0.5 text-xs bg-transparent focus:outline-none text-[#241814]"
                    autoFocus
                  />
                  <button type="button" onClick={handleAddLanguage} className="text-[#5B1421]">
                    <Check className="w-3 h-3" />
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  onClick={() => setShowAddLanguage(true)}
                  className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full border border-dashed border-[#8D7A68] text-xs text-[#66554D] hover:text-[#241814] cursor-pointer"
                >
                  <Plus className="w-3 h-3" />
                  <span>Add</span>
                </button>
              )}
            </div>
          </div>

          {/* Projects Section */}
          <div className="space-y-2">
            <div className="text-xs font-semibold text-[#66554D]">Projects</div>
            <div className="flex flex-wrap items-center gap-2">
              {projects.map((p, idx) =>
                editingProjectIndex === idx ? (
                  <div key={idx} className="inline-flex items-center gap-1 px-3 py-1.5 rounded-full bg-[#DFCAAC] border border-[#5B1421]">
                    <input
                      type="text"
                      value={editingProjectTitle}
                      onChange={(e) => setEditingProjectTitle(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault();
                          handleSaveEditedProject(idx);
                        }
                      }}
                      className="text-xs bg-transparent focus:outline-none text-[#241814] w-32"
                      autoFocus
                    />
                    <button type="button" onClick={() => handleSaveEditedProject(idx)} className="text-[#5B1421]">
                      <Check className="w-3 h-3" />
                    </button>
                  </div>
                ) : (
                  <span
                    key={p.title + idx}
                    onClick={() => {
                      setEditingProjectIndex(idx);
                      setEditingProjectTitle(p.title);
                    }}
                    className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-[#DFCAAC] border border-[#BFA789] text-xs font-medium text-[#241814] cursor-pointer hover:border-[#5B1421]"
                  >
                    <span>{p.title}</span>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleRemoveProject(idx);
                      }}
                      className="hover:text-[#5B1421]"
                      aria-label={`Remove project ${p.title}`}
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </span>
                )
              )}

              {showAddProject ? (
                <div className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-[#DFCAAC] border border-[#5B1421]">
                  <input
                    type="text"
                    value={newProjectTitle}
                    onChange={(e) => setNewProjectTitle(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        handleAddProject();
                      }
                    }}
                    placeholder="Project title"
                    className="w-28 px-1 py-0.5 text-xs bg-transparent focus:outline-none text-[#241814]"
                    autoFocus
                  />
                  <button type="button" onClick={handleAddProject} className="text-[#5B1421]">
                    <Check className="w-3 h-3" />
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  onClick={() => setShowAddProject(true)}
                  className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full border border-dashed border-[#8D7A68] text-xs text-[#66554D] hover:text-[#241814] cursor-pointer"
                >
                  <Plus className="w-3 h-3" />
                  <span>Add</span>
                </button>
              )}
            </div>
          </div>

          {/* "Check this" Banner: strictly shown ONLY when rule-based doubts exist */}
          {checkThisWarning && (
            <div className="p-4 rounded-2xl bg-[#5B1421] text-white text-xs leading-relaxed flex items-start gap-2.5 shadow-sm">
              <AlertTriangle className="w-4 h-4 text-[#E2CEB0] shrink-0 mt-0.5" />
              <span>{checkThisWarning}</span>
            </div>
          )}
        </div>
      </div>

      {/* Footer */}
      <div className="text-center text-xs text-[#8D7A68] pt-8">
        Fitwell — Academic Research Internships in Morocco
      </div>
    </div>
  );
};
