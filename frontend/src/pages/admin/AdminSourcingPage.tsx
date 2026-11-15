import React from 'react';
import { PageShell } from '../../components/common/PageShell';

export const AdminSourcingPage: React.FC = () => {
  return (
    <PageShell
      title="Where offers come from"
      subtitle="External web sources, RSS feeds, accuracy rates, and scheduled nightly Jsoup scraper"
      phase="Phase 6 (Sourcing Engine)"
      pdfPage={17}
    >
      <div className="space-y-4">
        <div className="p-4 rounded-xl bg-[#DFCAAC] border border-[#C5AE90]">
          <div className="flex justify-between items-center mb-2">
            <span className="font-bold text-sm text-[#241814]">Nightly job (ScheduledExecutorService)</span>
            <span className="text-xs text-[#66554D]">Runs every night at 03:00</span>
          </div>
          <p className="text-xs text-[#66554D]">
            Respects robots.txt, strictly protected against SSRF (no loopback, private ranges, or Docker subnets).
          </p>
        </div>
      </div>
    </PageShell>
  );
};

