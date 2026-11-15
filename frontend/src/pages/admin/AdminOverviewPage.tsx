import React from 'react';
import { PageShell } from '../../components/common/PageShell';

export const AdminOverviewPage: React.FC = () => {
  return (
    <PageShell
      title="Platform health"
      subtitle="Overview of offers, active students, research laboratories, and applications"
      phase="Phase 6 (Admin Overview)"
      pdfPage={16}
      badgeLabel="Admin Security Guard"
    >
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
        <div className="p-4 rounded-xl bg-[#DFCAAC] border border-[#C5AE90]">
          <div className="text-xs text-[#66554D]">Live offers</div>
          <div className="font-serif text-3xl font-bold text-[#241814] mt-1">412</div>
          <div className="text-[11px] text-[#66554D] mt-1">118 posted by labs</div>
        </div>

        <div className="p-4 rounded-xl bg-[#DFCAAC] border border-[#C5AE90]">
          <div className="text-xs text-[#66554D]">Students</div>
          <div className="font-serif text-3xl font-bold text-[#241814] mt-1">3,860</div>
          <div className="text-[11px] text-[#2D6A4F] font-semibold mt-1">+212 this month</div>
        </div>

        <div className="p-4 rounded-xl bg-[#DFCAAC] border border-[#C5AE90]">
          <div className="text-xs text-[#66554D]">Labs</div>
          <div className="font-serif text-3xl font-bold text-[#241814] mt-1">96</div>
          <div className="text-[11px] text-[#66554D] mt-1">23 claimed</div>
        </div>

        <div className="p-4 rounded-xl bg-[#DFCAAC] border border-[#C5AE90]">
          <div className="text-xs text-[#66554D]">Applications</div>
          <div className="font-serif text-3xl font-bold text-[#791E2D] mt-1">1,540</div>
          <div className="text-[11px] text-[#66554D] mt-1">This semester</div>
        </div>
      </div>
    </PageShell>
  );
};

