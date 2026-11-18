import React, { useState, useEffect } from 'react';
import { PageShell } from '../../components/common/PageShell';

interface Source {
  id: string;
  name: string;
  domain?: string;
  accessType?: 'INTERNAL' | 'API' | 'DEEPLINK' | 'CAREER_PAGE' | 'REMOTE_FEED' | string;
  url: string;
  cguUrl?: string;
  tosUrl?: string;
  searchUrlTemplate?: string;
  notes?: string;
  type: string;
  status: string;
  accuracyPct: number;
  isAllowed: boolean;
  robotsCheckedAt?: string;
  robotsStatus?: string;
  crawlDelaySeconds: number;
  cssSelectors?: string;
  autoPublish: boolean;
  failureReason?: string;
  alertMessage?: string;
  lastRunAt?: string;
  createdAt: string;
}

interface SampleOffer {
  title: string;
  employer: string;
  location: string;
  durationMonths: number;
  excerpt: string;
  extractionLevel: string;
  confidenceScore: number;
}

export const AdminSourcingPage: React.FC = () => {
  const [sources, setSources] = useState<Source[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [showAddModal, setShowAddModal] = useState<boolean>(false);

  // Edit Source State
  const [editingSource, setEditingSource] = useState<Source | null>(null);
  const [editTosUrl, setEditTosUrl] = useState('');
  const [editSearchTemplate, setEditSearchTemplate] = useState('');
  const [editNotes, setEditNotes] = useState('');
  const [savingEdit, setSavingEdit] = useState(false);

  // Form states for Add Source
  const [name, setName] = useState('');
  const [url, setUrl] = useState('');
  const [accessType, setAccessType] = useState('CAREER_PAGE');
  const [cguUrl, setCguUrl] = useState('');
  const [searchUrlTemplate, setSearchUrlTemplate] = useState('');
  const [notes, setNotes] = useState('');
  const [cssSelectors, setCssSelectors] = useState('');
  const [autoPublish, setAutoPublish] = useState(false);

  // Verification states
  const [checkingRobots, setCheckingRobots] = useState(false);
  const [robotsResult, setRobotsResult] = useState<{
    allowed: boolean;
    crawlDelaySeconds: number;
    status: string;
    message: string;
  } | null>(null);

  const [testingSample, setTestingSample] = useState(false);
  const [sampleResult, setSampleResult] = useState<{
    robotsAllowed: boolean;
    isJavascriptOnly: boolean;
    status: string;
    previewOffers: SampleOffer[];
    message: string;
  } | null>(null);

  const [isAllowed, setIsAllowed] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  const token = localStorage.getItem('fitwell_token');
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };

  const fetchSources = async () => {
    try {
      const res = await fetch('/api/admin/sources', { headers, credentials: 'include' });
      if (res.ok) {
        const data = await res.json();
        setSources(data.sources || []);
      }
    } catch (e) {
      console.error('Failed to load sources', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSources();
  }, []);

  const handleCheckRobots = async () => {
    if (!url) {
      alert('Please enter an offers page URL first.');
      return;
    }
    setCheckingRobots(true);
    setRobotsResult(null);
    try {
      const res = await fetch('/api/admin/sources/check-robots', {
        method: 'POST',
        headers,
        body: JSON.stringify({ url: url.trim() }),
      });
      const data = await res.json();
      setRobotsResult(data);
    } catch (e) {
      alert('Robots.txt check failed: ' + e);
    } finally {
      setCheckingRobots(false);
    }
  };

  const handleCheckRobotsForSource = async (s: Source) => {
    try {
      setActionMessage(`Checking robots.txt for "${s.name}"...`);
      const res = await fetch(`/api/admin/sources/${s.id}/check-robots`, {
        method: 'POST',
        headers,
      });
      const data = await res.json();
      setActionMessage(`Robots.txt check for "${s.name}": status ${data.status} (Crawl-delay: ${data.crawlDelaySeconds}s)`);
      fetchSources();
      setTimeout(() => setActionMessage(null), 5000);
    } catch (e) {
      alert('Failed to check robots.txt: ' + e);
    }
  };

  const handleTestSample = async () => {
    if (!url) {
      alert('Please enter an offers page URL first.');
      return;
    }
    setTestingSample(true);
    setSampleResult(null);
    try {
      const res = await fetch('/api/admin/sources/test-sample', {
        method: 'POST',
        headers,
        body: JSON.stringify({ url, cssSelectors: cssSelectors || null }),
      });
      const data = await res.json();
      setSampleResult(data);
    } catch (e) {
      alert('Sample test failed: ' + e);
    } finally {
      setTestingSample(false);
    }
  };

  const handleAddSource = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name || !url) {
      alert('Name and URL are mandatory.');
      return;
    }

    const isExempt = accessType === 'INTERNAL' || accessType === 'DEEPLINK';
    if (!isExempt && !cguUrl) {
      alert('CGU / Terms of Service URL is mandatory for this source type.');
      return;
    }

    setSubmitting(true);
    try {
      const res = await fetch('/api/admin/sources', {
        method: 'POST',
        headers,
        body: JSON.stringify({
          name,
          accessType,
          url,
          cguUrl,
          tosUrl: cguUrl,
          searchUrlTemplate,
          notes,
          cssSelectors: cssSelectors || null,
          autoPublish,
        }),
      });

      if (!res.ok) {
        const err = await res.json();
        alert('Error creating source: ' + (err.message || 'Unknown error'));
        setSubmitting(false);
        return;
      }

      const createdData = await res.json();
      const newSourceId = createdData.source?.id;

      if (newSourceId && isAllowed) {
        await fetch(`/api/admin/sources/${newSourceId}/allow`, {
          method: 'PUT',
          headers,
          body: JSON.stringify({ allowed: true }),
        });
      }

      setShowAddModal(false);
      resetForm();
      fetchSources();
      setActionMessage('Source successfully added!');
      setTimeout(() => setActionMessage(null), 4000);
    } catch (e) {
      alert('Failed to save source: ' + e);
    } finally {
      setSubmitting(false);
    }
  };

  const resetForm = () => {
    setName('');
    setUrl('');
    setAccessType('CAREER_PAGE');
    setCguUrl('');
    setSearchUrlTemplate('');
    setNotes('');
    setCssSelectors('');
    setAutoPublish(false);
    setRobotsResult(null);
    setSampleResult(null);
    setIsAllowed(false);
  };

  const openEditModal = (s: Source) => {
    setEditingSource(s);
    setEditTosUrl(s.tosUrl || s.cguUrl || '');
    setEditSearchTemplate(s.searchUrlTemplate || '');
    setEditNotes(s.notes || '');
  };

  const handleSaveEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingSource) return;

    setSavingEdit(true);
    try {
      const res = await fetch(`/api/admin/sources/${editingSource.id}`, {
        method: 'PUT',
        headers,
        body: JSON.stringify({
          tosUrl: editTosUrl.trim(),
          searchUrlTemplate: editSearchTemplate.trim(),
          notes: editNotes.trim(),
        }),
      });

      if (!res.ok) {
        const err = await res.json();
        alert('Error updating source: ' + (err.message || 'Unknown error'));
        setSavingEdit(false);
        return;
      }

      setEditingSource(null);
      fetchSources();
      setActionMessage(`Source "${editingSource.name}" updated successfully!`);
      setTimeout(() => setActionMessage(null), 3500);
    } catch (e) {
      alert('Failed to update source: ' + e);
    } finally {
      setSavingEdit(false);
    }
  };

  const handleToggleAllow = async (source: Source) => {
    const nextState = !source.isAllowed;
    try {
      const res = await fetch(`/api/admin/sources/${source.id}/allow`, {
        method: 'PUT',
        headers,
        body: JSON.stringify({ allowed: nextState }),
      });
      if (res.ok) {
        fetchSources();
        setActionMessage(`Source "${source.name}" ${nextState ? 'authorized' : 'deactivated'}.`);
        setTimeout(() => setActionMessage(null), 3500);
      } else {
        const err = await res.json();
        alert('Cannot toggle source: ' + (err.message || err.error || 'Check conditions'));
      }
    } catch (e) {
      alert('Failed to toggle source authorization: ' + e);
    }
  };

  const handleManualCrawl = async (source: Source) => {
    try {
      setActionMessage(`Running crawl for "${source.name}"...`);
      const res = await fetch(`/api/admin/sources/${source.id}/crawl`, {
        method: 'POST',
        headers,
      });
      const data = await res.json();
      if (data.summary) {
        setActionMessage(
          `Crawl finished for "${source.name}": ${data.summary.message} (Found: ${data.summary.itemsFound})`
        );
      }
      fetchSources();
      setTimeout(() => setActionMessage(null), 6000);
    } catch (e) {
      alert('Manual crawl failed: ' + e);
    }
  };

  const getAccessTypeBadge = (access?: string) => {
    switch (access?.toUpperCase()) {
      case 'INTERNAL':
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-purple-100 text-purple-900 border border-purple-300">INTERNAL</span>;
      case 'API':
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-indigo-100 text-indigo-900 border border-indigo-300">API</span>;
      case 'DEEPLINK':
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-sky-100 text-sky-900 border border-sky-300">DEEPLINK (Search Link)</span>;
      case 'REMOTE_FEED':
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-amber-100 text-amber-900 border border-amber-300">REMOTE FEED</span>;
      default:
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-100 text-emerald-900 border border-emerald-300">CAREER PAGE</span>;
    }
  };

  return (
    <PageShell
      title="Offer Source Catalog"
      subtitle="Configure and govern where offers come from: Internal postings, Jooble API, verified Career pages, Outbound search deep links, and Remote feeds."
      phase="Source Catalog & Access Governance"
      pdfPage={17}
    >
      <div className="space-y-6">
        {/* Toast / Notification Banner */}
        {actionMessage && (
          <div className="p-3 bg-[#5B1421] text-white rounded-lg text-xs font-semibold flex items-center justify-between shadow-md">
            <span>{actionMessage}</span>
            <button onClick={() => setActionMessage(null)} className="text-stone-300 hover:text-white">✕</button>
          </div>
        )}

        {/* Header Stats & Quick Add Button */}
        <div className="p-5 rounded-xl bg-[#DFCAAC] border border-[#C5AE90] shadow-sm flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
          <div>
            <div className="flex items-center gap-3 mb-1">
              <span className="font-serif font-bold text-base text-[#241814]">
                Source Catalog & Compliance Engine
              </span>
              <span className="px-2 py-0.5 rounded-full bg-[#E5D2BA] text-[#5B1421] text-xs font-semibold border border-[#C5AE90]">
                Strict robots.txt & ToS check
              </span>
            </div>
            <p className="text-xs text-[#66554D] max-w-2xl leading-relaxed">
              Every crawler respects robots.txt (≤ 1 req/5s, crawl-delay), requires verified ToS, and rejects Google/LinkedIn scraping.
              DEEPLINK sources never crawl and produce zero offer cards; they offer direct outbound student search links.
            </p>
          </div>

          <div className="flex gap-2">
            <button
              onClick={() => setShowAddModal(true)}
              className="px-4 py-2 bg-[#5B1421] hover:bg-[#461019] text-white rounded-lg text-xs font-semibold shadow transition whitespace-nowrap"
            >
              + Add a source
            </button>
          </div>
        </div>

        {/* Add Source Modal */}
        {showAddModal && (
          <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4">
            <div className="bg-[#EFE7D8] border border-[#C5AE90] rounded-2xl max-w-lg w-full p-6 shadow-xl space-y-4 max-h-[90vh] overflow-y-auto">
              <div className="flex justify-between items-center border-b border-[#C5AE90] pb-3">
                <h3 className="font-serif font-bold text-base text-[#241814]">Add New Offer Source</h3>
                <button onClick={() => setShowAddModal(false)} className="text-[#66554D] hover:text-[#241814] text-lg font-bold">✕</button>
              </div>

              <form onSubmit={handleAddSource} className="space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-[#241814] mb-1">Source Name *</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. OCP Group Careers, Univ Hassan II Stages"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-lg border border-[#C5AE90] bg-[#FAF6F0] focus:outline-none focus:ring-1 focus:ring-[#5B1421]"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-[#241814] mb-1">Access Type *</label>
                  <select
                    value={accessType}
                    onChange={(e) => setAccessType(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-lg border border-[#C5AE90] bg-[#FAF6F0] focus:outline-none focus:ring-1 focus:ring-[#5B1421]"
                  >
                    <option value="CAREER_PAGE">CAREER_PAGE (Verified company or university portal)</option>
                    <option value="DEEPLINK">DEEPLINK (Outbound search link only - no scraping)</option>
                    <option value="API">API (Direct provider API, e.g. Jooble)</option>
                    <option value="REMOTE_FEED">REMOTE_FEED (RSS / structured JSON job feed)</option>
                    <option value="INTERNAL">INTERNAL (Fitwell platform postings)</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-[#241814] mb-1">Offers Page / Portal URL *</label>
                  <div className="flex gap-2">
                    <input
                      type="url"
                      required
                      placeholder="https://company.ma/offres-stage"
                      value={url}
                      onChange={(e) => setUrl(e.target.value)}
                      className="flex-1 px-3 py-2 text-xs rounded-lg border border-[#C5AE90] bg-[#FAF6F0] focus:outline-none focus:ring-1 focus:ring-[#5B1421]"
                    />
                    {accessType !== 'INTERNAL' && accessType !== 'DEEPLINK' && (
                      <button
                        type="button"
                        disabled={checkingRobots || !url}
                        onClick={handleCheckRobots}
                        className="px-3 py-2 bg-[#705545] hover:bg-[#584134] disabled:opacity-50 text-white text-xs font-semibold rounded-lg whitespace-nowrap"
                      >
                        {checkingRobots ? 'Checking...' : 'Check robots.txt'}
                      </button>
                    )}
                  </div>
                </div>

                {robotsResult && (
                  <div
                    className={`p-3 rounded-lg border text-xs ${
                      robotsResult.allowed
                        ? 'bg-emerald-50 border-emerald-300 text-emerald-800'
                        : 'bg-rose-50 border-rose-300 text-rose-800'
                    }`}
                  >
                    <div className="font-semibold flex items-center justify-between">
                      <span>Status: {robotsResult.allowed ? '✓ ALLOWED' : '✗ DISALLOWED'}</span>
                      <span>Crawl-delay: {robotsResult.crawlDelaySeconds}s</span>
                    </div>
                    <p className="mt-1 text-[11px]">{robotsResult.message}</p>
                  </div>
                )}

                {accessType !== 'INTERNAL' && accessType !== 'DEEPLINK' && (
                  <div>
                    <label className="block text-xs font-semibold text-[#241814] mb-1">
                      CGU / Terms of Service (ToS) URL *
                    </label>
                    <input
                      type="url"
                      required={accessType !== 'INTERNAL' && accessType !== 'DEEPLINK'}
                      placeholder="https://company.ma/cgu or mentions-legales"
                      value={cguUrl}
                      onChange={(e) => setCguUrl(e.target.value)}
                      className="w-full px-3 py-2 text-xs rounded-lg border border-[#C5AE90] bg-[#FAF6F0] focus:outline-none focus:ring-1 focus:ring-[#5B1421]"
                    />
                  </div>
                )}

                {accessType === 'DEEPLINK' && (
                  <div>
                    <label className="block text-xs font-semibold text-[#241814] mb-1">
                      Search URL Template (Use {'{keywords}'} and {'{city}'})
                    </label>
                    <input
                      type="text"
                      placeholder="https://example.com/search?q={keywords}&location={city}"
                      value={searchUrlTemplate}
                      onChange={(e) => setSearchUrlTemplate(e.target.value)}
                      className="w-full px-3 py-2 text-xs font-mono rounded-lg border border-[#C5AE90] bg-[#FAF6F0] focus:outline-none focus:ring-1 focus:ring-[#5B1421]"
                    />
                    <p className="text-[10px] text-[#66554D] mt-1">
                      If unverified, leave empty: the source will fall back to its portal URL and be marked "template to verify".
                    </p>
                  </div>
                )}

                <div>
                  <label className="block text-xs font-semibold text-[#241814] mb-1">Notes / Verification status</label>
                  <input
                    type="text"
                    placeholder="e.g. Morocco coverage not confirmed, template to verify"
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-lg border border-[#C5AE90] bg-[#FAF6F0] focus:outline-none focus:ring-1 focus:ring-[#5B1421]"
                  />
                </div>

                {accessType === 'CAREER_PAGE' && (
                  <div>
                    <label className="block text-xs font-semibold text-[#241814] mb-1">
                      CSS Selectors (Optional, JSON format)
                    </label>
                    <textarea
                      rows={2}
                      placeholder='{"itemSelector": ".job-item", "titleSelector": "h3", "employerSelector": ".company"}'
                      value={cssSelectors}
                      onChange={(e) => setCssSelectors(e.target.value)}
                      className="w-full px-3 py-2 text-xs font-mono rounded-lg border border-[#C5AE90] bg-[#FAF6F0] focus:outline-none focus:ring-1 focus:ring-[#5B1421]"
                    />
                    <div className="flex justify-between items-center mt-1">
                      <span className="text-[10px] text-[#66554D]">
                        Order: (1) JSON-LD, (2) RSS, (3) CSS, (4) AI Fallback
                      </span>
                      <button
                        type="button"
                        disabled={testingSample || !url}
                        onClick={handleTestSample}
                        className="px-3 py-1 bg-[#8D6B4F] hover:bg-[#72533B] disabled:opacity-50 text-white text-[11px] font-semibold rounded-lg"
                      >
                        {testingSample ? 'Testing...' : 'Test on sample (max 5)'}
                      </button>
                    </div>
                  </div>
                )}

                {sampleResult && (
                  <div className="p-3 rounded-lg border border-[#C5AE90] bg-[#DFCAAC] space-y-2 text-xs">
                    <div className="flex justify-between items-center font-semibold text-[#241814]">
                      <span>Sample Preview: {sampleResult.previewOffers?.length || 0} offers found</span>
                      <span
                        className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                          sampleResult.isJavascriptOnly
                            ? 'bg-amber-600 text-white'
                            : sampleResult.previewOffers?.length > 0
                            ? 'bg-emerald-700 text-white'
                            : 'bg-stone-500 text-white'
                        }`}
                      >
                        {sampleResult.isJavascriptOnly ? 'unsupported (JavaScript)' : sampleResult.status}
                      </span>
                    </div>

                    {sampleResult.isJavascriptOnly && (
                      <div className="p-2 bg-amber-100 border border-amber-300 rounded text-amber-900 text-[11px]">
                        ⚠️ Offers do not appear in the static HTML received (page loaded via JavaScript). A direct search link will be offered instead.
                      </div>
                    )}
                  </div>
                )}

                <div className="space-y-2 pt-2 border-t border-[#C5AE90]">
                  <label className="flex items-start gap-2 cursor-pointer text-xs p-2.5 rounded-lg bg-[#DFCAAC] border border-[#C5AE90]">
                    <input
                      type="checkbox"
                      disabled={accessType !== 'INTERNAL' && accessType !== 'DEEPLINK' && (!robotsResult || !robotsResult.allowed)}
                      checked={isAllowed}
                      onChange={(e) => setIsAllowed(e.target.checked)}
                      className="mt-0.5 rounded text-[#5B1421] focus:ring-[#5B1421]"
                    />
                    <div>
                      <span className="font-bold text-[#241814]">allowed (Activate source)</span>
                      <p className="text-[11px] text-[#66554D]">
                        Only check after verifying robots.txt and ToS. A non-allowed source is never called or crawled.
                      </p>
                    </div>
                  </label>
                </div>

                <div className="flex justify-end gap-2 pt-2">
                  <button
                    type="button"
                    onClick={() => setShowAddModal(false)}
                    className="px-4 py-2 border border-[#C5AE90] rounded-lg text-xs font-semibold text-[#66554D] hover:bg-[#DFCAAC]"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={submitting}
                    className="px-4 py-2 bg-[#5B1421] hover:bg-[#461019] text-white rounded-lg text-xs font-semibold shadow disabled:opacity-50"
                  >
                    {submitting ? 'Saving...' : 'Add Source'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* Edit Modal */}
        {editingSource && (
          <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4">
            <div className="bg-[#EFE7D8] border border-[#C5AE90] rounded-2xl max-w-md w-full p-6 shadow-xl space-y-4">
              <div className="flex justify-between items-center border-b border-[#C5AE90] pb-3">
                <h3 className="font-serif font-bold text-base text-[#241814]">
                  Edit Source: {editingSource.name}
                </h3>
                <button onClick={() => setEditingSource(null)} className="text-[#66554D] hover:text-[#241814] text-lg font-bold">✕</button>
              </div>

              <form onSubmit={handleSaveEdit} className="space-y-3">
                <div>
                  <label className="block text-xs font-semibold text-[#241814] mb-1">
                    ToS / CGU URL
                  </label>
                  <input
                    type="url"
                    placeholder="https://company.ma/terms"
                    value={editTosUrl}
                    onChange={(e) => setEditTosUrl(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-lg border border-[#C5AE90] bg-[#FAF6F0]"
                  />
                </div>

                {editingSource.accessType === 'DEEPLINK' && (
                  <div>
                    <label className="block text-xs font-semibold text-[#241814] mb-1">
                      Search URL Template ({'{keywords}'}, {'{city}'})
                    </label>
                    <input
                      type="text"
                      placeholder="https://site.com/jobs?q={keywords}&city={city}"
                      value={editSearchTemplate}
                      onChange={(e) => setEditSearchTemplate(e.target.value)}
                      className="w-full px-3 py-2 text-xs font-mono rounded-lg border border-[#C5AE90] bg-[#FAF6F0]"
                    />
                  </div>
                )}

                <div>
                  <label className="block text-xs font-semibold text-[#241814] mb-1">
                    Notes
                  </label>
                  <textarea
                    rows={2}
                    placeholder="Notes on coverage or template status"
                    value={editNotes}
                    onChange={(e) => setEditNotes(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-lg border border-[#C5AE90] bg-[#FAF6F0]"
                  />
                </div>

                <div className="flex justify-end gap-2 pt-2 border-t border-[#C5AE90]">
                  <button
                    type="button"
                    onClick={() => setEditingSource(null)}
                    className="px-3 py-1.5 border border-[#C5AE90] rounded-lg text-xs font-semibold text-[#66554D]"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={savingEdit}
                    className="px-4 py-1.5 bg-[#5B1421] text-white rounded-lg text-xs font-semibold shadow disabled:opacity-50"
                  >
                    {savingEdit ? 'Saving...' : 'Save Changes'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* Sources Catalog Table */}
        <div className="rounded-xl bg-[#DFCAAC] border border-[#C5AE90] shadow-sm overflow-hidden">
          <div className="p-4 border-b border-[#C5AE90] flex justify-between items-center bg-[#D8C1A0]">
            <div>
              <h3 className="font-serif font-bold text-sm text-[#241814]">Offer Sources Catalog</h3>
              <p className="text-[11px] text-[#66554D]">
                Allowed by default: INTERNAL & DEEPLINK. API / CAREER_PAGE / REMOTE_FEED require filled ToS and ALLOWED robots.txt.
              </p>
            </div>
            <span className="text-xs font-semibold text-[#5B1421] bg-white/60 px-2.5 py-1 rounded-full border border-[#C5AE90]">
              {sources.length} sources registered
            </span>
          </div>

          {loading ? (
            <div className="p-8 text-center text-xs text-[#66554D]">Loading catalog...</div>
          ) : sources.length === 0 ? (
            <div className="p-8 text-center text-xs text-[#66554D]">
              No sources registered in catalog.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs text-[#241814]">
                <thead className="bg-[#E2CEB0] text-[11px] font-bold text-[#66554D] uppercase tracking-wider border-b border-[#C5AE90]">
                  <tr>
                    <th className="py-3 px-4">Source & Domain</th>
                    <th className="py-3 px-4">Access Type</th>
                    <th className="py-3 px-4">ToS / CGU</th>
                    <th className="py-3 px-4">Robots.txt</th>
                    <th className="py-3 px-4">Status & Notes</th>
                    <th className="py-3 px-4">Authorized</th>
                    <th className="py-3 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#C5AE90]">
                  {sources.map((s) => {
                    const isExempt = s.accessType === 'INTERNAL' || s.accessType === 'DEEPLINK';
                    const effectiveTos = s.tosUrl || s.cguUrl;
                    const hasTos = Boolean(effectiveTos && effectiveTos.trim().length > 0);
                    const isRobotsAllowed = s.robotsStatus === 'ALLOWED';
                    const canActivate = isExempt || (hasTos && isRobotsAllowed);

                    return (
                      <tr key={s.id} className="hover:bg-[#D8C1A0]/40 transition">
                        <td className="py-3 px-4 max-w-xs">
                          <div className="font-semibold text-sm text-[#241814] flex items-center gap-1.5">
                            <span>{s.name}</span>
                          </div>
                          <span className="text-[10px] text-stone-500 font-mono block">
                            {s.domain || 'no domain'}
                          </span>
                          <a
                            href={s.url}
                            target="_blank"
                            rel="noreferrer"
                            className="text-[11px] text-[#5B1421] hover:underline truncate block mt-0.5"
                          >
                            {s.url}
                          </a>
                        </td>

                        <td className="py-3 px-4">
                          {getAccessTypeBadge(s.accessType)}
                        </td>

                        <td className="py-3 px-4">
                          {isExempt ? (
                            <span className="text-[11px] text-[#66554D] italic">Exempt</span>
                          ) : hasTos ? (
                            <a
                              href={effectiveTos}
                              target="_blank"
                              rel="noreferrer"
                              className="text-[11px] text-emerald-800 hover:underline flex items-center gap-1 font-medium"
                            >
                              <span>✓ View ToS</span>
                              <span>↗</span>
                            </a>
                          ) : (
                            <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-rose-100 text-rose-800 border border-rose-300">
                              Missing ToS
                            </span>
                          )}
                        </td>

                        <td className="py-3 px-4">
                          {isExempt ? (
                            <span className="text-[11px] text-[#66554D] italic">No crawl</span>
                          ) : (
                            <div>
                              <span
                                className={`px-2 py-0.5 rounded text-[10px] font-semibold ${
                                  s.robotsStatus === 'ALLOWED'
                                    ? 'bg-emerald-100 text-emerald-800'
                                    : 'bg-rose-100 text-rose-800'
                                }`}
                              >
                                {s.robotsStatus || 'PENDING'}
                              </span>
                              {s.robotsCheckedAt && (
                                <span className="text-[10px] text-stone-500 block mt-0.5">
                                  {new Date(s.robotsCheckedAt).toLocaleDateString()}
                                </span>
                              )}
                            </div>
                          )}
                        </td>

                        <td className="py-3 px-4 max-w-xs">
                          <span className="text-[11px] font-medium text-[#241814] block">
                            {s.status}
                          </span>
                          {s.notes && (
                            <span className="text-[10px] text-[#8D6B4F] bg-[#FAF6F0] px-1.5 py-0.5 rounded border border-[#C5AE90]/50 block mt-1 line-clamp-1">
                              {s.notes}
                            </span>
                          )}
                          {s.alertMessage && (
                            <span className="text-[10px] text-rose-800 font-semibold block mt-0.5">
                              {s.alertMessage}
                            </span>
                          )}
                        </td>

                        <td className="py-3 px-4">
                          <span
                            className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                              s.isAllowed ? 'bg-emerald-700 text-white' : 'bg-stone-400 text-white'
                            }`}
                          >
                            {s.isAllowed ? 'ALLOWED' : 'DISABLED'}
                          </span>
                        </td>

                        <td className="py-3 px-4 text-right space-x-1.5 whitespace-nowrap">
                          {/* Toggle Allow button: disabled if conditions not met */}
                          <button
                            disabled={!s.isAllowed && !canActivate}
                            onClick={() => handleToggleAllow(s)}
                            title={
                              !s.isAllowed && !canActivate
                                ? 'Activation locked: Requires ToS URL and successful robots.txt check (ALLOWED)'
                                : s.isAllowed
                                ? 'Deactivate source'
                                : 'Authorize and activate source'
                            }
                            className={`px-2.5 py-1 text-[11px] font-semibold rounded border transition ${
                              !s.isAllowed && !canActivate
                                ? 'opacity-40 cursor-not-allowed bg-stone-200 text-stone-500 border-stone-300'
                                : 'border-[#C5AE90] hover:bg-[#C5AE90] text-[#241814]'
                            }`}
                          >
                            {s.isAllowed ? 'Deactivate' : 'Activate'}
                          </button>

                          {/* Edit ToS / Notes button */}
                          <button
                            onClick={() => openEditModal(s)}
                            className="px-2 py-1 text-[11px] font-semibold rounded bg-stone-100 border border-[#C5AE90] hover:bg-stone-200 text-[#241814]"
                          >
                            Edit
                          </button>

                          {/* Check robots button for non-exempt */}
                          {!isExempt && (
                            <button
                              onClick={() => handleCheckRobotsForSource(s)}
                              className="px-2 py-1 text-[11px] font-semibold rounded bg-[#705545] hover:bg-[#584134] text-white"
                            >
                              Check Robots
                            </button>
                          )}

                          {/* Crawl button if career page */}
                          {s.accessType === 'CAREER_PAGE' && (
                            <button
                              disabled={!s.isAllowed}
                              onClick={() => handleManualCrawl(s)}
                              className="px-2 py-1 text-[11px] font-semibold rounded bg-[#5B1421] disabled:opacity-30 hover:bg-[#461019] text-white"
                            >
                              Crawl
                            </button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </PageShell>
  );
};
