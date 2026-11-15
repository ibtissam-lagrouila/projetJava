import React, { useState, useId } from 'react';
import { useNavigate } from 'react-router-dom';
import { Eye, EyeOff, CheckCircle2, AlertCircle } from 'lucide-react';

export const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  const privacyCheckboxId = useId();

  const [name, setName] = useState('');
  const [accountType, setAccountType] = useState<'' | 'STUDENT' | 'HR' | 'CEO'>('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [labName, setLabName] = useState('');
  const [city, setCity] = useState('');
  const [privacyPolicyAccepted, setPrivacyPolicyAccepted] = useState(false);

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [devVerificationLink, setDevVerificationLink] = useState<string | null>(null);

  // Password strength calculation
  const getPasswordStrength = (pwd: string): { score: number; label: string; color: string } => {
    if (!pwd) return { score: 0, label: '', color: 'bg-transparent' };
    if (pwd.length < 12) return { score: 1, label: 'Too short (min 12 chars)', color: 'bg-red-500' };

    let score = 1;
    if (/[A-Z]/.test(pwd)) score++;
    if (/[0-9]/.test(pwd)) score++;
    if (/[^A-Za-z0-9]/.test(pwd)) score++;

    if (score <= 2) return { score: 2, label: 'Weak', color: 'bg-orange-500' };
    if (score === 3) return { score: 3, label: 'Good', color: 'bg-amber-500' };
    return { score: 4, label: 'Strong', color: 'bg-emerald-600' };
  };

  const strength = getPasswordStrength(password);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setGeneralError(null);
    setFieldErrors({});

    const errors: Record<string, string> = {};

    if (!name.trim()) {
      errors.name = 'Full name is required';
    }
    if (!accountType) {
      errors.accountType = 'Please select your profile';
    }
    if (!email.trim()) {
      errors.email = 'Email address is required';
    }
    if (password.length < 12) {
      errors.password = 'Password must be at least 12 characters';
    }
    if ((accountType === 'HR' || accountType === 'CEO')) {
      if (!labName.trim()) {
        errors.labName = 'Laboratory name is required';
      }
      if (!city.trim()) {
        errors.city = 'City is required';
      }
    }
    if (!privacyPolicyAccepted) {
      errors.privacyPolicyAccepted = 'You must accept the privacy policy to register';
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setLoading(true);

    try {
      const payload: Record<string, any> = {
        name: name.trim(),
        accountType,
        email: email.trim(),
        password,
        privacyPolicyAccepted,
      };

      if (accountType === 'HR' || accountType === 'CEO') {
        payload.labName = labName.trim();
        payload.city = city.trim();
      }

      const res = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      const data = await res.json();

      if (!res.ok) {
        if (data.errors && typeof data.errors === 'object') {
          setFieldErrors(data.errors);
        } else {
          setGeneralError(data.message || 'Registration failed');
        }
        return;
      }

      setSuccessMessage(data.message || 'If your email is eligible, a verification link has been sent to your address.');
      if (data.devVerificationLink) {
        setDevVerificationLink(data.devVerificationLink);
      }
    } catch (err: any) {
      setGeneralError(err.message || 'An unexpected error occurred. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen w-screen overflow-x-hidden bg-[#D3B996]">
      {/* Left Column: Registration Form */}
      <div className="w-full lg:w-[48%] min-h-screen flex flex-col justify-between px-8 sm:px-14 py-10 overflow-y-auto">
        {/* Top Logo */}
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 bg-[#791E2D] rounded flex items-center justify-center font-serif text-white font-bold text-xl shadow-sm">
            F
          </div>
          <span className="font-serif text-2xl tracking-tight text-[#241814]">Fitwell</span>
        </div>

        {/* Content Box */}
        <div className="max-w-md w-full my-6">
          <h1 className="font-serif text-3xl sm:text-[38px] leading-[1.2] text-[#241814] mb-2 font-bold">
            Create an account
          </h1>
          <p className="text-[#66554D] text-sm leading-relaxed mb-6">
            Join Fitwell to discover research laboratories and academic opportunities across Morocco.
          </p>

          {generalError && (
            <div className="mb-5 p-3 rounded-xl bg-[#791E2D]/10 border border-[#791E2D] text-[#791E2D] text-xs font-medium flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{generalError}</span>
            </div>
          )}

          {successMessage ? (
            <div className="p-6 rounded-2xl bg-[#E2CEB0] border border-[#BFA789] shadow-md text-center space-y-4">
              <div className="w-12 h-12 mx-auto rounded-full bg-emerald-100 text-emerald-700 flex items-center justify-center">
                <CheckCircle2 className="w-7 h-7" />
              </div>
              <h2 className="font-serif text-xl font-bold text-[#241814]">Check your inbox</h2>
              <p className="text-[#66554D] text-xs leading-relaxed">
                {successMessage}
              </p>

              {devVerificationLink && (
                <div className="mt-4 p-4 rounded-xl bg-[#DFCAAC] border-2 border-dashed border-[#791E2D] text-left space-y-2 shadow-sm">
                  <div className="text-[11px] font-bold text-[#791E2D] uppercase tracking-wider flex items-center gap-1.5">
                    <span>🛠️ DEV ONLY : verification link</span>
                  </div>
                  <p className="text-[11px] text-[#66554D] leading-snug">
                    Click the link below to verify this account directly in development mode:
                  </p>
                  <a
                    href={devVerificationLink}
                    className="block text-xs font-mono font-medium text-[#791E2D] underline hover:text-[#5B1421] break-all bg-[#ECD9C2] p-2.5 rounded-lg border border-[#BFA789]"
                  >
                    {devVerificationLink}
                  </a>
                </div>
              )}

              <div className="pt-2">
                <button
                  type="button"
                  onClick={() => navigate('/login')}
                  className="w-full py-3 px-4 rounded-xl bg-[#791E2D] hover:bg-[#631724] text-white text-sm font-semibold transition-colors shadow-sm"
                >
                  Already have an account? Login
                </button>
              </div>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-4">
              {/* Full Name */}
              <div>
                <label htmlFor="register-name" className="block text-xs font-semibold text-[#241814] mb-1">
                  Name
                </label>
                <input
                  id="register-name"
                  type="text"
                  required
                  value={name}
                  onChange={(e) => {
                    setName(e.target.value);
                    if (fieldErrors.name) setFieldErrors({ ...fieldErrors, name: '' });
                  }}
                  placeholder="Full name (e.g. Karim Idrissi)"
                  className="w-full px-4 py-2.5 rounded-xl bg-[#DFCAAC] border border-[#BFA789] text-[#241814] text-sm placeholder-[#8D7A68] focus:outline-none focus:ring-2 focus:ring-[#791E2D]"
                />
                {fieldErrors.name && (
                  <p className="mt-1 text-[11px] text-[#791E2D] font-medium">{fieldErrors.name}</p>
                )}
              </div>

              {/* I am... Dropdown */}
              <div>
                <label htmlFor="register-accountType" className="block text-xs font-semibold text-[#241814] mb-1">
                  I am…
                </label>
                <select
                  id="register-accountType"
                  required
                  value={accountType}
                  onChange={(e) => {
                    setAccountType(e.target.value as any);
                    if (fieldErrors.accountType) setFieldErrors({ ...fieldErrors, accountType: '' });
                  }}
                  className="w-full px-4 py-2.5 rounded-xl bg-[#DFCAAC] border border-[#BFA789] text-[#241814] text-sm focus:outline-none focus:ring-2 focus:ring-[#791E2D]"
                >
                  <option value="" disabled>Select your profile</option>
                  <option value="STUDENT">Student</option>
                  <option value="HR">HR</option>
                  <option value="CEO">CEO / Lab director</option>
                </select>
                {fieldErrors.accountType && (
                  <p className="mt-1 text-[11px] text-[#791E2D] font-medium">{fieldErrors.accountType}</p>
                )}
              </div>

              {/* Conditional Lab Fields for HR / CEO */}
              {(accountType === 'HR' || accountType === 'CEO') && (
                <div className="space-y-4 p-3.5 rounded-xl bg-[#DFCAAC]/60 border border-[#BFA789]">
                  <div>
                    <label htmlFor="register-labName" className="block text-xs font-semibold text-[#241814] mb-1">
                      Lab name
                    </label>
                    <input
                      id="register-labName"
                      type="text"
                      required
                      value={labName}
                      onChange={(e) => {
                        setLabName(e.target.value);
                        if (fieldErrors.labName) setFieldErrors({ ...fieldErrors, labName: '' });
                      }}
                      placeholder="Laboratory or research unit name"
                      className="w-full px-4 py-2.5 rounded-xl bg-[#DFCAAC] border border-[#BFA789] text-[#241814] text-sm placeholder-[#8D7A68] focus:outline-none focus:ring-2 focus:ring-[#791E2D]"
                    />
                    {fieldErrors.labName && (
                      <p className="mt-1 text-[11px] text-[#791E2D] font-medium">{fieldErrors.labName}</p>
                    )}
                  </div>

                  <div>
                    <label htmlFor="register-city" className="block text-xs font-semibold text-[#241814] mb-1">
                      City
                    </label>
                    <input
                      id="register-city"
                      type="text"
                      required
                      value={city}
                      onChange={(e) => {
                        setCity(e.target.value);
                        if (fieldErrors.city) setFieldErrors({ ...fieldErrors, city: '' });
                      }}
                      placeholder="City (e.g. Rabat, Casablanca, Fès)"
                      className="w-full px-4 py-2.5 rounded-xl bg-[#DFCAAC] border border-[#BFA789] text-[#241814] text-sm placeholder-[#8D7A68] focus:outline-none focus:ring-2 focus:ring-[#791E2D]"
                    />
                    {fieldErrors.city && (
                      <p className="mt-1 text-[11px] text-[#791E2D] font-medium">{fieldErrors.city}</p>
                    )}
                  </div>
                </div>
              )}

              {/* Email */}
              <div>
                <label htmlFor="register-email" className="block text-xs font-semibold text-[#241814] mb-1">
                  Email
                </label>
                <input
                  id="register-email"
                  type="email"
                  required
                  value={email}
                  onChange={(e) => {
                    setEmail(e.target.value);
                    if (fieldErrors.email) setFieldErrors({ ...fieldErrors, email: '' });
                  }}
                  placeholder="name@example.com"
                  className="w-full px-4 py-2.5 rounded-xl bg-[#DFCAAC] border border-[#BFA789] text-[#241814] text-sm placeholder-[#8D7A68] focus:outline-none focus:ring-2 focus:ring-[#791E2D]"
                />
                {fieldErrors.email && (
                  <p className="mt-1 text-[11px] text-[#791E2D] font-medium">{fieldErrors.email}</p>
                )}
              </div>

              {/* Password */}
              <div>
                <label htmlFor="register-password" className="block text-xs font-semibold text-[#241814] mb-1">
                  Password
                </label>
                <div className="relative">
                  <input
                    id="register-password"
                    type={showPassword ? 'text' : 'password'}
                    required
                    value={password}
                    onChange={(e) => {
                      setPassword(e.target.value);
                      if (fieldErrors.password) setFieldErrors({ ...fieldErrors, password: '' });
                    }}
                    placeholder="Min. 12 characters"
                    className="w-full px-4 py-2.5 pr-11 rounded-xl bg-[#DFCAAC] border border-[#BFA789] text-[#241814] text-sm placeholder-[#8D7A68] focus:outline-none focus:ring-2 focus:ring-[#791E2D]"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-[#66554D] hover:text-[#241814]"
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>

                {/* Password strength meter */}
                {password.length > 0 && (
                  <div className="mt-2 space-y-1">
                    <div className="flex gap-1 h-1.5 w-full bg-[#BFA789]/40 rounded-full overflow-hidden">
                      <div className={`h-full transition-all duration-300 ${strength.score >= 1 ? strength.color : ''} ${strength.score === 1 ? 'w-1/4' : strength.score === 2 ? 'w-2/4' : strength.score === 3 ? 'w-3/4' : 'w-full'}`} />
                    </div>
                    <div className="flex justify-between text-[11px]">
                      <span className="text-[#66554D]">Strength:</span>
                      <span className={`font-semibold ${strength.score >= 3 ? 'text-emerald-700' : 'text-[#791E2D]'}`}>
                        {strength.label}
                      </span>
                    </div>
                  </div>
                )}
                {fieldErrors.password && (
                  <p className="mt-1 text-[11px] text-[#791E2D] font-medium">{fieldErrors.password}</p>
                )}
              </div>

              {/* Privacy Policy Checkbox (Moroccan Law 09-08) */}
              <div className="pt-2">
                <label htmlFor={privacyCheckboxId} className="flex items-start gap-2.5 cursor-pointer">
                  <input
                    id={privacyCheckboxId}
                    type="checkbox"
                    checked={privacyPolicyAccepted}
                    onChange={(e) => {
                      setPrivacyPolicyAccepted(e.target.checked);
                      if (fieldErrors.privacyPolicyAccepted) {
                        setFieldErrors({ ...fieldErrors, privacyPolicyAccepted: '' });
                      }
                    }}
                    className="mt-0.5 h-4 w-4 rounded border-[#BFA789] text-[#791E2D] focus:ring-[#791E2D] accent-[#791E2D]"
                  />
                  <span className="text-xs text-[#241814] leading-snug">
                    I accept the privacy policy in accordance with Moroccan Law 09-08 on personal data protection.
                  </span>
                </label>
                {fieldErrors.privacyPolicyAccepted && (
                  <p className="mt-1 text-[11px] text-[#791E2D] font-medium">{fieldErrors.privacyPolicyAccepted}</p>
                )}
              </div>

              {/* Register Button */}
              <button
                type="submit"
                disabled={loading}
                className="w-full py-3.5 px-4 rounded-xl bg-[#791E2D] hover:bg-[#631724] active:bg-[#4E101B] text-white text-sm font-semibold tracking-wide transition-colors shadow-sm disabled:opacity-70 mt-3"
              >
                {loading ? 'Creating account...' : 'Register'}
              </button>

              {/* Already have an account? Login */}
              <div className="text-center text-xs text-[#241814] pt-2">
                <span>Already have an account? </span>
                <button
                  type="button"
                  onClick={() => navigate('/login')}
                  className="text-[#791E2D] font-semibold underline hover:text-[#5B1421]"
                >
                  Login
                </button>
              </div>
            </form>
          )}
        </div>

        {/* Footer */}
        <div className="text-[11px] text-[#8D7A68]">
          Fitwell Platform — Academic Research Internships in Morocco
        </div>
      </div>

      {/* Right Column: Floating Cards Showcase */}
      <div className="hidden lg:flex w-[52%] min-h-screen bg-[#5B1421] p-12 flex-col justify-center items-center relative overflow-hidden select-none">
        <div className="absolute w-[500px] h-[500px] rounded-full bg-[#721B2B]/20 blur-3xl pointer-events-none" />

        <div className="relative w-full max-w-lg space-y-6">
          <div className="text-center mb-8">
            <span className="px-3 py-1 rounded-full bg-[#791E2D] text-[#E2CEB0] text-xs font-semibold uppercase tracking-wider">
              Academic Network
            </span>
            <h2 className="font-serif text-3xl font-bold text-[#E2CEB0] mt-3">
              Morocco's Research Ecosystem
            </h2>
            <p className="text-[#D3B996] text-xs mt-2 max-w-sm mx-auto">
              Direct connection between academic talent and high-impact laboratories.
            </p>
          </div>

          {/* Feature Card 1 */}
          <div className="bg-[#E2CEB0] p-5 rounded-2xl shadow-lg border border-[#DFCAAC] transform -rotate-1">
            <div className="text-xs text-[#66554D] font-medium">Verified Laboratories</div>
            <div className="text-base font-serif font-bold text-[#241814] mt-0.5">
              Peer-reviewed academic research positions
            </div>
            <div className="text-xs text-[#791E2D] mt-2 font-semibold">
              UM6P • MAScIR • CNRST • Public Universities
            </div>
          </div>

          {/* Feature Card 2 */}
          <div className="bg-[#E2CEB0] p-5 rounded-2xl shadow-lg border border-[#DFCAAC] transform translate-x-4">
            <div className="text-xs text-[#66554D] font-medium">Compliance & Protection</div>
            <div className="text-base font-serif font-bold text-[#241814] mt-0.5">
              Law 09-08 strictly applied
            </div>
            <div className="text-xs text-[#66554D] mt-1">
              Your academic data remains private and protected at every step.
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
