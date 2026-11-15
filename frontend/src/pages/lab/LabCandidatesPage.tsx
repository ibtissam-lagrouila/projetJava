import React from 'react';
import { PageShell } from '../../components/common/PageShell';

export const LabCandidatesPage: React.FC = () => {
  return (
    <PageShell
      title="Gene expression analysis"
      subtitle="Kanban board of candidates with deterministic 'Rank by fit' scoring"
      phase="Phase 5 (Candidates & Kanban)"
      pdfPage={13}
      badgeLabel="Kanban Active"
    >
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        {/* Column 1: New */}
        <div className="bg-[#DFCAAC] p-3 rounded-xl border border-[#C5AE90]">
          <div className="flex justify-between items-center mb-2 font-bold text-xs text-[#241814]">
            <span>New</span>
            <span className="text-[#791E2D]">6</span>
          </div>
          <div className="p-3 bg-[#E2CEB0] rounded-lg shadow-sm border border-[#C5AE90]">
            <div className="font-semibold text-xs text-[#241814]">Imane Rafik</div>
            <div className="text-[11px] text-[#66554D]">Licence 3, statistics</div>
            <div className="mt-1 text-xs font-bold text-[#791E2D]">79% match</div>
          </div>
        </div>

        {/* Column 2: Shortlisted */}
        <div className="bg-[#DFCAAC] p-3 rounded-xl border border-[#C5AE90]">
          <div className="flex justify-between items-center mb-2 font-bold text-xs text-[#241814]">
            <span>Shortlisted</span>
            <span className="text-[#791E2D]">3</span>
          </div>
          <div className="p-3 bg-[#E2CEB0] rounded-lg shadow-sm border border-[#C5AE90]">
            <div className="font-semibold text-xs text-[#241814]">Hamza Kabbaj</div>
            <div className="text-[11px] text-[#66554D]">Master 2, biology</div>
            <div className="mt-1 text-xs font-bold text-[#791E2D]">84% match</div>
          </div>
        </div>

        {/* Column 3: Interview */}
        <div className="bg-[#DFCAAC] p-3 rounded-xl border border-[#C5AE90]">
          <div className="flex justify-between items-center mb-2 font-bold text-xs text-[#241814]">
            <span>Interview</span>
            <span className="text-[#791E2D]">2</span>
          </div>
          <div className="p-3 bg-[#E2CEB0] rounded-lg shadow-sm border border-[#C5AE90]">
            <div className="font-semibold text-xs text-[#241814]">Yassine Bennis</div>
            <div className="text-[11px] text-[#66554D]">Master 1, data science</div>
            <div className="mt-1 text-xs font-bold text-[#791E2D]">91% match</div>
          </div>
        </div>

        {/* Column 4: Decision */}
        <div className="bg-[#DFCAAC] p-3 rounded-xl border border-[#C5AE90]">
          <div className="flex justify-between items-center mb-2 font-bold text-xs text-[#241814]">
            <span>Decision</span>
            <span className="text-[#791E2D]">1</span>
          </div>
          <div className="p-3 bg-[#E2CEB0] rounded-lg shadow-sm border border-[#C5AE90]">
            <div className="font-semibold text-xs text-[#241814]">Oumaima Sefrioui</div>
            <div className="text-[11px] text-[#66554D]">Master 2, genomics</div>
            <div className="mt-1 text-xs font-bold text-[#791E2D]">86% match</div>
          </div>
        </div>
      </div>
    </PageShell>
  );
};

