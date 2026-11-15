import React from 'react';
import { PageShell } from '../../components/common/PageShell';

export const AdminLabsClaimsPage: React.FC = () => {
  return (
    <PageShell
      title="Labs on Fitwell"
      subtitle="Verify laboratory accounts, claim requests, and institutional email domain matching"
      phase="Phase 6 (Labs & Claims)"
      pdfPage={19}
      badgeLabel="Free Email Flagged"
    >
      <div className="space-y-4">
        <div className="p-4 rounded-xl bg-[#DFCAAC] border border-[#C5AE90] flex items-center justify-between">
          <div>
            <div className="font-serif font-bold text-sm text-[#241814]">Genomics Unit, Fès</div>
            <div className="text-xs text-[#66554D]">s.elidrissi@univ-fes.ma · 4 offers</div>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-xs font-semibold text-[#2D6A4F]">Domain matches</span>
            <button className="px-3 py-1.5 rounded-lg bg-[#5B1421] text-white text-xs font-bold hover:bg-[#48101A]">
              Approve
            </button>
          </div>
        </div>

        <div className="p-4 rounded-xl bg-[#DFCAAC] border border-[#C5AE90] space-y-2">
          <div className="flex items-center justify-between">
            <div>
              <div className="font-serif font-bold text-sm text-[#241814]">Materials Lab, Marrakech</div>
              <div className="text-xs text-[#66554D]">contact.ml@gmail.com · 2 offers</div>
            </div>
            <div className="flex items-center gap-3">
              <span className="text-xs font-semibold px-2 py-0.5 rounded bg-[#A31D1D]/15 text-[#A31D1D] border border-[#A31D1D]/30">
                Personal email
              </span>
              <button className="px-3 py-1.5 rounded-lg bg-[#5B1421] text-white text-xs font-bold hover:bg-[#48101A]">
                Approve
              </button>
            </div>
          </div>
          <div className="text-[11px] text-[#791E2D] bg-[#E2CEB0] p-2 rounded-lg border border-[#C5AE90]">
            💡 <span className="font-medium">Suggestion:</span> Free/personal email provider detected. Request a university faculty/staff page link to verify lab affiliation before approving.
          </div>
        </div>
      </div>
    </PageShell>
  );
};

