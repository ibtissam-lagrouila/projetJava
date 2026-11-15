import React from 'react';
import { PageShell } from '../../components/common/PageShell';

export const StudentMessagesPage: React.FC = () => {
  return (
    <PageShell
      title="Messages"
      subtitle="Direct conversations with research lab directors and supervisors"
      phase="Phase 4 (Messages & Assistant)"
      pdfPage={8}
      badgeLabel="Drafts Private"
    >
      <div className="p-4 rounded-xl bg-[#DFCAAC] border border-[#C5AE90]">
        <h3 className="font-serif text-lg font-bold text-[#241814] mb-1">
          Dr. Salma El Idrissi — Genomics Unit, Fès
        </h3>
        <p className="text-xs text-[#66554D] mb-3">
          Gene expression analysis · 3 slots for interview proposed
        </p>
        <div className="p-3 rounded-lg bg-[#E2CEB0] text-xs text-[#241814] italic">
          "Hello Yassine, thank you for your application. Your RNA-seq project caught our attention..."
        </div>
      </div>
    </PageShell>
  );
};

