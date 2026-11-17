import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageShell } from '../../components/common/PageShell';

interface ApplicationItem {
  id: string;
  offerId: string;
  offerTitle: string;
  labName: string;
  location: string;
  durationMonths: number;
  status: string;
  nextStepCalc: string;
  sourceName?: string;
  sourceUrl?: string;
  sentAt?: string;
  createdAt?: string;
}

export const StudentApplicationsPage: React.FC = () => {
  const [applications, setApplications] = useState<ApplicationItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch('/api/student/applications', {
      headers: {
        Authorization: `Bearer ${localStorage.getItem('token') || ''}`,
      },
    })
      .then((res) => (res.ok ? res.json() : Promise.reject()))
      .then((data) => {
        if (data && data.applications) {
          setApplications(data.applications);
        }
        setLoading(false);
      })
      .catch(() => {
        setLoading(false);
      });
  }, []);

  const getStatusBadge = (status: string) => {
    switch (status.toUpperCase()) {
      case 'INTERVIEW':
        return <span className="px-3 py-1 rounded-full bg-[#791E2D] text-white text-xs font-semibold">Entretien</span>;
      case 'SENT':
      case 'SUBMITTED':
        return <span className="px-3 py-1 rounded-full bg-[#2D6A4F] text-white text-xs font-semibold">✓ Candidature envoyée</span>;
      case 'REVIEWED':
      case 'SHORTLISTED':
        return <span className="px-3 py-1 rounded-full bg-[#0A66C2] text-white text-xs font-semibold">En cours d'examen</span>;
      case 'DRAFT':
        return <span className="px-3 py-1 rounded-full bg-[#BFA789] text-[#241814] text-xs font-semibold">Brouillon</span>;
      default:
        return <span className="px-3 py-1 rounded-full bg-[#DFCAAC] text-[#241814] border border-[#BFA789] text-xs font-semibold">{status}</span>;
    }
  };

  return (
    <PageShell
      title="Mes Candidatures en temps réel"
      subtitle="Suivez vos candidatures de stage, statuts et retours des recruteurs instantanément"
      phase="Phase 4 (Applications & Assistant)"
      pdfPage={7}
      badgeLabel="Timeline Tracked"
    >
      <div className="space-y-4">
        {loading ? (
          <div className="p-8 text-center text-[#66554D] text-sm">Chargement de vos candidatures...</div>
        ) : applications.length === 0 ? (
          <div className="p-8 rounded-2xl bg-[#DFCAAC] border border-[#C5AE90] text-center space-y-3">
            <p className="text-sm text-[#241814] font-medium">Vous n'avez pas encore postulé à des offres.</p>
            <Link
              to="/student/explore"
              className="inline-block px-5 py-2.5 rounded-xl bg-[#5B1421] text-white text-xs font-bold hover:bg-[#791E2D] transition shadow-xs"
            >
              Explorer les annonces de stage disponibles →
            </Link>
          </div>
        ) : (
          applications.map((app) => (
            <div
              key={app.id}
              className="p-5 rounded-2xl bg-[#DFCAAC] border border-[#C5AE90] flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 shadow-2xs hover:border-[#5B1421] transition"
            >
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <h3 className="font-serif text-lg font-bold text-[#241814]">{app.offerTitle}</h3>
                  {app.sourceName && (
                    <span className="text-[10px] font-semibold px-2 py-0.5 rounded bg-white/40 text-[#5B1421]">
                      {app.sourceName}
                    </span>
                  )}
                </div>
                <p className="text-xs text-[#66554D]">
                  {app.labName} • {app.location} {app.durationMonths > 0 ? `• ${app.durationMonths} mois` : ''}
                </p>
                {app.nextStepCalc && (
                  <p className="text-[11px] text-[#5B1421] font-medium mt-1">
                    Prochaine étape : {app.nextStepCalc}
                  </p>
                )}
              </div>

              <div className="flex items-center gap-3 self-end sm:self-auto">
                {getStatusBadge(app.status)}
                <Link
                  to={`/student/offers/${app.offerId}`}
                  className="px-3 py-1.5 rounded-lg bg-white/50 hover:bg-white text-xs font-medium text-[#241814] transition"
                >
                  Voir l'offre →
                </Link>
              </div>
            </div>
          ))
        )}
      </div>
    </PageShell>
  );
};

