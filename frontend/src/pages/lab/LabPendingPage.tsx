import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { Clock, ShieldAlert, LogOut } from 'lucide-react';

export const LabPendingPage: React.FC = () => {
  const { user, setUser } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    try {
      await fetch('/api/auth/logout', {
        method: 'POST',
        credentials: 'include',
      });
    } catch {
      // ignore
    }
    localStorage.removeItem('fitwell_token');
    setUser(null);
    navigate('/login');
  };

  return (
    <div className="min-h-screen w-screen flex flex-col justify-between bg-[#D3B996] px-6 py-10">
      {/* Top Header */}
      <div className="flex items-center justify-between max-w-4xl w-full mx-auto">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 bg-[#791E2D] rounded flex items-center justify-center font-serif text-white font-bold text-xl shadow-sm">
            F
          </div>
          <span className="font-serif text-2xl tracking-tight text-[#241814]">Fitwell</span>
        </div>
        <button
          onClick={handleLogout}
          className="flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-semibold text-[#5B1421] hover:bg-[#C8AE8B] transition-colors"
        >
          <LogOut className="w-4 h-4" />
          Sign out
        </button>
      </div>

      {/* Main Pending Card */}
      <div className="max-w-xl w-full mx-auto my-auto bg-[#E2CEB0] p-8 md:p-10 rounded-2xl shadow-xl border border-[#BFA789] text-center">
        <div className="w-16 h-16 mx-auto mb-6 rounded-full bg-[#791E2D]/10 flex items-center justify-center text-[#791E2D]">
          <Clock className="w-8 h-8 animate-pulse" />
        </div>

        <h1 className="font-serif text-2xl md:text-3xl font-bold text-[#241814] mb-3">
          Your lab request is pending approval
        </h1>

        <p className="text-[#66554D] text-sm leading-relaxed mb-6">
          Thank you for registering with Fitwell. In accordance with university research governance rules, our administration team must verify your laboratory affiliation and domain credentials before granting access to publish offers or view candidates.
        </p>

        <div className="p-4 rounded-xl bg-[#D3B996]/60 border border-[#BFA789] text-left text-xs text-[#241814] space-y-2 mb-8">
          <div className="flex items-center gap-2 font-semibold text-[#5B1421]">
            <ShieldAlert className="w-4 h-4 shrink-0" />
            <span>Verification in progress</span>
          </div>
          <p className="text-[#66554D]">
            • Account: <span className="font-medium text-[#241814]">{user?.email}</span>
          </p>
          <p className="text-[#66554D]">
            • Status: <span className="inline-block px-2 py-0.5 rounded text-[11px] font-bold bg-[#791E2D] text-white">PENDING ADMIN APPROVAL</span>
          </p>
          <p className="text-[#66554D]">
            Once approved by the administrator in the "Labs and claims" dashboard, you will have immediate access to publish research offers and review candidate applications.
          </p>
        </div>

        <button
          onClick={handleLogout}
          className="w-full py-3 px-4 rounded-xl bg-[#791E2D] hover:bg-[#631724] text-white text-sm font-semibold transition-colors shadow-sm"
        >
          Sign out
        </button>
      </div>

      {/* Footer */}
      <div className="text-center text-xs text-[#8D7A68]">
        Fitwell Academic Research Platform — Law 09-08 Protection of Personal Data
      </div>
    </div>
  );
};

