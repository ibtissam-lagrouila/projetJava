import React, { useEffect, useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { 
  Home, 
  Compass, 
  Send, 
  MessageSquare, 
  FileText, 
  Sliders, 
  LayoutDashboard, 
  PlusCircle, 
  Users, 
  Calendar, 
  Activity, 
  Globe, 
  CheckCircle, 
  ShieldCheck, 
  Cpu,
  LogOut
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { api } from '../../services/api';
import { Badges } from '../../types';

interface NavItemConfig {
  label: string;
  path: string;
  icon: React.FC<{ className?: string }>;
  badgeKey?: string;
}

export const Sidebar: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [badges, setBadges] = useState<Badges>({});

  useEffect(() => {
    if (user) {
      api.getBadges().then(setBadges);
    }
  }, [user]);

  if (!user) return null;

  const role = user.role;

  const getStudentItems = (): NavItemConfig[] => [
    { label: 'Home', path: '/student/home', icon: Home },
    { label: 'Explore', path: '/student/explore', icon: Compass },
    { label: 'Applications', path: '/student/applications', icon: Send, badgeKey: 'applications' },
    { label: 'Messages', path: '/student/messages', icon: MessageSquare, badgeKey: 'messages' },
    { label: 'CV studio', path: '/onboarding/cv', icon: FileText },
    { label: 'Settings', path: '/student/settings', icon: Sliders },
  ];

  const getLabItems = (): NavItemConfig[] => [
    { label: 'Dashboard', path: '/lab/dashboard', icon: LayoutDashboard },
    { label: 'Post an offer', path: '/lab/post-offer', icon: PlusCircle },
    { label: 'Candidates', path: '/lab/candidates', icon: Users, badgeKey: 'candidates' },
    { label: 'Interviews', path: '/lab/interviews', icon: Calendar, badgeKey: 'interviews' },
  ];

  const getAdminItems = (): NavItemConfig[] => [
    { label: 'Overview', path: '/admin/overview', icon: Activity },
    { label: 'Sourcing', path: '/admin/sourcing', icon: Globe },
    { label: 'Validation', path: '/admin/validation', icon: CheckCircle, badgeKey: 'validation' },
    { label: 'Labs and claims', path: '/admin/labs-claims', icon: ShieldCheck, badgeKey: 'claims' },
    { label: 'AI and rules', path: '/admin/rules', icon: Cpu },
  ];

  let items: NavItemConfig[] = [];
  let headerLabel: string | null = null;

  if (role === 'STUDENT') {
    items = getStudentItems();
  } else if (role === 'LAB') {
    items = getLabItems();
    headerLabel = 'FOR LABS';
  } else if (role === 'ADMIN') {
    items = getAdminItems();
    headerLabel = 'ADMIN';
  }

  const getInitials = (name?: string) => {
    if (!name) return 'U';
    const parts = name.trim().split(/\s+/);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return name.substring(0, 2).toUpperCase();
  };

  const getSubtitle = () => {
    if (role === 'STUDENT') return user.level || 'Master 1, data science';
    if (role === 'LAB') return 'Genomics Unit, Fès';
    return 'Platform admin';
  };

  const handleSignOut = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <aside className="w-[240px] bg-[#5B1421] text-[#F5EFEB] flex flex-col justify-between shrink-0 h-screen select-none border-r border-[#48101A]/50">
      {/* Top Header & Navigation */}
      <div className="flex flex-col">
        {/* Brand Logo */}
        <div className="flex items-center gap-3 px-6 pt-6 pb-4">
          <div className="w-7 h-7 bg-[#791E2D] rounded flex items-center justify-center font-serif text-white font-bold text-lg shadow-sm">
            F
          </div>
          <span className="font-serif text-2xl tracking-tight text-[#F5EFEB]">Fitwell</span>
        </div>

        {/* Section label if LAB or ADMIN */}
        {headerLabel && (
          <div className="px-6 pt-3 pb-2 text-[11px] font-bold tracking-wider text-[#BCA29F] uppercase">
            {headerLabel}
          </div>
        )}

        {/* Navigation list */}
        <nav className="mt-2 space-y-1 px-3">
          {items.map((item) => {
            const Icon = item.icon;
            const count = item.badgeKey ? badges[item.badgeKey] : undefined;

            return (
              <NavLink
                key={item.path}
                to={item.path}
                className={({ isActive }) =>
                  `flex items-center justify-between px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                    isActive
                      ? 'bg-[#721B2B] text-white shadow-sm'
                      : 'text-[#EFE7D8]/80 hover:bg-[#681927] hover:text-white'
                  }`
                }
              >
                <div className="flex items-center gap-3">
                  <Icon className="w-4 h-4 opacity-80" />
                  <span>{item.label}</span>
                </div>

                {count !== undefined && count > 0 ? (
                  <span className="min-w-5 h-5 px-1.5 flex items-center justify-center rounded-full bg-[#E2CEB0] text-[#5B1421] text-xs font-bold leading-none">
                    {count}
                  </span>
                ) : (
                  <span className="text-[#8B5A63] text-xs font-light">—</span>
                )}
              </NavLink>
            );
          })}
        </nav>
      </div>

      {/* Bottom Profile Card */}
      <div className="p-3">
        <div className="flex items-center justify-between p-2.5 rounded-xl bg-[#4A101B] border border-[#681927]">
          <div className="flex items-center gap-2.5 overflow-hidden">
            <div className="w-9 h-9 rounded-full bg-[#791E2D] flex items-center justify-center font-semibold text-xs text-[#F5EFEB] shrink-0 border border-[#8B2E3D]">
              {getInitials(user.fullName)}
            </div>
            <div className="flex flex-col truncate">
              <span className="text-xs font-semibold text-[#F5EFEB] truncate leading-tight">
                {user.fullName || user.email}
              </span>
              <span className="text-[11px] text-[#BCA29F] truncate leading-tight mt-0.5">
                {getSubtitle()}
              </span>
            </div>
          </div>

          <button
            onClick={handleSignOut}
            title="Sign out"
            className="p-1.5 text-[#BCA29F] hover:text-white hover:bg-[#681927] rounded-lg transition-colors ml-1 shrink-0"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </div>
    </aside>
  );
};

