import type { ReactNode } from 'react';

interface RoleCardProps {
  icon: ReactNode;
  title: string;
  description: string;
}

export default function RoleCard({ icon, title, description }: RoleCardProps) {
  return (
    <div className="group bg-white rounded-[14px] border border-neutral-200/60 p-6 shadow-card transition-all duration-300 hover:shadow-raised hover:-translate-y-0.5 hover:border-primary-200/60">
      <div className="mb-4 flex h-11 w-11 items-center justify-center rounded-[10px] bg-neutral-900 text-white transition-all duration-300 group-hover:bg-primary-500 group-hover:shadow-soft">
        {icon}
      </div>
      <h3 className="text-[15px] font-semibold text-neutral-900">{title}</h3>
      <p className="mt-1.5 text-[14px] leading-relaxed text-text-secondary">
        {description}
      </p>
    </div>
  );
}
