import React from 'react';
import { PageShell } from '../../components/common/PageShell';

export const StudentApplicationsPage: React.FC = () => {
  return (
    <PageShell
      title="Where you stand"
      subtitle="Track your applications, timelines, and next suggested actions"
      phase="Phase 4 (Applications & Assistant)"
      pdfPage={7}
      badgeLabel="Timeline Tracked"
    >
      <div className="space-y-4">
        <div className="p-4 rounded-xl bg-[#DFCAAC] border border-[#C5AE90] flex items-center justify-between">
          <div>
            <h3 className="font-serif text-lg font-bold text-[#241814]">Gene expression analysis</h3>
            <p className="text-xs text-[#66554D]">Genomics Unit, Fès</p>
          </div>
          <span className="px-3 py-1 rounded-full bg-[#791E2D] text-white text-xs font-semibold">
            Interview
          </span>
        </div>

        <div className="p-4 rounded-xl bg-[#DFCAAC] border border-[#C5AE90] flex items-center justify-between">
          <div>
            <h3 className="font-serif text-lg font-bold text-[#241814]">Thin-film solar cell testing</h3>
            <p className="text-xs text-[#66554D]">Materials Lab, Marrakech</p>
          </div>
          <span className="px-3 py-1 rounded-full bg-[#DFCAAC] text-[#241814] border border-[#BFA789] text-xs font-semibold">
            Sent
          </span>
        </div>
      </div>
    </PageShell>
  );
};

