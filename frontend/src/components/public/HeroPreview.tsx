import { Users, Calendar, Briefcase } from 'lucide-react';

export default function HeroPreview() {
  const stats = [
    { label: 'Students', value: '1,247', icon: Users },
    { label: 'Upcoming Drives', value: '12', icon: Calendar },
  ];

  return (
    <div className="rounded-2xl border border-neutral-200/70 bg-white p-1.5 shadow-overlay">
      <div className="rounded-xl border border-neutral-100 overflow-hidden">
        {/* Preview header */}
        <div className="flex items-center justify-between border-b border-neutral-100 bg-neutral-50/60 px-4 py-3">
          <div className="flex items-center gap-2">
            <span className="h-2.5 w-2.5 rounded-full bg-primary-500" />
            <span className="text-[12px] font-semibold text-neutral-800">
              Placement Overview
            </span>
          </div>
          <span className="rounded-md bg-white px-2 py-0.5 text-[11px] font-medium text-neutral-500 border border-neutral-200">
            Dashboard
          </span>
        </div>

        {/* Stat cards */}
        <div className="grid grid-cols-2 gap-2 p-3">
          {stats.map((stat) => (
            <div key={stat.label} className="rounded-lg border border-neutral-100 bg-white p-3">
              <div className="flex items-center gap-1.5 text-neutral-500">
                <stat.icon size={13} />
                <span className="text-[11px] font-medium">{stat.label}</span>
              </div>
              <div className="mt-1 text-[22px] font-semibold text-neutral-900">
                {stat.value}
              </div>
            </div>
          ))}
        </div>

        {/* Latest drive */}
        <div className="mx-3 mb-3 rounded-lg border border-neutral-100 bg-neutral-50/60 p-3">
          <div className="text-[11px] font-medium uppercase tracking-wider text-neutral-400">
            Latest Drive
          </div>
          <div className="mt-1.5 flex items-center gap-2">
            <span className="flex h-7 w-7 items-center justify-center rounded-md bg-brand-navy text-white">
              <Briefcase size={14} />
            </span>
            <div>
              <div className="text-[13px] font-semibold text-neutral-900">
                TCS &middot; Software Engineer
              </div>
              <div className="text-[11px] text-neutral-500">
                <span className="inline-block w-1.5 h-1.5 rounded-full bg-primary-500 mr-1 align-middle" />
                Department &middot; CSE
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
