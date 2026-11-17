import React, { useState, useEffect } from 'react';
import { PageShell } from '../../components/common/PageShell';

interface ValidationItem {
  id: string;
  sourceId: string;
  sourceName: string;
  rawPageText: string;
  extractedFieldsJson: string;
  confidenceScore: number;
  duplicateOfOfferId?: string;
  status: string;
  createdAt: string;
}

export const AdminValidationPage: React.FC = () => {
  const [items, setItems] = useState<ValidationItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);

  const token = localStorage.getItem('fitwell_token');
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };

  const fetchQueue = async () => {
    try {
      const res = await fetch('/api/admin/validation-queue', { headers, credentials: 'include' });
      if (res.ok) {
        const data = await res.json();
        setItems(data.queue || []);
      }
    } catch (e) {
      console.error('Failed to load validation queue', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchQueue();
  }, []);

  const handleApprove = async (id: string) => {
    try {
      const res = await fetch(`/api/admin/validation-queue/${id}/approve`, {
        method: 'POST',
        headers,
      });
      if (res.ok) {
        setMessage('Offer successfully approved and published to Explore!');
        fetchQueue();
        setTimeout(() => setMessage(null), 3500);
      }
    } catch (e) {
      alert('Approval failed: ' + e);
    }
  };

  const handleReject = async (id: string) => {
    try {
      const res = await fetch(`/api/admin/validation-queue/${id}/reject`, {
        method: 'POST',
        headers,
      });
      if (res.ok) {
        setMessage('Offer rejected from queue.');
        fetchQueue();
        setTimeout(() => setMessage(null), 3500);
      }
    } catch (e) {
      alert('Rejection failed: ' + e);
    }
  };

  return (
    <PageShell
      title="Queue"
      subtitle="Side-by-side validation: original extracted page text vs parsed structured fields"
      phase="Phase C (Validation Queue)"
      pdfPage={18}
      badgeLabel="Duplicate Check"
    >
      <div className="space-y-6">
        {message && (
          <div className="p-3 bg-[#5B1421] text-white rounded-lg text-xs font-semibold flex items-center justify-between shadow">
            <span>{message}</span>
            <button onClick={() => setMessage(null)} className="text-stone-300 hover:text-white">✕</button>
          </div>
        )}

        {loading ? (
          <div className="p-8 text-center text-xs text-[#66554D]">Loading validation queue...</div>
        ) : items.length === 0 ? (
          <div className="p-12 text-center rounded-xl bg-[#DFCAAC] border border-[#C5AE90] text-xs text-[#66554D]">
            No offers currently waiting in the validation queue. All sourced offers are validated or auto-published.
          </div>
        ) : (
          items.map((item) => {
            let fields: any = {};
            try {
              fields = JSON.parse(item.extractedFieldsJson);
            } catch (ignored) {}

            return (
              <div
                key={item.id}
                className="p-5 rounded-xl bg-[#DFCAAC] border border-[#C5AE90] shadow-sm space-y-4"
              >
                <div className="flex justify-between items-center pb-2 border-b border-[#C5AE90]">
                  <div className="flex items-center gap-2">
                    <span className="font-serif font-bold text-sm text-[#241814]">
                      Source: {item.sourceName}
                    </span>
                    {item.duplicateOfOfferId && (
                      <span className="px-2 py-0.5 rounded text-[10px] bg-amber-200 text-amber-900 font-semibold border border-amber-400">
                        Duplicate Flagged
                      </span>
                    )}
                  </div>

                  <div className="flex items-center gap-2">
                    <span className="px-2 py-0.5 rounded-full bg-[#5B1421] text-white text-[11px] font-bold">
                      {item.confidenceScore}% Confidence
                    </span>
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {/* Original Page Column */}
                  <div className="p-4 rounded-xl bg-[#E2CEB0] border border-[#C5AE90]">
                    <h4 className="text-xs font-bold uppercase tracking-wider text-[#66554D] mb-2">
                      Original Page Excerpt (Scraped & Sanitized)
                    </h4>
                    <p className="text-xs text-[#241814] leading-relaxed italic bg-[#FAF6F0] p-3 rounded-lg border border-[#C5AE90]">
                      "{item.rawPageText}"
                    </p>
                  </div>

                  {/* AI / Sourced Structured Fields Column */}
                  <div className="p-4 rounded-xl bg-[#E2CEB0] border border-[#C5AE90]">
                    <h4 className="text-xs font-bold uppercase tracking-wider text-[#66554D] mb-2">
                      Structured Fields (Extracted)
                    </h4>
                    <div className="space-y-1.5 text-xs text-[#241814] bg-[#FAF6F0] p-3 rounded-lg border border-[#C5AE90]">
                      <div>
                        <span className="font-semibold text-stone-600">Title:</span> {fields.title || 'N/A'}
                      </div>
                      <div>
                        <span className="font-semibold text-stone-600">Employer:</span> {fields.employer || 'N/A'}
                      </div>
                      <div>
                        <span className="font-semibold text-stone-600">Location:</span> {fields.location || 'N/A'}
                      </div>
                      <div>
                        <span className="font-semibold text-stone-600">Duration:</span>{' '}
                        {fields.durationMonths ? `${fields.durationMonths} months` : 'N/A'}
                      </div>
                      <div>
                        <span className="font-semibold text-stone-600">Extraction Level:</span>{' '}
                        {fields.extractionLevel || 'AI_FALLBACK'}
                      </div>
                      {fields.canonicalUrl && (
                        <div>
                          <span className="font-semibold text-stone-600">Canonical URL:</span>{' '}
                          <a
                            href={fields.canonicalUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="text-[#5B1421] hover:underline"
                          >
                            {fields.canonicalUrl}
                          </a>
                        </div>
                      )}
                    </div>
                  </div>
                </div>

                {/* Validation Actions */}
                <div className="flex justify-end gap-2 pt-2">
                  <button
                    onClick={() => handleReject(item.id)}
                    className="px-4 py-1.5 border border-rose-300 bg-rose-50 hover:bg-rose-100 text-rose-800 text-xs font-semibold rounded-lg transition"
                  >
                    Reject Offer
                  </button>
                  <button
                    onClick={() => handleApprove(item.id)}
                    className="px-4 py-1.5 bg-[#5B1421] hover:bg-[#461019] text-white text-xs font-semibold rounded-lg shadow transition"
                  >
                    Approve & Publish Offer
                  </button>
                </div>
              </div>
            );
          })
        )}
      </div>
    </PageShell>
  );
};
