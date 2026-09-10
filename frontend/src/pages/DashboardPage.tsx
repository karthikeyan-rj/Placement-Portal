import { useEffectiveRole } from '../hooks/useEffectiveRole';
import PODashboard from './po/PODashboard';
import PCDashboard from './pc/PCDashboard';
import PRDashboard from './pr/PRDashboard';
import StudentDashboard from './student/StudentDashboard';

export default function DashboardPage() {
  const role = useEffectiveRole();

  switch (role) {
    case 'PO':
      return <PODashboard />;
    case 'PC':
      return <PCDashboard />;
    case 'PR':
      return <PRDashboard />;
    case 'STUDENT':
    default:
      return <StudentDashboard />;
  }
}