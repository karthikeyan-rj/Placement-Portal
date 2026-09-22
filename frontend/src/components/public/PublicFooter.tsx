export default function PublicFooter() {
  return (
    <footer className="border-t border-neutral-200/80 bg-white">
      <div className="mx-auto max-w-[1200px] px-5 py-12 md:px-8">
        <div className="flex flex-col items-center gap-2 text-center">
          <div className="flex items-center gap-2.5">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary-600 text-sm font-semibold text-white">
              PP
            </span>
            <span className="text-[15px] font-semibold text-neutral-900">
              Placement Portal
            </span>
          </div>
          <p className="text-[14px] text-neutral-500">
            College Placement Management System
          </p>
          <p className="mt-4 text-[13px] text-neutral-500">
            © {new Date().getFullYear()} Placement Portal
          </p>
        </div>
      </div>
    </footer>
  );
}
