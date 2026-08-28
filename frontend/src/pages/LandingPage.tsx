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
} from 'lucide-react';
import { Button } from '../components/ui';
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

export default function LandingPage() {
  return (
    <div>
      {/* HERO */}
      <section className="relative overflow-hidden border-b border-neutral-200/70 bg-gradient-to-br from-neutral-50 via-white to-primary-50/50">
        <div className="pointer-events-none absolute -top-40 -right-40 h-[420px] w-[420px] rounded-full bg-primary-100/40 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-32 -left-20 h-[360px] w-[360px] rounded-full bg-info-100/40 blur-3xl" />
        <div className="relative mx-auto grid max-w-[1200px] items-center gap-12 px-5 py-16 md:grid-cols-2 md:py-24 lg:px-8">
          <div className="animate-slideUp">
            <div className="inline-flex items-center gap-2 rounded-full border border-primary-200/80 bg-white/80 px-3.5 py-1.5 mb-6 shadow-soft">
              <span className="h-1.5 w-1.5 rounded-full bg-primary-500 animate-pulse" />
              <span className="text-[13px] font-medium text-primary-700">
                College Placement Management
              </span>
            </div>
            <h1 className="text-[38px] font-extrabold leading-[1.08] tracking-[-0.03em] text-brand-navy md:text-[52px]">
              College placements,
              <br />
              organized{" "}
              <span className="bg-gradient-to-r from-primary-500 to-primary-700 bg-clip-text text-transparent">
                in one place.
              </span>
            </h1>
            <p className="mt-6 max-w-md text-[16.5px] leading-relaxed text-neutral-600">
              A centralized platform connecting Placement Officers, Coordinators,
              Placement Representatives and students through structured
              communication and placement workflows.
            </p>
            <div className="mt-8 flex flex-wrap items-center gap-3">
              <Link to="/register">
                <Button size="lg">Register as Student</Button>
              </Link>
              <Link to="/login">
                <Button variant="secondary" size="lg">
                  Login
                </Button>
              </Link>
            </div>
            <div className="mt-8 flex flex-wrap gap-x-6 gap-y-2">
              {['Role-based access', 'Real-time updates', 'Interview tracking'].map((item) => (
                <span key={item} className="flex items-center gap-1.5 text-[14px] text-neutral-500">
                  <CheckCircle2 size={15} className="text-primary-500" />
                  {item}
                </span>
              ))}
            </div>
          </div>
          <div className="hidden md:block animate-slideUp" style={{ animationDelay: '0.1s' }}>
            <div className="relative">
              <div className="absolute -inset-3 rounded-3xl bg-gradient-to-br from-primary-200/40 to-info-200/40 blur-xl" />
              <HeroPreview />
            </div>
          </div>
        </div>
      </section>

      {/* PURPOSE / HIERARCHY */}
      <section id="overview" className="bg-white py-20 md:py-28">
        <div className="mx-auto max-w-[1200px] px-5 lg:px-8">
          <div className="mx-auto max-w-2xl text-center">
            <span className="inline-block text-[12px] font-semibold uppercase tracking-[0.14em] text-primary-600 mb-3">
              Hierarchy
            </span>
            <h2 className="text-[30px] font-semibold tracking-tight text-neutral-900">
              One platform for the complete placement communication hierarchy.
            </h2>
            <p className="mt-4 text-[15px] leading-relaxed text-neutral-600">
              From the Placement Officer down to each student, every level of
              the placement cell communicates and operates through a single,
              structured system.
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
        </div>
      </section>

      {/* FEATURES */}
      <section id="features" className="bg-neutral-50 py-20 md:py-28">
        <div className="mx-auto max-w-[1200px] px-5 lg:px-8">
          <div className="mx-auto max-w-2xl text-center">
            <span className="inline-block text-[12px] font-semibold uppercase tracking-[0.14em] text-primary-600 mb-3">
              Capabilities
            </span>
            <h2 className="text-[30px] font-semibold tracking-tight text-neutral-900">
              Everything needed for campus placements.
            </h2>
            <p className="mt-4 text-[15px] leading-relaxed text-neutral-600">
              A focused set of tools that keep placement workflows clear and
              consistent.
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
      <section id="how-it-works" className="bg-white py-20 md:py-28">
        <div className="mx-auto max-w-[1200px] px-5 lg:px-8">
          <div className="mx-auto max-w-2xl text-center">
            <span className="inline-block text-[12px] font-semibold uppercase tracking-[0.14em] text-primary-600 mb-3">
              Process
            </span>
            <h2 className="text-[30px] font-semibold tracking-tight text-neutral-900">
              How it works
            </h2>
            <p className="mt-4 text-[15px] leading-relaxed text-neutral-600">
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

      {/* COMMUNICATION */}
      <section className="bg-brand-navy py-20 md:py-28">
        <div className="mx-auto grid max-w-[1200px] items-center gap-12 px-5 lg:grid-cols-2 lg:px-8">
          <div>
            <h2 className="text-[30px] font-semibold tracking-tight text-white">
              Structured communication.
              <br />
              <span className="text-primary-300">No missed placement updates.</span>
            </h2>
            <p className="mt-4 max-w-md text-[15px] leading-relaxed text-neutral-200">
              Announcements travel through the official hierarchy, so important
              updates reach every relevant student.
            </p>
            <div className="mt-6 flex flex-wrap gap-x-8 gap-y-3">
              {['Delivered', 'Read', 'Acknowledged'].map((item) => (
                <div
                  key={item}
                  className="flex items-center gap-2 text-[14px] text-neutral-100"
                >
                  <CheckCircle2 size={16} className="text-primary-300" />
                  {item}
                </div>
              ))}
            </div>
          </div>

          <div className="flex flex-col items-center gap-3">
            {[
              { label: 'Placement Officer', isFirst: true },
              { label: 'Placement Coordinator' },
              { label: 'Placement Representative' },
              { label: 'Students' },
            ].map((role, idx) => (
              <div key={role.label} className="flex flex-col items-center gap-3">
                <div className="flex h-12 w-52 items-center justify-center rounded-lg border border-white/10 bg-white/5 text-[14px] font-medium text-white">
                  {role.isFirst ? (
                    <span className="flex items-center gap-2">
                      <span className="h-2 w-2 rounded-full bg-primary-400" />
                      {role.label}
                    </span>
                  ) : (
                    role.label
                  )}
                </div>
                {idx < 3 && (
                  <span className="text-neutral-400">
                    <ChevronDown />
                  </span>
                )}
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="bg-neutral-50 py-20 md:py-28">
        <div className="mx-auto max-w-[1200px] px-5 lg:px-8">
          <div className="rounded-2xl bg-gradient-to-br from-primary-600 via-primary-500 to-primary-400 px-6 py-14 text-center md:px-16">
            <h2 className="mx-auto max-w-2xl text-[30px] font-semibold tracking-tight text-white">
              Ready to access the Placement Portal?
            </h2>
            <p className="mx-auto mt-4 max-w-md text-[15px] leading-relaxed text-primary-50">
              Students can create an account to manage their placement journey.
            </p>
            <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
              <Link to="/register">
                <Button size="lg" className="bg-white text-primary-700 hover:bg-primary-50 hover:text-primary-800 shadow-card">
                  Register as Student
                </Button>
              </Link>
              <Link to="/login">
                <Button variant="ghost" size="lg" className="text-white border border-white/30 hover:bg-white/10">
                  Login
                </Button>
              </Link>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}

function ChevronDown() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="m6 9 6 6 6-6" />
    </svg>
  );
}
