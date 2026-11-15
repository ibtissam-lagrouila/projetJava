import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { AuthResponse } from '../../types';
import { Eye, EyeOff } from 'lucide-react';

export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const { setUser } = useAuth();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [isDev, setIsDev] = useState(false);
  const [isUnverified, setIsUnverified] = useState(false);
  const [resendStatus, setResendStatus] = useState<string | null>(null);
  const [resending, setResending] = useState(false);
  const [devResendLink, setDevResendLink] = useState<string | null>(null);

  useEffect(() => {
    fetch('/api/auth/config')
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (data && typeof data.isDev === 'boolean') {
          setIsDev(data.isDev);
        }
      })
      .catch(() => {
        // Fallback: stay non-dev or dev false
      });
  }, []);

  const handleLogin = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ email: email.trim(), password }),
      });

      const data = await res.json();

      if (!res.ok) {
        throw new Error(data.message || 'Invalid email or password');
      }

      const authData = data as AuthResponse;
      if (authData.token) {
        localStorage.setItem('fitwell_token', authData.token);
      }
      setUser(authData.user);

      // Routing logic based on role & state
      if (authData.user.role === 'STUDENT') {
        if (authData.user.onboardingCompleted === false) {
          navigate('/student/onboarding');
        } else {
          navigate('/student/home');
        }
      } else if (authData.user.role === 'LAB') {
        if (authData.user.isLabApproved) {
          navigate('/lab/dashboard');
        } else {
          navigate('/lab/pending');
        }
      } else if (authData.user.role === 'ADMIN') {
        navigate('/admin/overview');
      }
    } catch (err: any) {
      const msg = err.message || 'Invalid email or password';
      setError(msg);
      if (msg.toLowerCase().includes('verify your email')) {
        setIsUnverified(true);
      } else {
        setIsUnverified(false);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleResendVerification = async () => {
    if (!email.trim()) {
      setError('Please enter your email address to resend verification.');
      return;
    }
    setResending(true);
    setResendStatus(null);
    setDevResendLink(null);

    try {
      const res = await fetch('/api/auth/resend-verification', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: email.trim() }),
      });
      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || 'Failed to resend verification email');
      }
      setResendStatus(data.message || 'If your account is eligible, a new verification link has been sent to your email.');
      if (data.devVerificationLink) {
        setDevResendLink(data.devVerificationLink);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to resend verification email');
    } finally {
      setResending(false);
    }
  };

  const setDemoCredentials = (role: 'student' | 'lab' | 'admin') => {
    if (role === 'student') {
      setEmail('yassine.bennis@univ-casa.ma');
      setPassword('StudentSecure2026!');
    } else if (role === 'lab') {
      setEmail('s.elidrissi@univ-fes.ma');
      setPassword('LabSecure2026!');
    } else if (role === 'admin') {
      setEmail('admin@fitwell.ma');
      setPassword('AdminSecure2026!');
    }
  };

  return (
    <div className="flex h-screen w-screen overflow-hidden bg-[#D3B996]">
      {/* Left Column: Sign In Form */}
      <div className="w-[45%] h-full flex flex-col justify-between px-16 py-12 overflow-y-auto">
        {/* Top Logo */}
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 bg-[#791E2D] rounded flex items-center justify-center font-serif text-white font-bold text-xl shadow-sm">
            F
          </div>
          <span className="font-serif text-2xl tracking-tight text-[#241814]">Fitwell</span>
        </div>

        {/* Form Container */}
        <div className="max-w-md w-full my-auto">
          <h1 className="font-serif text-[42px] leading-[1.15] text-[#241814] mb-3">
            Find the lab that will shape your future
          </h1>
          <p className="text-[#66554D] text-[15px] leading-relaxed mb-8">
            Research internships matched to your skills, with an assistant that helps you apply.
          </p>

          {error && (
            <div className="mb-6 p-4 rounded-xl bg-[#5B1421]/10 border border-[#791E2D] text-[#791E2D] text-xs font-medium space-y-2.5">
              <div>{error}</div>
              {isUnverified && (
                <div>
                  <button
                    type="button"
                    onClick={handleResendVerification}
                    disabled={resending}
                    className="px-3 py-1.5 rounded-lg bg-[#791E2D] hover:bg-[#631724] text-white text-xs font-semibold transition-colors shadow-sm disabled:opacity-60"
                  >
                    {resending ? 'Sending...' : 'Resend verification email'}
                  </button>
                </div>
              )}
            </div>
          )}

          {resendStatus && (
            <div className="mb-6 p-4 rounded-xl bg-emerald-50 border border-emerald-400 text-emerald-800 text-xs font-medium space-y-2.5">
              <div>{resendStatus}</div>
              {isDev && devResendLink && (
                <div className="p-3 bg-[#DFCAAC] border border-[#BFA789] rounded-lg text-left space-y-1">
                  <div className="text-[11px] font-bold text-[#791E2D] uppercase tracking-wider">
                    🛠️ DEV ONLY : verification link
                  </div>
                  <a
                    href={devResendLink}
                    className="block text-xs font-mono font-medium text-[#791E2D] underline hover:text-[#5B1421] break-all"
                  >
                    {devResendLink}
                  </a>
                </div>
              )}
            </div>
          )}

          <form onSubmit={handleLogin} className="space-y-5">
            <div>
              <label className="block text-xs font-semibold text-[#241814] mb-1.5">
                Email
              </label>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="name@example.com"
                className="w-full px-4 py-3 rounded-xl bg-[#DFCAAC] border border-[#BFA789] text-[#241814] text-sm placeholder-[#8D7A68] focus:outline-none focus:ring-2 focus:ring-[#791E2D]"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-[#241814] mb-1.5">
                Password
              </label>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Your password"
                  className="w-full px-4 py-3 pr-11 rounded-xl bg-[#DFCAAC] border border-[#BFA789] text-[#241814] text-sm placeholder-[#8D7A68] focus:outline-none focus:ring-2 focus:ring-[#791E2D]"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  className="absolute right-3.5 top-1/2 -translate-y-1/2 text-[#66554D] hover:text-[#241814]"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3.5 px-4 rounded-xl bg-[#791E2D] hover:bg-[#631724] active:bg-[#4E101B] text-white text-sm font-semibold tracking-wide transition-colors shadow-sm disabled:opacity-70 mt-2"
            >
              {loading ? 'Signing in...' : 'Login'}
            </button>
          </form>

          {/* Register Link */}
          <div className="flex items-center justify-between text-xs mt-6 text-[#241814]">
            <span>
              Don't have an account?{' '}
              <button
                type="button"
                onClick={() => navigate('/register')}
                className="text-[#791E2D] font-semibold underline hover:text-[#5B1421]"
              >
                Register
              </button>
            </span>
            <button
              type="button"
              onClick={() => alert('In development mode, password reset links are printed directly to the backend logs.')}
              className="text-[#66554D] hover:underline"
            >
              Forgot password
            </button>
          </div>

          {/* Quick demo credentials helper (ONLY shown in dev mode) */}
          {isDev && (
            <div className="mt-8 pt-6 border-t border-[#BFA789]/60">
              <span className="block text-[11px] font-semibold uppercase tracking-wider text-[#7A6757] mb-2.5">
                QUICK DEMO ACCOUNTS (SEED):
              </span>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={() => setDemoCredentials('student')}
                  className="px-2.5 py-1 text-xs rounded-lg bg-[#E2CEB0] hover:bg-[#D6C1A2] text-[#241814] font-medium border border-[#C5AE90]"
                >
                  🎓 Étudiant (Yassine)
                </button>
                <button
                  type="button"
                  onClick={() => setDemoCredentials('lab')}
                  className="px-2.5 py-1 text-xs rounded-lg bg-[#E2CEB0] hover:bg-[#D6C1A2] text-[#241814] font-medium border border-[#C5AE90]"
                >
                  🔬 Labo (Dr. El Idrissi)
                </button>
                <button
                  type="button"
                  onClick={() => setDemoCredentials('admin')}
                  className="px-2.5 py-1 text-xs rounded-lg bg-[#E2CEB0] hover:bg-[#D6C1A2] text-[#241814] font-medium border border-[#C5AE90]"
                >
                  🛡️ Admin (Fitwell)
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Footer info */}
        <div className="text-[11px] text-[#8D7A68]">
          Fitwell Platform — Academic Research Internships in Morocco
        </div>
      </div>

      {/* Right Column: Floating Cards Showcase */}
      <div className="w-[55%] h-full bg-[#5B1421] p-12 flex flex-col justify-center items-center relative overflow-hidden select-none">
        {/* Decorative background glow */}
        <div className="absolute w-[500px] h-[500px] rounded-full bg-[#721B2B]/20 blur-3xl pointer-events-none" />

        <div className="relative w-full max-w-lg space-y-6">
          {/* Card 1: Genomics Unit */}
          <div className="bg-[#E2CEB0] p-5 rounded-2xl shadow-lg transform -rotate-1 hover:rotate-0 transition-transform">
            <div className="text-xs text-[#66554D] font-medium">Genomics Unit, Fès</div>
            <div className="text-lg font-serif font-bold text-[#241814] mt-0.5">
              Gene expression analysis
            </div>
            <div className="flex items-center justify-between mt-2">
              <span className="text-xs text-[#66554D]">4 months, funded</span>
              <span className="text-xs font-bold text-[#791E2D]">74% match</span>
            </div>
          </div>

          {/* Card 2: NLP Group */}
          <div className="bg-[#E2CEB0] p-5 rounded-2xl shadow-lg transform translate-x-6 hover:translate-x-4 transition-transform">
            <div className="text-xs text-[#66554D] font-medium">NLP Group, Casablanca</div>
            <div className="text-lg font-serif font-bold text-[#241814] mt-0.5">
              NLP for Arabic dialects
            </div>
            <div className="flex items-center justify-between mt-2">
              <span className="text-xs text-[#66554D]">3 months</span>
              <span className="text-xs font-bold text-[#791E2D]">88% match</span>
            </div>
          </div>

          {/* Card 3: Hero Card (Vision models for medical imaging) */}
          <div className="bg-[#E2CEB0] p-6 rounded-2xl shadow-xl transform -translate-x-3 hover:translate-x-0 transition-transform border border-[#DFCAAC]">
            <div className="flex items-center justify-between mb-1">
              <span className="text-xs text-[#66554D] font-medium">Vision and Imaging Lab, Rabat</span>
              <span className="px-2.5 py-0.5 rounded-full bg-[#791E2D] text-white text-xs font-bold shadow-sm">
                96% match
              </span>
            </div>
            <h2 className="font-serif text-2xl font-bold text-[#241814] mt-1 mb-3">
              Vision models for medical imaging
            </h2>
            <div className="p-3.5 rounded-xl bg-[#5B1421] text-[#F5EFEB] text-xs leading-relaxed">
              <span className="font-semibold text-white">Fitwell:</span> your PyTorch segmentation project covers the main task.
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
