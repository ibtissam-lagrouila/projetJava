import React from 'react';
import { PageShell } from '../../components/common/PageShell';

export const LabInterviewsPage: React.FC = () => {
  return (
    <PageShell
      title="Week of 16 November"
      subtitle="Laboratory interview schedule and smart slot proposal from entered availability"
      phase="Phase 5 (Interviews & Scheduling)"
      pdfPage={15}
    >
      <div className="grid grid-cols-5 gap-2 text-center text-xs">
        <div className="p-3 rounded-lg bg-[#DFCAAC] border border-[#C5AE90]">
          <span className="font-bold text-[#66554D]">Mon 16</span>
          <div className="mt-4 text-[#8D7A68]">No slots</div>
        </div>
        <div className="p-3 rounded-lg bg-[#DFCAAC] border border-[#C5AE90]">
          <span className="font-bold text-[#66554D]">Tue 17</span>
          <div className="mt-2 p-1.5 bg-[#E2CEB0] rounded text-[11px] font-semibold text-[#241814]">10:00 Hamza K.</div>
        </div>
        <div className="p-3 rounded-lg bg-[#DFCAAC] border border-[#C5AE90]">
          <span className="font-bold text-[#66554D]">Wed 18</span>
          <div className="mt-2 p-1.5 bg-[#5B1421] text-white rounded text-[11px] font-semibold">10:00 Yassine B.</div>
        </div>
        <div className="p-3 rounded-lg bg-[#DFCAAC] border border-[#C5AE90]">
          <span className="font-bold text-[#66554D]">Thu 19</span>
          <div className="mt-2 p-1.5 bg-[#E2CEB0] rounded text-[11px] font-semibold text-[#241814]">14:00 Nour A.</div>
        </div>
        <div className="p-3 rounded-lg bg-[#DFCAAC] border border-[#C5AE90]">
          <span className="font-bold text-[#66554D]">Fri 20</span>
          <div className="mt-4 text-[#8D7A68]">No slots</div>
        </div>
      </div>
    </PageShell>
  );
};

