import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Shield, Download, Trash2, CheckCircle2, AlertTriangle, User as UserIcon, Bot, Bell, Palette } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { MOROCCAN_UNIVERSITIES } from '../../types';

export const StudentSettingsPage: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const photoInputRef = useRef<HTMLInputElement>(null);

  const [activeTab, setActiveTab] = useState<'account' | 'ai' | 'notifications' | 'appearance' | 'privacy'>('account');
  const [fullName, setFullName] = useState(user?.fullName || 'Yassine Bennis');
  const [email] = useState(user?.email || 'yassine.bennis@univ-casa.ma');
  const [university, setUniversity] = useState('Université Hassan II de Casablanca (UH2C)');
  const [isCustomUniversity, setIsCustomUniversity] = useState(false);
  const [customUniversity, setCustomUniversity] = useState('');
  const [level, setLevel] = useState('Master 1, data science');
  const [aiEnabled, setAiEnabled] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    fetch('/api/student/profile', {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('fitwell_token') || ''}`,
      },
      credentials: 'include',
    })
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (data) {
          if (data.fullName) setFullName(data.fullName);
          if (data.university) {
            if (MOROCCAN_UNIVERSITIES.includes(data.university)) {
              setUniversity(data.university);
              setIsCustomUniversity(false);
            } else {
              setUniversity('Other');
              setIsCustomUniversity(true);
              setCustomUniversity(data.university);
            }
          }
          if (data.level) setLevel(data.level);
          if (typeof data.aiAssistanceEnabled === 'boolean') setAiEnabled(data.aiAssistanceEnabled);
        }
      })
      .catch(() => {});
  }, []);

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setMessage(null);
    setError(null);

    try {
      const res = await fetch('/api/student/profile', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('fitwell_token') || ''}`,
        },
        credentials: 'include',
        body: JSON.stringify({
          fullName,
          university: isCustomUniversity ? (customUniversity.trim() || 'Other') : university,
          level,
          languages: ['Arabic', 'French', 'English'],
          interests: ['Medical imaging', 'Machine Learning'],
          availabilityStart: '2027-02-01',
          availabilityDurationMonths: 5,
        }),
      });

      if (!res.ok) throw new Error('Failed to update profile');
      setMessage('Account settings saved successfully.');
    } catch (err: any) {
      setError(err.message || 'Error updating settings');
    } finally {
      setLoading(false);
    }
  };

  const handleToggleAi = async (enabled: boolean) => {
    setAiEnabled(enabled);
    try {
      await fetch('/api/student/settings/ai', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('fitwell_token') || ''}`,
        },
        credentials: 'include',
        body: JSON.stringify({ enabled }),
      });
      setMessage(`AI Assistant is now ${enabled ? 'enabled' : 'disabled'}.`);
    } catch {
      setError('Failed to update AI assistant settings');
    }
  };

  const handlePhotoUpload = async (file: File) => {
    setError(null);
    try {
      const buffer = await file.arrayBuffer();
      const res = await fetch('/api/student/profile/photo', {
        method: 'POST',
        headers: {
          'Content-Type': file.type,
          'Authorization': `Bearer ${localStorage.getItem('fitwell_token') || ''}`,
        },
        credentials: 'include',
        body: buffer,
      });

      if (!res.ok) {
        const data = await res.json();
        throw new Error(data.message || 'Failed to upload photo');
      }
      setMessage('Profile photo updated.');
    } catch (err: any) {
      setError(err.message || 'Error uploading photo');
    }
  };

  const handleExportData = async () => {
    try {
      const res = await fetch('/api/student/export-data', {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('fitwell_token') || ''}`,
        },
        credentials: 'include',
      });
      const data = await res.json();
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `fitwell-data-export-${new Date().toISOString().slice(0, 10)}.json`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err: any) {
      setError('Failed to export data: ' + err.message);
    }
  };

  const handleDeleteAccount = async () => {
    const confirmed = window.confirm(
      'Are you sure you want to permanently delete your Fitwell account? In compliance with Moroccan Law 09-08, all your profile data, uploaded CVs, and applications will be erased immediately.'
    );
    if (!confirmed) return;

    try {
      const res = await fetch('/api/student/account', {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('fitwell_token') || ''}`,
        },
        credentials: 'include',
      });
      if (res.ok) {
        await logout();
        navigate('/login');
      }
    } catch (err: any) {
      setError('Failed to delete account: ' + err.message);
    }
  };

  return (
    <div className="space-y-6">
      {/* Page Title from Page 10 */}
      <div>
        <h1 className="font-serif text-4xl font-bold text-[#241814]">Settings</h1>
      </div>

      {message && (
        <div className="p-3.5 rounded-xl bg-[#2D6A4F]/10 border border-[#2D6A4F] text-[#2D6A4F] text-xs font-semibold flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4 shrink-0" />
          <span>{message}</span>
        </div>
      )}

      {error && (
        <div className="p-3.5 rounded-xl bg-[#791E2D]/10 border border-[#791E2D] text-[#791E2D] text-xs font-semibold flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Main Settings Layout matching Page 10 */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-8">
        {/* Left Sub-Navigation Menu */}
        <div className="space-y-1">
          <button
            onClick={() => setActiveTab('account')}
            className={`w-full text-left px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors flex items-center gap-2.5 ${
              activeTab === 'account' ? 'bg-[#E2CEB0] text-[#241814] shadow-sm' : 'text-[#66554D] hover:bg-[#E2CEB0]/50'
            }`}
          >
            <UserIcon className="w-4 h-4" />
            <span>Account</span>
          </button>

          <button
            onClick={() => setActiveTab('ai')}
            className={`w-full text-left px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors flex items-center gap-2.5 ${
              activeTab === 'ai' ? 'bg-[#E2CEB0] text-[#241814] shadow-sm' : 'text-[#66554D] hover:bg-[#E2CEB0]/50'
            }`}
          >
            <Bot className="w-4 h-4" />
            <span>AI assistant</span>
          </button>

          <button
            onClick={() => setActiveTab('notifications')}
            className={`w-full text-left px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors flex items-center gap-2.5 ${
              activeTab === 'notifications' ? 'bg-[#E2CEB0] text-[#241814] shadow-sm' : 'text-[#66554D] hover:bg-[#E2CEB0]/50'
            }`}
          >
            <Bell className="w-4 h-4" />
            <span>Notifications</span>
          </button>

          <button
            onClick={() => setActiveTab('appearance')}
            className={`w-full text-left px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors flex items-center gap-2.5 ${
              activeTab === 'appearance' ? 'bg-[#E2CEB0] text-[#241814] shadow-sm' : 'text-[#66554D] hover:bg-[#E2CEB0]/50'
            }`}
          >
            <Palette className="w-4 h-4" />
            <span>Appearance</span>
          </button>

          <button
            onClick={() => setActiveTab('privacy')}
            className={`w-full text-left px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors flex items-center gap-2.5 ${
              activeTab === 'privacy' ? 'bg-[#E2CEB0] text-[#241814] shadow-sm' : 'text-[#66554D] hover:bg-[#E2CEB0]/50'
            }`}
          >
            <Shield className="w-4 h-4" />
            <span>Privacy and data</span>
          </button>
        </div>

        {/* Right Content Area */}
        <div className="md:col-span-3 space-y-6">
          {/* TAB: ACCOUNT (Exact Page 10 Mockup) */}
          {activeTab === 'account' && (
            <div className="space-y-6">
              <h2 className="font-serif text-2xl font-bold text-[#241814]">Account</h2>

              <form onSubmit={handleSaveProfile} className="space-y-5">
                <div className="bg-[#E2CEB0] p-6 rounded-2xl border border-[#C5AE90] space-y-4">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-xs font-semibold text-[#66554D] mb-1.5">
                        Full name
                      </label>
                      <input
                        type="text"
                        value={fullName}
                        onChange={(e) => setFullName(e.target.value)}
                        className="w-full px-4 py-2.5 rounded-xl bg-[#DFCAAC] border border-[#BFA789] text-sm text-[#241814] focus:ring-2 focus:ring-[#791E2D]"
                      />
                    </div>

                    <div>
                      <label className="block text-xs font-semibold text-[#66554D] mb-1.5">
                        Email
                      </label>
                      <input
                        type="email"
                        disabled
                        value={email}
                        className="w-full px-4 py-2.5 rounded-xl bg-[#D6C1A2] border border-[#BFA789] text-sm text-[#66554D] cursor-not-allowed"
                      />
                    </div>

                    <div>
                      <div className="flex items-center justify-between mb-1.5">
                        <label htmlFor="settings-university" className="block text-xs font-semibold text-[#66554D]">
                          University
                        </label>
                        {user?.isVerifiedStudent && (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800 text-[10px] font-bold">
                            ✓ Verified student
                          </span>
                        )}
                      </div>
                      <select
                        id="settings-university"
                        value={isCustomUniversity ? 'Other' : university}
                        onChange={(e) => {
                          if (e.target.value === 'Other') {
                            setIsCustomUniversity(true);
                          } else {
                            setIsCustomUniversity(false);
                            setUniversity(e.target.value);
                          }
                        }}
                        className="w-full px-4 py-2.5 rounded-xl bg-[#DFCAAC] border border-[#BFA789] text-sm text-[#241814] focus:ring-2 focus:ring-[#791E2D]"
                      >
                        {MOROCCAN_UNIVERSITIES.map((u) => (
                          <option key={u} value={u}>{u}</option>
                        ))}
                        <option value="Other">Other</option>
                      </select>

                      {isCustomUniversity && (
                        <input
                          type="text"
                          value={customUniversity}
                          onChange={(e) => setCustomUniversity(e.target.value)}
                          placeholder="Enter your university or institution name"
                          className="mt-2 w-full px-4 py-2.5 rounded-xl bg-[#DFCAAC] border border-[#BFA789] text-sm text-[#241814] placeholder-[#8D7A68] focus:ring-2 focus:ring-[#791E2D]"
                        />
                      )}
                    </div>

                    <div>
                      <label className="block text-xs font-semibold text-[#66554D] mb-1.5">
                        Level
                      </label>
                      <input
                        type="text"
                        value={level}
                        onChange={(e) => setLevel(e.target.value)}
                        className="w-full px-4 py-2.5 rounded-xl bg-[#DFCAAC] border border-[#BFA789] text-sm text-[#241814] focus:ring-2 focus:ring-[#791E2D]"
                      />
                    </div>
                  </div>

                  <div className="pt-2 flex justify-end">
                    <button
                      type="submit"
                      disabled={loading}
                      className="px-5 py-2 rounded-xl bg-[#5B1421] text-white text-xs font-semibold hover:bg-[#48101A] transition-colors"
                    >
                      {loading ? 'Saving...' : 'Save changes'}
                    </button>
                  </div>
                </div>
              </form>

              {/* Profile Photo Card matching Page 10 */}
              <div className="bg-[#E2CEB0] p-6 rounded-2xl border border-[#C5AE90] flex items-center justify-between">
                <div className="flex items-center gap-4">
                  <div className="w-12 h-12 rounded-full bg-[#5B1421] text-white flex items-center justify-center font-bold text-sm">
                    YB
                  </div>
                  <div>
                    <h3 className="font-semibold text-sm text-[#241814]">Profile photo</h3>
                    <p className="text-xs text-[#66554D] mt-0.5">
                      Optional. Labs see it only after you apply.
                    </p>
                  </div>
                </div>

                <input
                  ref={photoInputRef}
                  type="file"
                  accept="image/jpeg,image/png"
                  className="hidden"
                  onChange={(e) => {
                    if (e.target.files && e.target.files[0]) {
                      handlePhotoUpload(e.target.files[0]);
                    }
                  }}
                />
                <button
                  type="button"
                  onClick={() => photoInputRef.current?.click()}
                  className="px-4 py-2 rounded-xl bg-[#DFCAAC] border border-[#BFA789] hover:bg-[#D6C1A2] text-xs font-semibold text-[#241814] transition-colors"
                >
                  Change
                </button>
              </div>
            </div>
          )}

          {/* TAB: AI ASSISTANT */}
          {activeTab === 'ai' && (
            <div className="space-y-6">
              <h2 className="font-serif text-2xl font-bold text-[#241814]">AI Assistant</h2>
              <div className="bg-[#E2CEB0] p-6 rounded-2xl border border-[#C5AE90] space-y-4">
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="text-sm font-semibold text-[#241814]">Enable AI assistance</h3>
                    <p className="text-xs text-[#66554D] mt-0.5">
                      Fitwell AI suggests tailored cover letter phrasing and interview questions. AI never submits applications on your behalf.
                    </p>
                  </div>
                  <label className="relative inline-flex items-center cursor-pointer">
                    <input
                      type="checkbox"
                      checked={aiEnabled}
                      onChange={(e) => handleToggleAi(e.target.checked)}
                      className="sr-only peer"
                    />
                    <div className="w-11 h-6 bg-[#C5AE90] peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#5B1421]"></div>
                  </label>
                </div>
              </div>
            </div>
          )}

          {/* TAB: NOTIFICATIONS */}
          {activeTab === 'notifications' && (
            <div className="space-y-6">
              <h2 className="font-serif text-2xl font-bold text-[#241814]">Notifications</h2>
              <div className="bg-[#E2CEB0] p-6 rounded-2xl border border-[#C5AE90] space-y-4 text-xs">
                <div className="flex items-center justify-between py-2 border-b border-[#C5AE90]">
                  <span>New research internship matches</span>
                  <input type="checkbox" defaultChecked className="accent-[#5B1421]" />
                </div>
                <div className="flex items-center justify-between py-2 border-b border-[#C5AE90]">
                  <span>Interview slot offers from laboratories</span>
                  <input type="checkbox" defaultChecked className="accent-[#5B1421]" />
                </div>
                <div className="flex items-center justify-between py-2">
                  <span>Messages from research directors</span>
                  <input type="checkbox" defaultChecked className="accent-[#5B1421]" />
                </div>
              </div>
            </div>
          )}

          {/* TAB: APPEARANCE */}
          {activeTab === 'appearance' && (
            <div className="space-y-6">
              <h2 className="font-serif text-2xl font-bold text-[#241814]">Appearance</h2>
              <div className="bg-[#E2CEB0] p-6 rounded-2xl border border-[#C5AE90] space-y-3">
                <p className="text-xs text-[#66554D]">
                  Fitwell uses an academic editorial palette inspired by Moroccan university libraries.
                </p>
                <div className="flex gap-3">
                  <div className="flex items-center gap-2 p-3 rounded-xl bg-[#5B1421] text-white text-xs font-semibold">
                    <div className="w-4 h-4 rounded-full bg-[#E2CEB0]"></div>
                    <span>Moroccan Warm Desert & Wine (Active)</span>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB: PRIVACY AND DATA (Moroccan Law 09-08) */}
          {activeTab === 'privacy' && (
            <div className="space-y-6">
              <h2 className="font-serif text-2xl font-bold text-[#241814]">Privacy & Data Protection</h2>

              <div className="bg-[#E2CEB0] p-6 rounded-2xl border border-[#C5AE90] space-y-4">
                <div className="flex items-start gap-3">
                  <Shield className="w-5 h-5 text-[#2D6A4F] shrink-0 mt-0.5" />
                  <div className="text-xs text-[#66554D] leading-relaxed">
                    <span className="font-bold text-[#241814]">Moroccan Law 09-08 Compliance:</span> Fitwell guarantees your right to access, export, and permanently delete your personal information at any time. Compatibility scores strictly never evaluate your name, gender, photo, or background.
                  </div>
                </div>

                <div className="pt-4 border-t border-[#C5AE90] flex flex-wrap items-center justify-between gap-4">
                  <div>
                    <h4 className="text-xs font-semibold text-[#241814]">Export your complete record</h4>
                    <p className="text-[11px] text-[#66554D]">Download a JSON archive of your profile, projects, and applications.</p>
                  </div>
                  <button
                    onClick={handleExportData}
                    className="px-4 py-2 rounded-xl bg-[#DFCAAC] border border-[#BFA789] hover:bg-[#D6C1A2] text-xs font-semibold text-[#241814] flex items-center gap-2 transition-colors"
                  >
                    <Download className="w-3.5 h-3.5" />
                    <span>Export data</span>
                  </button>
                </div>

                <div className="pt-4 border-t border-[#C5AE90] flex flex-wrap items-center justify-between gap-4">
                  <div>
                    <h4 className="text-xs font-semibold text-[#791E2D]">Delete Fitwell Account</h4>
                    <p className="text-[11px] text-[#66554D]">Permanently erase your account, sessions, and all submitted files.</p>
                  </div>
                  <button
                    onClick={handleDeleteAccount}
                    className="px-4 py-2 rounded-xl bg-[#791E2D] hover:bg-[#631724] text-white text-xs font-semibold flex items-center gap-2 transition-colors"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                    <span>Delete account</span>
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
