import React from 'react';
import { PageShell } from '../../components/common/PageShell';

export const StudentCvStudioPage: React.FC = () => {
  return (
    <PageShell
      title="CV studio"
      subtitle="Master CV and tailored versions per research topic with strictly truthful suggestions"
      phase="Phase 4 (CV Studio)"
      pdfPage={9}
    >
      <div className="space-y-4">
        <div className="flex items-center justify-between p-4 rounded-xl bg-[#DFCAAC] border border-[#C5AE90]">
          <div>
            <h3 className="font-serif text-lg font-bold text-[#241814]">Master CV</h3>
            <p className="text-xs text-[#66554D]">General · 79 score baseline</p>
          </div>
          <button className="px-3 py-1.5 rounded-lg bg-[#5B1421] text-white text-xs font-semibold">
            Edit
          </button>
        </div>

        <div className="flex items-center justify-between p-4 rounded-xl bg-[#DFCAAC] border border-[#C5AE90]">
          <div>
            <h3 className="font-serif text-lg font-bold text-[#241814]">Gene expression analysis</h3>
            <p className="text-xs text-[#66554D]">Tailored · 88 match score</p>
          </div>
          <span className="text-xs text-[#791E2D] font-bold">Tailored</span>
        </div>
      </div>
    </PageShell>
  );
};

