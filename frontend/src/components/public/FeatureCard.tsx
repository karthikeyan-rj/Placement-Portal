import type { ReactNode } from 'react';

interface FeatureCardProps {
  icon: ReactNode;
  title: string;
  description: string;
}

export default function FeatureCard({ icon, title, description }: FeatureCardProps) {
  return (
    <div className="group rounded-xl border border-neutral-200/70 bg-white p-6 shadow-card transition-all duration-200 hover:border-primary-200 hover:shadow-raised hover:-translate-y-1">
      <div className="mb-4 flex h-11 w-11 items-center justify-center rounded-lg bg-primary-50 text-primary-600 transition-colors duration-150 group-hover:bg-primary-100">
        {icon}
      </div>
      <h3 className="text-[16px] font-semibold text-neutral-900">{title}</h3>
      <p className="mt-1.5 text-[14px] leading-relaxed text-neutral-600">
        {description}
      </p>
    </div>
  );
}
