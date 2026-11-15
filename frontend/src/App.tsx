import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { LoginPage } from './pages/auth/LoginPage';
import { RegisterPage } from './pages/auth/RegisterPage';
import { VerifyEmailPage } from './pages/auth/VerifyEmailPage';
import { LabPendingPage } from './pages/lab/LabPendingPage';
import { AppShell } from './components/layout/AppShell';

// Student Space Pages
import { StudentHomePage } from './pages/student/StudentHomePage';
import { StudentExplorePage } from './pages/student/StudentExplorePage';
import { StudentOfferDetailPage } from './pages/student/StudentOfferDetailPage';
import { StudentApplicationsPage } from './pages/student/StudentApplicationsPage';
import { StudentMessagesPage } from './pages/student/StudentMessagesPage';
import { StudentCvStudioPage } from './pages/student/StudentCvStudioPage';
import { StudentSettingsPage } from './pages/student/StudentSettingsPage';
import { StudentOnboardingPage } from './pages/student/StudentOnboardingPage';
import { StudentOnboardingInterestsPage } from './pages/student/StudentOnboardingInterestsPage';
import { StudentOnboardingAvailabilityPage } from './pages/student/StudentOnboardingAvailabilityPage';

// Lab Space Pages
import { LabDashboardPage } from './pages/lab/LabDashboardPage';
import { LabPostOfferPage } from './pages/lab/LabPostOfferPage';
import { LabCandidatesPage } from './pages/lab/LabCandidatesPage';
import { LabInterviewsPage } from './pages/lab/LabInterviewsPage';

// Admin Space Pages
import { AdminOverviewPage } from './pages/admin/AdminOverviewPage';
import { AdminSourcingPage } from './pages/admin/AdminSourcingPage';
import { AdminValidationPage } from './pages/admin/AdminValidationPage';
import { AdminLabsClaimsPage } from './pages/admin/AdminLabsClaimsPage';
import { AdminRulesPage } from './pages/admin/AdminRulesPage';

const ProtectedRoute: React.FC<{ children: React.ReactNode; allowedRole?: string }> = ({
  children,
  allowedRole,
}) => {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="h-screen w-screen flex items-center justify-center bg-[#D3B996] text-[#241814] font-serif text-xl">
        Loading Fitwell...
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  // Pending lab user cannot access regular lab space
  if (user.role === 'LAB' && !user.isLabApproved) {
    return <Navigate to="/lab/pending" replace />;
  }

  if (allowedRole && user.role !== allowedRole) {
    // Redirect to user's authorized home if attempting to access another role's space
    if (user.role === 'STUDENT') {
      return <Navigate to={user.onboardingCompleted === false ? '/onboarding/cv' : '/student/home'} replace />;
    }
    if (user.role === 'LAB') return <Navigate to="/lab/dashboard" replace />;
    if (user.role === 'ADMIN') return <Navigate to="/admin/overview" replace />;
  }

  return <>{children}</>;
};

const RootRedirect: React.FC = () => {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="h-screen w-screen flex items-center justify-center bg-[#D3B996] text-[#241814] font-serif text-xl">
        Loading Fitwell...
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (user.role === 'STUDENT') {
    return <Navigate to={user.onboardingCompleted === false ? '/onboarding/cv' : '/student/home'} replace />;
  }
  if (user.role === 'LAB') {
    return <Navigate to={user.isLabApproved ? '/lab/dashboard' : '/lab/pending'} replace />;
  }
  if (user.role === 'ADMIN') return <Navigate to="/admin/overview" replace />;

  return <Navigate to="/login" replace />;
};

export const App: React.FC = () => {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          {/* Public Auth Routes */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/verify-email" element={<VerifyEmailPage />} />
          <Route path="/lab/pending" element={<LabPendingPage />} />
          <Route path="/onboarding/cv" element={<StudentOnboardingPage />} />
          <Route path="/onboarding/interests" element={<StudentOnboardingInterestsPage />} />
          <Route path="/onboarding/availability" element={<StudentOnboardingAvailabilityPage />} />
          <Route path="/student/onboarding" element={<Navigate to="/onboarding/cv" replace />} />

          {/* Protected Space Shell Routes */}
          <Route
            element={
              <ProtectedRoute>
                <AppShell />
              </ProtectedRoute>
            }
          >
            {/* Student Routes */}
            <Route path="/student/home" element={<StudentHomePage />} />
            <Route path="/student/explore" element={<StudentExplorePage />} />
            <Route path="/student/offers/:id" element={<StudentOfferDetailPage />} />
            <Route path="/student/applications" element={<StudentApplicationsPage />} />
            <Route path="/student/messages" element={<StudentMessagesPage />} />
            <Route path="/student/cv-studio" element={<StudentCvStudioPage />} />
            <Route path="/student/settings" element={<StudentSettingsPage />} />

            {/* Lab Routes */}
            <Route path="/lab/dashboard" element={<LabDashboardPage />} />
            <Route path="/lab/post-offer" element={<LabPostOfferPage />} />
            <Route path="/lab/candidates" element={<LabCandidatesPage />} />
            <Route path="/lab/interviews" element={<LabInterviewsPage />} />

            {/* Admin Routes */}
            <Route path="/admin/overview" element={<AdminOverviewPage />} />
            <Route path="/admin/sourcing" element={<AdminSourcingPage />} />
            <Route path="/admin/validation" element={<AdminValidationPage />} />
            <Route path="/admin/labs-claims" element={<AdminLabsClaimsPage />} />
            <Route path="/admin/rules" element={<AdminRulesPage />} />
          </Route>

          {/* Root Fallback */}
          <Route path="/" element={<RootRedirect />} />
          <Route path="*" element={<RootRedirect />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
};

export default App;

