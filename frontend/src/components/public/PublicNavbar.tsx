import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Menu, X } from 'lucide-react';
import { Button } from '../ui';

const NAV_LINKS = [
  { label: 'Overview', id: 'overview' },
  { label: 'Features', id: 'features' },
  { label: 'How It Works', id: 'how-it-works' },
];

function scrollToSection(id: string) {
  const el = document.getElementById(id);
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

export default function PublicNavbar() {
  const [open, setOpen] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();

  const closeMenu = () => setOpen(false);

  const handleLogo = (e: React.MouseEvent) => {
    e.preventDefault();
    closeMenu();
    if (location.pathname === '/') {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } else {
      navigate('/');
      window.scrollTo({ top: 0 });
    }
  };

  const handleSectionClick = (id: string) => {
    closeMenu();
    if (location.pathname === '/') {
      scrollToSection(id);
    } else {
      navigate('/');
      // Landing page mounts after navigation; scroll once it exists.
      window.setTimeout(() => scrollToSection(id), 120);
    }
  };

  return (
    <header
      className="sticky top-0 z-40 h-16 border-b border-neutral-200/70 bg-white/85 backdrop-blur-md shadow-soft"
    >
      <div className="mx-auto flex h-full max-w-[1200px] items-center justify-between px-5 md:px-8">
        <a href="/" onClick={handleLogo} className="flex items-center gap-2.5">
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
        </a>

        {/* Desktop nav */}
        <nav className="hidden items-center gap-7 md:flex">
          {NAV_LINKS.map((link) => (
            <button
              key={link.id}
              type="button"
              onClick={() => handleSectionClick(link.id)}
              className="cursor-pointer text-[14px] font-medium text-neutral-600 transition-colors hover:text-primary-600"
            >

              {link.label}
            </button>
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
          type="button"
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
                <button
                  key={link.id}
                  type="button"
                  onClick={() => handleSectionClick(link.id)}
                  className="rounded-lg px-3 py-2.5 text-left text-[15px] font-medium text-neutral-700 hover:bg-neutral-50"
                >
                  {link.label}
                </button>
              ))}
            </nav>
            <div className="mt-3 flex flex-col gap-2 border-t border-neutral-100 pt-4">
              <Link to="/login" onClick={closeMenu}>
                <Button variant="secondary" size="md" className="w-full">
                  Login
                </Button>
              </Link>
              <Link to="/register" onClick={closeMenu}>
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
