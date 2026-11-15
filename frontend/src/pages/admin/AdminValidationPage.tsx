import React from 'react';
import { PageShell } from '../../components/common/PageShell';

export const AdminValidationPage: React.FC = () => {
  return (
    <PageShell
      title="Queue"
      subtitle="Side-by-side validation: original extracted page text vs parsed structured fields"
      phase="Phase 6 (Validation Queue)"
      pdfPage={18}
      badgeLabel="Duplicate Check"
    >
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Original Page Column */}
        <div className="p-4 rounded-xl bg-[#DFCAAC] border border-[#C5AE90]">
          <h4 className="text-xs font-bold uppercase tracking-wider text-[#66554D] mb-2">
            Original Page (Scraped)
          </h4>
          <p className="text-xs text-[#241814] leading-relaxed italic bg-[#E2CEB0] p-3 rounded-lg border border-[#C5AE90]">
            "Stage de recherche en bio-informatique. Durée : 5 mois à partir de février. Lieu : Fès. Profil : étudiant en master, connaissance de R et Python. Gratification mensuelle."
          </p>
        </div>

        {/* AI Extracted Fields Column */}
        <div className="p-4 rounded-xl bg-[#DFCAAC] border border-[#C5AE90]">
          <div className="flex justify-between items-center mb-2">
            <h4 className="text-xs font-bold uppercase tracking-wider text-[#66554D]">
              What the AI Extracted
            </h4>
            <span className="px-2 py-0.5 rounded-full bg-[#5B1421] text-white text-[11px] font-bold">
              96% Confidence
            </span>
          </div>
          <div className="space-y-1.5 text-xs text-[#241814]">
            <div><span className="font-semibold">Title:</span> Bioinformatics research internship</div>
            <div><span className="font-semibold">Duration:</span> 5 months</div>
            <div><span className="font-semibold">Location:</span> Fès</div>
            <div><span className="font-semibold">Skills:</span> R, Python</div>
          </div>
        </div>
      </div>
    </PageShell>
  );
};

