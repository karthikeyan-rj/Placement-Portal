interface WorkflowStepProps {
  step: number;
  title: string;
  description: string;
}

export default function WorkflowStep({ step, title, description }: WorkflowStepProps) {
  return (
    <div className="relative">
      <div className="flex items-center gap-4">
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-primary-200 bg-primary-50 text-[14px] font-semibold text-primary-700">
          {step}
        </span>
        <h3 className="text-[16px] font-semibold text-neutral-900">{title}</h3>
      </div>
      <p className="mt-3 text-[14px] leading-relaxed text-neutral-600">
        {description}
      </p>
    </div>
  );
}
