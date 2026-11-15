import React from 'react';
import { PageShell } from '../../components/common/PageShell';

export const LabPostOfferPage: React.FC = () => {
  return (
    <PageShell
      title="Describe the internship"
      subtitle="Publish a new research internship offer with live preview and quality score"
      phase="Phase 5 (Post an Offer)"
      pdfPage={12}
    >
      <div className="space-y-4">
        {/* Start from an idea box */}
        <div className="p-4 rounded-xl bg-[#5B1421] text-white">
          <div className="text-xs font-semibold uppercase tracking-wider text-[#E2CEB0] mb-1">
            Start from an idea
          </div>
          <div className="text-sm">
            We need a student to analyse RNA-seq data from our mouse cohort and build a small pipeline.
          </div>
          <button className="mt-3 px-3 py-1.5 rounded-lg bg-[#E2CEB0] text-[#5B1421] text-xs font-bold hover:bg-white">
            Draft the offer
          </button>
        </div>

        <div className="text-xs text-[#66554D]">
          Offer quality score is computed deterministically from field completeness (duration, tasks, skills, supervisor info).
        </div>
      </div>
    </PageShell>
  );
};

