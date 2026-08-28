import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Menu, X } from 'lucide-react';
import { Button } from '../ui';

const NAV_LINKS = [
  { label: 'Overview', href: '#overview' },
  { label: 'Features', href: '#features' },
  { label: 'How It Works', href: '#how-it-works' },
];

export default function PublicNavbar() {
  const [open, setOpen] = useState(false);

  return (
    <header
      className="sticky top-0 z-40 h-16 border-b border-neutral-200/70 bg-white/85 backdrop-blur-md shadow-soft"
    >
      <div className="mx-auto flex h-full max-w-[1200px] items-center justify-between px-5 md:px-8">
        <Link to="/" className="flex items-center gap-2.5">
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-primary-400 to-primary-600 text-sm font-bold text-white shadow-card">
            PP
          </span>
          <span className="flex flex-col leading-tight">
            <span className="text-[15px] font-semibold text-neutral-900">
              Placement Portal
            </span>
            <span className="text-[11px] text-neutral-500">
              Placement Management System
            </span>
          </span>
        </Link>

        {/* Desktop nav */}
        <nav className="hidden items-center gap-7 md:flex">
          {NAV_LINKS.map((link) => (
            <a
              key={link.label}
              href={link.href}
              className="text-[14px] font-medium text-neutral-600 transition-colors hover:text-primary-600"
            >
              {link.label}
            </a>
          ))}
        </nav>

        <div className="hidden items-center gap-3 md:flex">
          <Link to="/login">
            <Button variant="ghost" size="sm">
              Login
            </Button>
          </Link>
          <Link to="/register">
            <Button size="sm">
              Register
            </Button>
          </Link>
        </div>

        {/* Mobile menu button */}
        <button
          className="md:hidden rounded-lg p-2 text-neutral-600 hover:bg-neutral-100"
          onClick={() => setOpen((v) => !v)}
          aria-label="Toggle menu"
        >
          {open ? <X size={20} /> : <Menu size={20} />}
        </button>
      </div>

      {/* Mobile menu */}
      {open && (
        <div className="border-t border-neutral-200/80 bg-white md:hidden">
          <div className="mx-auto max-w-[1200px] px-5 py-4">
            <nav className="flex flex-col gap-1">
              {NAV_LINKS.map((link) => (
                <a
                  key={link.label}
                  href={link.href}
                  onClick={() => setOpen(false)}
                  className="rounded-lg px-3 py-2.5 text-[15px] font-medium text-neutral-700 hover:bg-neutral-50"
                >
                  {link.label}
                </a>
              ))}
            </nav>
            <div className="mt-3 flex flex-col gap-2 border-t border-neutral-100 pt-4">
              <Link to="/login" onClick={() => setOpen(false)}>
                <Button variant="secondary" size="md" className="w-full">
                  Login
                </Button>
              </Link>
              <Link to="/register" onClick={() => setOpen(false)}>
                <Button size="md" className="w-full">
                  Register
                </Button>
              </Link>
            </div>
          </div>
        </div>
      )}
    </header>
  );
}
