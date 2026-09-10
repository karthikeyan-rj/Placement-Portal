interface WorkflowStepProps {
  step: number;
  title: string;
  description: string;
}

export default function WorkflowStep({ step, title, description }: WorkflowStepProps) {
  return (
    <div className="relative flex flex-col items-start">
      <div className="mb-4 flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary-500 text-[14px] font-bold text-white shadow-soft">
        {step}
      </div>
      <h3 className="text-[15px] font-semibold text-neutral-900">{title}</h3>
      <p className="mt-1.5 text-[14px] leading-relaxed text-text-secondary">
        {description}
      </p>
    </div>
  );
}
