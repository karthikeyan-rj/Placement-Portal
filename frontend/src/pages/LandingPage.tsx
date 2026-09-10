import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  GraduationCap,
  Briefcase,
  UserRound,
  ClipboardList,
  MessagesSquare,
  Target,
  ListChecks,
  CheckCircle2,
  Users,
  Building2,
  Contact,
  ChevronDown,
  Menu,
  X,
} from 'lucide-react';
import { Button } from '../components/ui';
import { LiquidBlob } from '../components/ui/Liquid';
import HeroPreview from '../components/public/HeroPreview';
import FeatureCard from '../components/public/FeatureCard';
import RoleCard from '../components/public/RoleCard';
import WorkflowStep from '../components/public/WorkflowStep';

const FEATURES = [
  {
    icon: <Briefcase size={20} />,
    title: 'Placement Drives',
    description:
      'View upcoming recruiters, roles, deadlines and eligibility criteria.',
  },
  {
    icon: <UserRound size={20} />,
    title: 'Student Profiles',
    description:
      'Maintain academic and placement information in one structured profile.',
  },
  {
    icon: <MessagesSquare size={20} />,
    title: 'Role-Based Communication',
    description:
      'Messages follow the official PO → PC → PR → Student hierarchy.',
  },
  {
    icon: <Target size={20} />,
    title: 'Eligibility Tracking',
    description:
      'Match students with placement criteria and opportunities.',
  },
  {
    icon: <ClipboardList size={20} />,
    title: 'Interview Tracking',
    description:
      'Maintain interview rounds, results and placement history.',
  },
  {
    icon: <ListChecks size={20} />,
    title: 'Message Acknowledgement',
    description:
      'Track read status and acknowledgement for important updates.',
  },
];

const ROLES = [
  {
    icon: <Building2 size={20} />,
    title: 'Placement Officer',
    description:
      'Oversee departments, coordinators, students and placement operations.',
  },
  {
    icon: <Contact size={20} />,
    title: 'Placement Coordinator',
    description:
      'Manage departmental students, representatives and placement communication.',
  },
  {
    icon: <Users size={20} />,
    title: 'Placement Representative',
    description:
      'Connect students with coordinators and assist with placement communication.',
  },
  {
    icon: <GraduationCap size={20} />,
    title: 'Student',
    description:
      'Track opportunities, eligibility, interviews and placement progress.',
  },
];

const STEPS = [
  {
    step: 1,
    title: 'Create Profile',
    description:
      'Register and complete your academic and placement information.',
  },
  {
    step: 2,
    title: 'Set Placement Preference',
    description:
      'Confirm whether you are interested in campus placements.',
  },
  {
    step: 3,
    title: 'Discover Opportunities',
    description:
      'View relevant companies, eligibility and upcoming drives.',
  },
  {
    step: 4,
    title: 'Track Your Journey',
    description:
      'Follow interviews, results and placement progress.',
  },
];

