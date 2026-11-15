import React from 'react';
import { PageShell } from '../../components/common/PageShell';

export const LabDashboardPage: React.FC = () => {
  return (
    <PageShell
      title="Good morning, Dr. El Idrissi"
      subtitle="Genomics Unit, Fès — Real-time laboratory activity and candidate overview"
      phase="Phase 5 (Lab Space)"
      pdfPage={11}
      badgeLabel="Lab Verified"
    >
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
        <div className="p-4 rounded-xl bg-[#DFCAAC] border border-[#C5AE90]">
          <div className="text-xs text-[#66554D]">Open offers</div>
          <div className="font-serif text-3xl font-bold text-[#241814] mt-1">3</div>
        </div>

        <div className="p-4 rounded-xl bg-[#DFCAAC] border border-[#C5AE90]">
          <div className="text-xs text-[#66554D]">New applicants</div>
          <div className="font-serif text-3xl font-bold text-[#241814] mt-1">18</div>
        </div>

        <div className="p-4 rounded-xl bg-[#DFCAAC] border border-[#C5AE90]">
          <div className="text-xs text-[#66554D]">To review</div>
          <div className="font-serif text-3xl font-bold text-[#791E2D] mt-1">7</div>
        </div>

        <div className="p-4 rounded-xl bg-[#DFCAAC] border border-[#C5AE90]">
          <div className="text-xs text-[#66554D]">Interviews this week</div>
          <div className="font-serif text-3xl font-bold text-[#241814] mt-1">2</div>
        </div>
      </div>
    </PageShell>
  );
};

