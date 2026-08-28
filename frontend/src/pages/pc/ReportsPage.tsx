import { useState } from 'react';
import { reportApi } from '../../api/api';
import { Card, Button, PageHeader, PageContainer, notify } from '../../components/ui';
import { Download, Users, BarChart3 } from 'lucide-react';

interface ReportCard {
  type: 'students' | 'placements';
  title: string;
  description: string;
  icon: React.ReactNode;
}

const reports: ReportCard[] = [
  {
    type: 'students',
    title: 'Student Report',
    description: 'Complete list of all students with profiles, academic records, and placement status.',
    icon: <Users size={24} />,
  },
  {
    type: 'placements',
    title: 'Placement Report',
    description: 'Detailed placement records including companies, roles, and selection status.',
    icon: <BarChart3 size={24} />,
  },
];

export default function ReportsPage() {
  const [loading, setLoading] = useState<string | null>(null);

  const handleDownload = async (type: string) => {
    setLoading(type);
    try {
      let res;
      if (type === 'students') res = await reportApi.students();
      else if (type === 'placements') res = await reportApi.placements();

      if (res) {
        const blob = new Blob([res.data], { type: 'text/csv' });
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${type}_report.csv`;
        a.click();
        window.URL.revokeObjectURL(url);
        notify.success(`${type} report downloaded successfully`);
      }
    } catch {
      notify.error('Failed to download report');
    } finally {
      setLoading(null);
    }
  };

  return (
    <PageContainer>
      <PageHeader
        title="Reports"
        description="Download student and placement reports."
      />

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {reports.map((report) => (
          <Card key={report.type} className="flex flex-col" padding="lg">
            <div className="flex items-start gap-4">
              <div className="p-3 rounded-lg bg-brand-navy text-white shrink-0">
                {report.icon}
              </div>
              <div className="flex-1">
                <h3 className="text-[16px] font-semibold text-neutral-900">
                  {report.title}
                </h3>
                <p className="text-[14px] text-neutral-500 mt-1">
                  {report.description}
                </p>
              </div>
            </div>
            <div className="mt-6">
              <Button
                variant="secondary"
                className="w-full"
                loading={loading === report.type}
                onClick={() => handleDownload(report.type)}
              >
                <Download size={14} />
                Download CSV
              </Button>
            </div>
          </Card>
        ))}
      </div>
    </PageContainer>
  );
}
