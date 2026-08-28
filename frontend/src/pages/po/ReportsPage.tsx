import { useState, useEffect } from 'react';
import { reportApi, departmentApi } from '../../api/api';
import type { Department } from '../../types';
import { Button, Select, PageHeader, PageContainer, Card, Skeleton } from '../../components/ui';
import { getErrorMessage } from '../../api/axios';
import { Download, Users, FileText, Building2, AlertCircle } from 'lucide-react';
import toast from 'react-hot-toast';

export default function ReportsPage() {
  const [downloadingType, setDownloadingType] = useState<string | null>(null);
  const [selectedDept, setSelectedDept] = useState('');

  const [departments, setDepartments] = useState<Department[]>([]);
  const [deptLoading, setDeptLoading] = useState(true);
  const [deptError, setDeptError] = useState<string | null>(null);

  useEffect(() => {
    departmentApi.getAll()
      .then((res) => setDepartments(res.data.data))
      .catch((err) => setDeptError(getErrorMessage(err)))
      .finally(() => setDeptLoading(false));
  }, []);

  const handleDownload = async (type: string) => {
    setDownloadingType(type);
    try {
      let res;
      if (type === 'students') {
        res = await reportApi.students();
      } else if (type === 'placements') {
        res = await reportApi.placements();
      } else if (type === 'department') {
        if (!selectedDept) {
          toast.error('Please select a department first');
          setDownloadingType(null);
          return;
        }
        res = await reportApi.students(Number(selectedDept));
      }

      if (res) {
        const blob = new Blob([res.data], { type: 'text/csv' });
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${type}_report.csv`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
        toast.success('Report downloaded successfully');
      }
    } catch {
      toast.error('Failed to download report');
    } finally {
      setDownloadingType(null);
    }
  };

  const reports = [
    {
      type: 'students',
      title: 'Student Report',
      description: 'Download a complete CSV of all students with profiles, academics, and placement status.',
      icon: <Users size={20} />,
      needsDept: false,
    },
    {
      type: 'placements',
      title: 'Placement Report',
      description: 'Export all placement records including company details, roles, and selection status.',
      icon: <FileText size={20} />,
      needsDept: false,
    },
    {
      type: 'department',
      title: 'Department-wise Report',
      description: 'Download student data filtered by a specific department.',
      icon: <Building2 size={20} />,
      needsDept: true,
    },
  ];

  return (
    <PageContainer>
      <PageHeader title="Reports" description="Download placement and student reports." />

      {deptError && (
        <div className="mb-6 p-4 bg-warning-50 rounded-lg border border-warning-200 flex items-center gap-3">
          <AlertCircle size={16} className="text-warning-600 shrink-0" />
          <p className="text-[14px] text-warning-700">Could not load departments for filtering. {deptError}</p>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {reports.map((report) => (
          <Card key={report.type} className="flex flex-col" padding="lg">
            <div className="flex items-start gap-4 mb-4">
              <div className="p-3 rounded-lg bg-brand-navy text-white shrink-0">
                {report.icon}
              </div>
              <div className="flex-1">
                <h3 className="text-[16px] font-semibold text-neutral-900">{report.title}</h3>
                <p className="text-[14px] text-neutral-500 mt-1 leading-relaxed">{report.description}</p>
              </div>
            </div>

            {report.needsDept && (
              <div className="mb-4">
                {deptLoading ? (
                  <Skeleton className="h-[44px] w-full" />
                ) : (
                  <Select
                    label="Department"
                    options={departments.map((d) => ({ label: d.name, value: d.id }))}
                    placeholder="Select department"
                    value={selectedDept}
                    onChange={(e) => setSelectedDept(e.target.value)}
                  />
                )}
              </div>
            )}

            <div className="mt-auto pt-2">
              <Button
                variant="secondary"
                className="w-full"
                loading={downloadingType === report.type}
                disabled={report.needsDept && !selectedDept}
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