function Navbar() {
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <div className="fixed top-0 left-0 right-0 z-50 px-4 pt-3">
      <nav className="mx-auto flex max-w-[1120px] items-center justify-between glass rounded-[14px] border border-white/40 shadow-glass px-5 py-3">
        <a href="/" className="flex items-center gap-2.5">
          <span className="flex h-8 w-8 items-center justify-center rounded-[10px] bg-gradient-to-br from-primary-500 to-primary-600 text-sm font-bold text-white shadow-card">
            PP
          </span>
          <span className="text-[15px] font-semibold text-neutral-900">
            Placement Portal
          </span>
        </a>

        <div className="hidden items-center gap-8 md:flex">
          <nav className="flex items-center gap-7">
            {[
              { label: 'Overview', id: 'overview' },
              { label: 'Features', id: 'features' },
              { label: 'How It Works', id: 'how-it-works' },
            ].map((link) => (
              <button
                key={link.id}
                type="button"
                onClick={() => {
                  const el = document.getElementById(link.id);
                  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
                }}
                className="text-[14px] font-medium text-neutral-500 transition-colors hover:text-primary-600"
              >
                {link.label}
              </button>
            ))}
          </nav>

          <div className="flex items-center gap-2.5">
            <Link to="/login">
              <Button variant="ghost" size="sm">Login</Button>
            </Link>
            <Link to="/register">
              <Button size="sm">Register</Button>
            </Link>
          </div>
        </div>

        <button
          type="button"
          className="md:hidden p-1.5 rounded-[10px] text-neutral-500 hover:bg-neutral-100/80 transition-colors"
          onClick={() => setMobileOpen(!mobileOpen)}
          aria-label="Toggle menu"
        >
          {mobileOpen ? <X size={20} /> : <Menu size={20} />}
        </button>
      </nav>

      {mobileOpen && (
        <div className="md:hidden mx-auto max-w-[1120px] mt-2 glass rounded-[14px] border border-white/40 shadow-overlay p-4 animate-slideDown">
          <div className="flex flex-col gap-1">
            {[
              { label: 'Overview', id: 'overview' },
              { label: 'Features', id: 'features' },
              { label: 'How It Works', id: 'how-it-works' },
            ].map((link) => (
              <button
                key={link.id}
                type="button"
                onClick={() => {
                  const el = document.getElementById(link.id);
                  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
                  setMobileOpen(false);
                }}
                className="text-[14px] font-medium text-neutral-600 hover:text-primary-600 text-left px-3 py-2.5 rounded-[10px] hover:bg-neutral-100/60 transition-colors"
              >
                {link.label}
              </button>
            ))}
          </div>
          <div className="mt-3 pt-3 border-t border-neutral-200/60 flex flex-col gap-2">
            <Link to="/login" onClick={() => setMobileOpen(false)}>
              <Button variant="ghost" size="sm" className="w-full">Login</Button>
            </Link>
            <Link to="/register" onClick={() => setMobileOpen(false)}>
              <Button size="sm" className="w-full">Register</Button>
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}

function Footer() {
  return (
    <footer className="border-t border-neutral-200/60 bg-white">
      <div className="mx-auto max-w-[1120px] px-5 py-10 lg:px-8">
        <div className="flex flex-col items-center gap-4 md:flex-row md:justify-between">
          <div className="flex items-center gap-2.5">
            <span className="flex h-7 w-7 items-center justify-center rounded-[8px] bg-gradient-to-br from-primary-500 to-primary-600 text-xs font-bold text-white">
              PP
            </span>
            <span className="text-[14px] font-semibold text-neutral-800">
              Placement Portal
            </span>
          </div>
          <p className="text-[13px] text-text-secondary">
            A structured platform for campus placement management.
          </p>
        </div>
      </div>
    </footer>
  );
}

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-background">
      <Navbar />

      {/* HERO */}
      <section className="relative overflow-hidden bg-background pt-28 pb-20 md:pt-36 md:pb-28">
        <LiquidBlob
          color="rgba(102,92,246,0.06)"
          size={600}
          className="absolute -top-32 -right-32"
        />
        <LiquidBlob
          color="rgba(56,189,248,0.05)"
          size={400}
          className="absolute -bottom-20 -left-24"
          style={{ animationDelay: '3s' }}
        />
        <div className="relative mx-auto grid max-w-[1120px] items-center gap-12 px-5 md:grid-cols-2 lg:px-8">
          <div className="animate-slideUp">
            <h1 className="text-[42px] font-bold leading-[1.1] tracking-[-0.03em] text-neutral-900 md:text-[56px]">
              Campus placements,{' '}
              <span className="bg-gradient-to-r from-primary-500 via-primary-400 to-accent-300 bg-clip-text text-transparent">
                beautifully organized.
              </span>
            </h1>
            <p className="mt-6 max-w-md text-[16px] leading-relaxed text-text-secondary">
              A structured platform connecting students, representatives,
              coordinators and placement officers through a single system.
            </p>
            <div className="mt-8 flex flex-wrap items-center gap-3">
              <Link to="/register">
                <Button size="lg">Register as Student</Button>
              </Link>
              <Link to="/login">
                <Button variant="secondary" size="lg">Sign In</Button>
              </Link>
            </div>
            <div className="mt-8 flex flex-wrap gap-x-6 gap-y-2">
              {['Role-based access', 'Real-time updates', 'Interview tracking'].map((item) => (
                <span key={item} className="flex items-center gap-1.5 text-[14px] text-text-secondary">
                  <CheckCircle2 size={15} className="text-primary-500" />
                  {item}
                </span>
              ))}
            </div>
          </div>
          <div className="hidden md:block animate-slideUp" style={{ animationDelay: '0.15s' }}>
            <HeroPreview />
          </div>
        </div>
      </section>

      {/* ROLE HIERARCHY */}
      <section id="overview" className="bg-background py-20 md:py-28">
        <div className="mx-auto max-w-[1120px] px-5 lg:px-8">
          <div className="mx-auto max-w-2xl text-center">
            <span className="inline-block text-[12px] font-semibold uppercase tracking-[0.14em] text-primary-500 mb-3">
              Hierarchy
            </span>
            <h2 className="text-[28px] font-semibold tracking-[-0.02em] text-neutral-900 md:text-[32px]">
              One platform for the complete placement communication hierarchy.
            </h2>
            <p className="mt-4 text-[15px] leading-relaxed text-text-secondary">
              From the Placement Officer down to each student, every level of
              the placement cell communicates through a single, structured system.
            </p>
          </div>

          <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            {ROLES.map((role) => (
              <RoleCard
                key={role.title}
                icon={role.icon}
                title={role.title}
                description={role.description}
              />
            ))}
          </div>

          {/* Visual flow */}
          <div className="mt-12 hidden lg:flex items-center justify-center gap-4">
            {['Placement Officer', 'Coordinator', 'Representative', 'Students'].map((label, i) => (
              <div key={label} className="flex items-center gap-4">
                <div className="flex h-11 w-44 items-center justify-center rounded-[10px] bg-white border border-neutral-200/60 text-[13px] font-medium text-neutral-700 shadow-soft">
                  {label}
                </div>
                {i < 3 && (
                  <ChevronDown size={18} className="text-neutral-300 -rotate-90" />
                )}
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* FEATURES */}
      <section id="features" className="bg-background py-20 md:py-28">
        <div className="mx-auto max-w-[1120px] px-5 lg:px-8">
          <div className="mx-auto max-w-2xl text-center">
            <span className="inline-block text-[12px] font-semibold uppercase tracking-[0.14em] text-primary-500 mb-3">
              Capabilities
            </span>
            <h2 className="text-[28px] font-semibold tracking-[-0.02em] text-neutral-900 md:text-[32px]">
              Everything needed for campus placements.
            </h2>
            <p className="mt-4 text-[15px] leading-relaxed text-text-secondary">
              A focused set of tools that keep placement workflows clear and consistent.
            </p>
          </div>

          <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map((feature) => (
              <FeatureCard
                key={feature.title}
                icon={feature.icon}
                title={feature.title}
                description={feature.description}
              />
            ))}
          </div>
        </div>
      </section>

      {/* HOW IT WORKS */}
      <section id="how-it-works" className="bg-background py-20 md:py-28">
        <div className="mx-auto max-w-[1120px] px-5 lg:px-8">
          <div className="mx-auto max-w-2xl text-center">
            <span className="inline-block text-[12px] font-semibold uppercase tracking-[0.14em] text-primary-500 mb-3">
              Process
            </span>
            <h2 className="text-[28px] font-semibold tracking-[-0.02em] text-neutral-900 md:text-[32px]">
              How it works
            </h2>
            <p className="mt-4 text-[15px] leading-relaxed text-text-secondary">
              A simple path from registration to placement for every student.
            </p>
          </div>

          <div className="mt-12 grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
            {STEPS.map((step) => (
              <WorkflowStep
                key={step.step}
                step={step.step}
                title={step.title}
                description={step.description}
              />
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="bg-background py-20 md:py-28">
        <div className="mx-auto max-w-[1120px] px-5 lg:px-8">
          <div className="relative overflow-hidden rounded-[18px] bg-brand-navy px-6 py-14 text-center md:px-16">
            <LiquidBlob
              color="rgba(102,92,246,0.15)"
              size={300}
              className="absolute -top-16 -right-16"
            />
            <h2 className="relative mx-auto max-w-2xl text-[28px] font-semibold tracking-[-0.02em] md:text-[32px]">
              <span className="bg-gradient-to-r from-white via-white to-white/70 bg-clip-text text-transparent">
                Ready to access the Placement Portal?
              </span>
            </h2>
            <p className="relative mx-auto mt-4 max-w-md text-[15px] leading-relaxed text-white/60">
              Students can create an account to manage their placement journey.
            </p>
            <div className="relative mt-8 flex flex-wrap items-center justify-center gap-3">
              <Link to="/register">
                <Button size="lg">Register as Student</Button>
              </Link>
              <Link to="/login">
                <Button variant="secondary" size="lg">Sign In</Button>
              </Link>
            </div>
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
}
