import React from 'react';
import { PageShell } from '../../components/common/PageShell';

export const AdminRulesPage: React.FC = () => {
  return (
    <PageShell
      title="How the platform decides"
      subtitle="Configure automatic offer publication thresholds, duplicate blocks, and sourcing rules"
      phase="Phase 6 (AI & Rules)"
      pdfPage={20}
      badgeLabel="Rules Engine"
    >
      <div className="space-y-4 text-xs text-[#241814]">
        <div className="p-4 rounded-xl bg-[#DFCAAC] border border-[#C5AE90] flex items-center justify-between">
          <div>
            <div className="font-bold text-sm">Confidence threshold: 95%</div>
            <div className="text-[#66554D]">Below this, an offer goes to the validation queue.</div>
          </div>
          <span className="font-bold text-[#5B1421]">Active</span>
        </div>

        <div className="p-4 rounded-xl bg-[#DFCAAC] border border-[#C5AE90] flex items-center justify-between">
          <div>
            <div className="font-bold text-sm">Block duplicates</div>
            <div className="text-[#66554D]">Offers matching content_hash are held back.</div>
          </div>
          <span className="font-bold text-[#5B1421]">Active</span>
        </div>

        <div className="p-4 rounded-xl bg-[#DFCAAC] border border-[#C5AE90] flex items-center justify-between">
          <div>
            <div className="font-bold text-sm">Flag free email claims</div>
            <div className="text-[#66554D]">Claims from personal addresses (@gmail, @yahoo) need admin check.</div>
          </div>
          <span className="font-bold text-[#5B1421]">Active</span>
        </div>
      </div>
    </PageShell>
  );
};

