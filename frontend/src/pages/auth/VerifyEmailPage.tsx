import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { CheckCircle2, AlertCircle, Loader2 } from 'lucide-react';

export const VerifyEmailPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [success, setSuccess] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!token) {
      setLoading(false);
      setSuccess(false);
      setMessage('No verification token provided in the URL.');
      return;
    }

    fetch(`/api/auth/verify-email?token=${encodeURIComponent(token)}`)
      .then(async (res) => {
        const data = await res.json();
        if (res.ok) {
          setSuccess(true);
          setMessage(data.message || 'Email successfully verified. You can now sign in.');
        } else {
          setSuccess(false);
          setMessage(data.message || 'Invalid or expired verification token.');
        }
      })
      .catch(() => {
        setSuccess(false);
        setMessage('Network error verifying email. Please try again.');
      })
      .finally(() => {
        setLoading(false);
      });
  }, [token]);

  return (
    <div className="min-h-screen w-screen flex flex-col justify-between bg-[#D3B996] px-6 py-12">
      {/* Top Logo */}
      <div className="flex items-center gap-3 max-w-md w-full mx-auto">
        <div className="w-8 h-8 bg-[#791E2D] rounded flex items-center justify-center font-serif text-white font-bold text-xl shadow-sm">
          F
        </div>
        <span className="font-serif text-2xl tracking-tight text-[#241814]">Fitwell</span>
      </div>

      {/* Verification Card */}
      <div className="max-w-md w-full mx-auto my-auto bg-[#E2CEB0] p-8 md:p-10 rounded-2xl shadow-xl border border-[#BFA789] text-center space-y-5">
        {loading ? (
          <div className="space-y-4 py-8">
            <Loader2 className="w-10 h-10 animate-spin text-[#791E2D] mx-auto" />
            <h1 className="font-serif text-2xl font-bold text-[#241814]">Verifying your email...</h1>
            <p className="text-xs text-[#66554D]">Please wait while we validate your verification token.</p>
          </div>
        ) : success ? (
          <div className="space-y-4">
            <div className="w-14 h-14 mx-auto rounded-full bg-emerald-100 text-emerald-700 flex items-center justify-center">
              <CheckCircle2 className="w-8 h-8" />
            </div>
            <h1 className="font-serif text-2xl md:text-3xl font-bold text-[#241814]">
              Email Verified!
            </h1>
            <p className="text-xs text-[#66554D] leading-relaxed">
              {message}
            </p>
            <div className="pt-4">
              <button
                type="button"
                onClick={() => navigate('/login')}
                className="w-full py-3.5 px-4 rounded-xl bg-[#791E2D] hover:bg-[#631724] text-white text-sm font-semibold transition-colors shadow-sm"
              >
                Go to Login
              </button>
            </div>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="w-14 h-14 mx-auto rounded-full bg-red-100 text-[#791E2D] flex items-center justify-center">
              <AlertCircle className="w-8 h-8" />
            </div>
            <h1 className="font-serif text-2xl md:text-3xl font-bold text-[#241814]">
              Verification Failed
            </h1>
            <p className="text-xs text-[#791E2D] font-medium leading-relaxed">
              {message}
            </p>
            <div className="pt-4 space-y-2">
              <button
                type="button"
                onClick={() => navigate('/login')}
                className="w-full py-3.5 px-4 rounded-xl bg-[#791E2D] hover:bg-[#631724] text-white text-sm font-semibold transition-colors shadow-sm"
              >
                Return to Login
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Footer */}
      <div className="text-center text-xs text-[#8D7A68]">
        Fitwell Academic Research Platform — Morocco
      </div>
    </div>
  );
};
