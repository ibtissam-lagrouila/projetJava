import React, { useState, useEffect } from 'react';
import { PageShell } from '../../components/common/PageShell';

interface Source {
  id: string;
  name: string;
  url: string;
  cguUrl?: string;
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

  // Form states
  const [name, setName] = useState('');
  const [url, setUrl] = useState('');
  const [cguUrl, setCguUrl] = useState('');
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
        body: JSON.stringify({ url }),
      });
      const data = await res.json();
      setRobotsResult(data);
    } catch (e) {
      alert('Failed to check robots.txt: ' + e);
    } finally {
      setCheckingRobots(false);
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
    if (!name || !url || !cguUrl) {
      alert('Name, URL, and CGU URL are mandatory.');
      return;
    }

    setSubmitting(true);
    try {
      const res = await fetch('/api/admin/sources', {
        method: 'POST',
        headers,
        body: JSON.stringify({
          name,
          url,
          cguUrl,
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

      // If admin checked allowed and both checks succeeded, activate it
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
    setCguUrl('');
    setCssSelectors('');
    setAutoPublish(false);
    setRobotsResult(null);
    setSampleResult(null);
    setIsAllowed(false);
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

  return (
    <PageShell
      title="Where offers come from"
      subtitle="Career sites, university portals, RSS feeds, robots.txt compliance, and scheduled Jsoup scraper"
      phase="Phase C (CareerPageSource)"
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

        {/* Nightly Scheduled Scraper & Controls Card */}
        <div className="p-5 rounded-xl bg-[#DFCAAC] border border-[#C5AE90] shadow-sm flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
          <div>
            <div className="flex items-center gap-3 mb-1">
              <span className="font-serif font-bold text-base text-[#241814]">
                Nightly job (ScheduledExecutorService)
              </span>
              <span className="px-2 py-0.5 rounded-full bg-[#E5D2BA] text-[#5B1421] text-xs font-semibold border border-[#C5AE90]">
                Runs every night at 03:00
              </span>
            </div>
            <p className="text-xs text-[#66554D] max-w-2xl leading-relaxed">
              Respects robots.txt (including Crawl-delay), strictly protected against SSRF (no loopback, private CIDR ranges, or metadata services). Rate-limited to at most 1 request every 5 seconds per domain.
            </p>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={() => setShowAddModal(true)}
              className="px-4 py-2 bg-[#5B1421] hover:bg-[#461019] text-white text-xs font-semibold rounded-lg shadow transition flex items-center gap-2"
            >
              <span>+</span>
              <span>Add a source</span>
            </button>
          </div>
        </div>

        {/* Modal: Add a Source */}
        {showAddModal && (
          <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm flex items-center justify-center p-4">
            <div className="bg-[#EBE0D0] border border-[#C5AE90] rounded-2xl p-6 max-w-2xl w-full max-h-[90vh] overflow-y-auto shadow-2xl">
              <div className="flex justify-between items-center mb-4 pb-2 border-b border-[#C5AE90]">
                <div>
                  <h3 className="font-serif font-bold text-lg text-[#241814]">Add a Career Page Source</h3>
                  <p className="text-xs text-[#66554D]">
                    Phase C: Only company careers or university portals authorized by Admin with validated robots.txt
                  </p>
                </div>
                <button
                  onClick={() => setShowAddModal(false)}
                  className="text-[#66554D] hover:text-[#241814] text-lg font-bold"
                >
                  ✕
                </button>
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
                  <label className="block text-xs font-semibold text-[#241814] mb-1">Offers Page URL *</label>
                  <div className="flex gap-2">
                    <input
                      type="url"
                      required
                      placeholder="https://company.ma/offres-stage"
                      value={url}
                      onChange={(e) => setUrl(e.target.value)}
                      className="flex-1 px-3 py-2 text-xs rounded-lg border border-[#C5AE90] bg-[#FAF6F0] focus:outline-none focus:ring-1 focus:ring-[#5B1421]"
                    />
                    <button
                      type="button"
                      disabled={checkingRobots || !url}
                      onClick={handleCheckRobots}
                      className="px-3 py-2 bg-[#705545] hover:bg-[#584134] disabled:opacity-50 text-white text-xs font-semibold rounded-lg whitespace-nowrap"
                    >
                      {checkingRobots ? 'Checking...' : 'Check robots.txt'}
                    </button>
                  </div>
                </div>

                {/* Robots.txt check result feedback */}
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

                <div>
                  <label className="block text-xs font-semibold text-[#241814] mb-1">
                    CGU / Terms of Service URL *
                  </label>
                  <input
                    type="url"
                    required
                    placeholder="https://company.ma/cgu or mentions-legales"
                    value={cguUrl}
                    onChange={(e) => setCguUrl(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-lg border border-[#C5AE90] bg-[#FAF6F0] focus:outline-none focus:ring-1 focus:ring-[#5B1421]"
                  />
                </div>

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
                      Extraction order: (1) JSON-LD JobPosting, (2) RSS/Sitemap, (3) CSS, (4) AI Fallback
                    </span>
                    <button
                      type="button"
                      disabled={testingSample || !url}
                      onClick={handleTestSample}
                      className="px-3 py-1 bg-[#8D6B4F] hover:bg-[#72533B] disabled:opacity-50 text-white text-[11px] font-semibold rounded-lg"
                    >
                      {testingSample ? 'Testing...' : 'Test on a sample (max 5)'}
                    </button>
                  </div>
                </div>

                {/* Sample Test Result Preview */}
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

                    {sampleResult.previewOffers?.length > 0 && (
                      <div className="space-y-1 max-h-36 overflow-y-auto pr-1">
                        {sampleResult.previewOffers.map((o, idx) => (
                          <div key={idx} className="p-2 rounded bg-white/70 border border-[#C5AE90]/50 text-[11px]">
                            <div className="font-bold text-[#5B1421]">{o.title}</div>
                            <div className="text-stone-600 text-[10px]">
                              {o.employer} · {o.location} · {o.durationMonths} mois · Level: {o.extractionLevel} ({o.confidenceScore}%)
                            </div>
                            <div className="text-stone-500 text-[10px] italic mt-0.5 line-clamp-1">{o.excerpt}</div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}

                <div className="space-y-2 pt-2 border-t border-[#C5AE90]">
                  <label className="flex items-center gap-2 cursor-pointer text-xs text-[#241814]">
                    <input
                      type="checkbox"
                      checked={autoPublish}
                      onChange={(e) => setAutoPublish(e.target.checked)}
                      className="rounded text-[#5B1421] focus:ring-[#5B1421]"
                    />
                    <span>Auto-publish offers directly if confidence score ≥ 95%</span>
                  </label>

                  {/* Authorization checkbox */}
                  <label className="flex items-start gap-2 cursor-pointer text-xs p-2.5 rounded-lg bg-[#DFCAAC] border border-[#C5AE90]">
                    <input
                      type="checkbox"
                      disabled={!robotsResult || !robotsResult.allowed}
                      checked={isAllowed}
                      onChange={(e) => setIsAllowed(e.target.checked)}
                      className="mt-0.5 rounded text-[#5B1421] focus:ring-[#5B1421]"
                    />
                    <div>
                      <span className="font-bold text-[#241814]">allowed (Activate source)</span>
                      <p className="text-[11px] text-[#66554D]">
                        Only check after verifying robots.txt and CGU. A non-allowed source is never called or crawled.
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

        {/* Sources Table */}
        <div className="rounded-xl bg-[#DFCAAC] border border-[#C5AE90] shadow-sm overflow-hidden">
          <div className="p-4 border-b border-[#C5AE90] flex justify-between items-center bg-[#D8C1A0]">
            <h3 className="font-serif font-bold text-sm text-[#241814]">Active & Configured Sourcing Portals</h3>
            <span className="text-xs text-[#66554D]">{sources.length} sources registered</span>
          </div>

          {loading ? (
            <div className="p-8 text-center text-xs text-[#66554D]">Loading sources...</div>
          ) : sources.length === 0 ? (
            <div className="p-8 text-center text-xs text-[#66554D]">
              No external sources configured. Click "+ Add a source" to add one.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs text-[#241814]">
                <thead className="bg-[#E2CEB0] text-[11px] font-bold text-[#66554D] uppercase tracking-wider border-b border-[#C5AE90]">
                  <tr>
                    <th className="py-3 px-4">Source & URL</th>
                    <th className="py-3 px-4">Status & Alerts</th>
                    <th className="py-3 px-4">Robots.txt</th>
                    <th className="py-3 px-4">Rate & Delay</th>
                    <th className="py-3 px-4">Accuracy</th>
                    <th className="py-3 px-4">Authorized</th>
                    <th className="py-3 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#C5AE90]">
                  {sources.map((s) => {
                    const isPaused = s.status === 'PAUSED' || s.status === 'FAILING';
                    const isJsOnly = s.status === 'unsupported (JavaScript)';

                    return (
                      <tr key={s.id} className="hover:bg-[#D8C1A0]/40 transition">
                        <td className="py-3 px-4">
                          <div className="font-semibold text-sm text-[#241814]">{s.name}</div>
                          <a
                            href={s.url}
                            target="_blank"
                            rel="noreferrer"
                            className="text-[11px] text-[#5B1421] hover:underline truncate max-w-xs block"
                          >
                            {s.url}
                          </a>
                          {s.cguUrl && (
                            <a
                              href={s.cguUrl}
                              target="_blank"
                              rel="noreferrer"
                              className="text-[10px] text-[#8D6B4F] hover:underline"
                            >
                              CGU / Terms ↗
                            </a>
                          )}
                        </td>

                        <td className="py-3 px-4">
                          {isPaused ? (
                            <div>
                              <span className="px-2 py-0.5 rounded-full bg-rose-700 text-white font-bold text-[10px]">
                                Source paused
                              </span>
                              {s.alertMessage && (
                                <p className="text-[10px] text-rose-900 mt-1 font-medium">{s.alertMessage}</p>
                              )}
                            </div>
                          ) : isJsOnly ? (
                            <div>
                              <span className="px-2 py-0.5 rounded-full bg-amber-600 text-white font-bold text-[10px]">
                                unsupported (JavaScript)
                              </span>
                              <p className="text-[10px] text-amber-900 mt-1">Direct search link offered</p>
                            </div>
                          ) : (
                            <span className="px-2 py-0.5 rounded-full bg-emerald-700 text-white font-bold text-[10px]">
                              {s.status}
                            </span>
                          )}
                        </td>

                        <td className="py-3 px-4">
                          <span
                            className={`px-2 py-0.5 rounded text-[10px] font-semibold ${
                              s.robotsStatus === 'ALLOWED'
                                ? 'bg-emerald-100 text-emerald-800'
                                : 'bg-rose-100 text-rose-800'
                            }`}
                          >
                            {s.robotsStatus || 'PENDING'}
                          </span>
                        </td>

                        <td className="py-3 px-4 text-[#66554D]">
                          <div>≤ 1 req / 5s</div>
                          <div className="text-[10px]">Delay: {s.crawlDelaySeconds || 5}s</div>
                        </td>

                        <td className="py-3 px-4 font-mono font-bold text-xs">{s.accuracyPct || 100}%</td>

                        <td className="py-3 px-4">
                          <span
                            className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                              s.isAllowed ? 'bg-emerald-700 text-white' : 'bg-stone-400 text-white'
                            }`}
                          >
                            {s.isAllowed ? 'ALLOWED' : 'REVOKED'}
                          </span>
                        </td>

                        <td className="py-3 px-4 text-right space-x-2">
                          <button
                            onClick={() => handleToggleAllow(s)}
                            className="px-2.5 py-1 text-[11px] font-semibold rounded border border-[#C5AE90] hover:bg-[#C5AE90] text-[#241814]"
                          >
                            {s.isAllowed ? 'Revoke' : 'Allow'}
                          </button>
                          <button
                            disabled={!s.isAllowed}
                            onClick={() => handleManualCrawl(s)}
                            className="px-2.5 py-1 text-[11px] font-semibold rounded bg-[#5B1421] disabled:opacity-40 hover:bg-[#461019] text-white"
                          >
                            Run Crawl
                          </button>
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
