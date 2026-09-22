import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { lazy, Suspense, type ReactNode } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { DevModeProvider } from './context/DevModeContext';
import { NotificationsProvider } from './context/NotificationsContext';
import PublicLayout from './components/public/PublicLayout';
import ProtectedRoute from './components/ProtectedRoute';
import AuthorizedRoute from './components/AuthorizedRoute';
import Layout from './components/layout/Layout';
import AppToaster from './components/ui/Toaster';

import LandingPage from './pages/LandingPage';
import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';
import UnauthorizedPage from './pages/UnauthorizedPage';
import NotFoundPage from './pages/NotFoundPage';

// Code-split the authenticated role pages so the login/landing first paint is light.
const DashboardPage = lazy(() => import('./pages/DashboardPage'));

const StudentsPage = lazy(() => import('./pages/po/StudentsPage'));
const DepartmentsPage = lazy(() => import('./pages/po/DepartmentsPage'));
const PcManagementPage = lazy(() => import('./pages/po/PcManagementPage'));
const PrManagementPage = lazy(() => import('./pages/po/PrManagementPage'));
const ReportsPage = lazy(() => import('./pages/po/ReportsPage'));
const AuditLogsPage = lazy(() => import('./pages/po/AuditLogsPage'));

const CompaniesPage = lazy(() => import('./pages/pc/CompaniesPage'));
const DrivesPage = lazy(() => import('./pages/pc/DrivesPage'));

const MessagesPage = lazy(() => import('./pages/pr/MessagesPage'));
const ContactRequestsPage = lazy(() => import('./pages/pr/ContactRequestsPage'));

const ProfilePage = lazy(() => import('./pages/profile/ProfilePage'));
const StudentDrivesPage = lazy(() => import('./pages/student/DrivesPage'));
const InterviewsPage = lazy(() => import('./pages/student/InterviewsPage'));
const ResumeAnalyzerPage = lazy(() => import('./pages/student/ResumeAnalyzerPage'));

function SuspenseRoute({ children }: { children: ReactNode }) {
  return <Suspense fallback={<PageLoader />}>{children}</Suspense>;
}

function PageLoader() {
  return <div style={{ padding: '2rem', color: '#666', fontFamily: 'inherit' }}>Loading…</div>;
}

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
        <DevModeProvider>
          <NotificationsProvider>
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
              <Route path="/dashboard" element={<SuspenseRoute><DashboardPage /></SuspenseRoute>} />
              <Route path="/" element={<Navigate to="/dashboard" replace />} />
              <Route path="/profile" element={<SuspenseRoute><ProfilePage /></SuspenseRoute>} />

              <Route element={<AuthorizedRoute allowedRoles={['PO', 'PC', 'PR']} />}>
                <Route path="/students" element={<SuspenseRoute><StudentsPage /></SuspenseRoute>} />
              </Route>

              <Route element={<AuthorizedRoute allowedRoles={['PO']} />}>
                <Route path="/departments" element={<SuspenseRoute><DepartmentsPage /></SuspenseRoute>} />
                <Route path="/pc-management" element={<SuspenseRoute><PcManagementPage /></SuspenseRoute>} />
                <Route path="/pr-management" element={<SuspenseRoute><PrManagementPage /></SuspenseRoute>} />
                <Route path="/audit-logs" element={<SuspenseRoute><AuditLogsPage /></SuspenseRoute>} />
              </Route>

              <Route element={<AuthorizedRoute allowedRoles={['PO', 'PC']} />}>
                <Route path="/companies" element={<SuspenseRoute><CompaniesPage /></SuspenseRoute>} />
                <Route path="/placement-drives" element={<SuspenseRoute><DrivesPage /></SuspenseRoute>} />
                <Route path="/reports" element={<SuspenseRoute><ReportsPage /></SuspenseRoute>} />
              </Route>

              <Route element={<AuthorizedRoute allowedRoles={['PO', 'PC', 'PR', 'STUDENT']} />}>
                <Route path="/messages" element={<SuspenseRoute><MessagesPage /></SuspenseRoute>} />
              </Route>

              <Route element={<AuthorizedRoute allowedRoles={['PC', 'PR']} />}>
                <Route path="/contact-requests" element={<SuspenseRoute><ContactRequestsPage /></SuspenseRoute>} />
              </Route>

              <Route element={<AuthorizedRoute allowedRoles={['STUDENT', 'PR']} />}>
                <Route path="/student/drives" element={<SuspenseRoute><StudentDrivesPage /></SuspenseRoute>} />
                <Route path="/student/interviews" element={<SuspenseRoute><InterviewsPage /></SuspenseRoute>} />
                <Route path="/interviews" element={<SuspenseRoute><InterviewsPage /></SuspenseRoute>} />
                <Route path="/resume-analyzer" element={<SuspenseRoute><ResumeAnalyzerPage /></SuspenseRoute>} />
              </Route>
            </Route>
          </Route>

          <Route path="/404" element={<NotFoundPage />} />
          <Route path="*" element={<Navigate to="/404" replace />} />
          </Routes>
          <AppToaster />
          </NotificationsProvider>
        </DevModeProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
