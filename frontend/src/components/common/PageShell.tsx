import React from 'react';
import { useAuth } from '../../context/AuthContext';

interface PageShellProps {
  title: string;
  subtitle?: string;
  phase: string;
  pdfPage: number;
  badgeLabel?: string;
  children?: React.ReactNode;
}

export const PageShell: React.FC<PageShellProps> = ({
  title,
  subtitle,
  phase,
  pdfPage,
  badgeLabel,
  children,
}) => {
  const { user } = useAuth();

  return (
    <div className="space-y-6">
      {/* Top Banner / Header */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4 pb-4 border-b border-[#BFA789]/50">
        <div>
          <div className="text-xs font-semibold uppercase tracking-wider text-[#791E2D] mb-1">
            Fitwell Mockup Page {pdfPage} — {phase}
          </div>
          <h1 className="font-serif text-3xl md:text-4xl font-bold text-[#241814]">
            {title}
          </h1>
          {subtitle && (
            <p className="text-sm text-[#66554D] mt-1">
              {subtitle}
            </p>
          )}
        </div>

        {badgeLabel && (
          <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-[#E2CEB0] border border-[#BFA789] text-xs font-semibold text-[#5B1421]">
            <span className="w-2 h-2 rounded-full bg-[#2D6A4F] animate-pulse"></span>
            {badgeLabel}
          </div>
        )}
      </div>

      {/* Main Content Card */}
      <div className="bg-[#E2CEB0] border border-[#BFA789]/70 rounded-2xl p-6 shadow-sm">
        {children ? (
          children
        ) : (
          <div className="space-y-4">
            <div className="p-4 rounded-xl bg-[#D6C1A2] border border-[#BFA789] flex items-center justify-between">
              <div>
                <h3 className="font-serif text-lg font-bold text-[#241814]">
                  Interactive Shell Ready
                </h3>
                <p className="text-xs text-[#66554D] mt-0.5">
                  Connected to Java 21 SE pure backend as <span className="font-semibold text-[#241814]">{user?.email}</span> ({user?.role}).
                </p>
              </div>
              <span className="text-xs font-mono font-medium px-2.5 py-1 rounded bg-[#5B1421] text-[#F5EFEB]">
                Phase 1B Verified
              </span>
            </div>

            <p className="text-sm text-[#4A3B32] leading-relaxed">
              This space shell reproduces the layout, typography, navigation hierarchy, and color tokens from page {pdfPage} of the Fitwell design document. Full interactive workflows for this screen are scheduled in {phase}.
            </p>
          </div>
        )}
      </div>
    </div>
  );
};

