import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import PublicLayout from './components/public/PublicLayout';
import ProtectedRoute from './components/ProtectedRoute';
import Layout from './components/layout/Layout';
import AppToaster from './components/ui/Toaster';

import LandingPage from './pages/LandingPage';
import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';
import UnauthorizedPage from './pages/UnauthorizedPage';
import NotFoundPage from './pages/NotFoundPage';
import DashboardPage from './pages/DashboardPage';

import StudentsPage from './pages/po/StudentsPage';
import DepartmentsPage from './pages/po/DepartmentsPage';
import PcManagementPage from './pages/po/PcManagementPage';
import PrManagementPage from './pages/po/PrManagementPage';
import ReportsPage from './pages/po/ReportsPage';
import AuditLogsPage from './pages/po/AuditLogsPage';

import CompaniesPage from './pages/pc/CompaniesPage';
import DrivesPage from './pages/pc/DrivesPage';

import MessagesPage from './pages/pr/MessagesPage';
import ContactRequestsPage from './pages/pr/ContactRequestsPage';

import ProfilePage from './pages/student/ProfilePage';
import StudentDrivesPage from './pages/student/DrivesPage';
import InterviewsPage from './pages/student/InterviewsPage';

function AuthRedirect({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, loading } = useAuth();
  if (loading) return null;
  if (isAuthenticated) return <Navigate to="/dashboard" replace />;
  return <>{children}</>;
}

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Public layout — landing, login, register */}
          <Route element={<PublicLayout />}>
            <Route
              path="/"
              element={
                <AuthRedirect>
                  <LandingPage />
                </AuthRedirect>
              }
            />
            <Route
              path="/login"
              element={
                <AuthRedirect>
                  <LoginPage />
                </AuthRedirect>
              }
            />
            <Route
              path="/register"
              element={
                <AuthRedirect>
                  <RegisterPage />
                </AuthRedirect>
              }
            />
          </Route>

          <Route path="/unauthorized" element={<UnauthorizedPage />} />

          {/* Authenticated app shell */}
          <Route element={<ProtectedRoute />}>
            <Route element={<Layout />}>
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/" element={<Navigate to="/dashboard" replace />} />

              <Route path="/students" element={<StudentsPage />} />
              <Route path="/departments" element={<DepartmentsPage />} />
              <Route path="/pc-management" element={<PcManagementPage />} />
              <Route path="/pr-management" element={<PrManagementPage />} />
              <Route path="/companies" element={<CompaniesPage />} />
              <Route path="/placement-drives" element={<DrivesPage />} />
              <Route path="/messages" element={<MessagesPage />} />
              <Route path="/reports" element={<ReportsPage />} />
              <Route path="/audit-logs" element={<AuditLogsPage />} />
              <Route path="/contact-requests" element={<ContactRequestsPage />} />
              <Route path="/profile" element={<ProfilePage />} />
              <Route path="/student/drives" element={<StudentDrivesPage />} />
              <Route path="/student/interviews" element={<InterviewsPage />} />
              <Route path="/interviews" element={<InterviewsPage />} />
            </Route>
          </Route>

          <Route path="/404" element={<NotFoundPage />} />
          <Route path="*" element={<Navigate to="/404" replace />} />
        </Routes>
        <AppToaster />
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
