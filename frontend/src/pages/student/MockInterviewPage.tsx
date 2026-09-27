import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertCircle,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  CircleDashed,
  Clock3,
  History,
  Lightbulb,
  Mic,
  Send,
  Sparkles,
  Target,
  ThumbsUp,
  TriangleAlert,
} from 'lucide-react';
import { mockInterviewApi } from '../../api/api';
import { getErrorMessage } from '../../api/axios';
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  PageContainer,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  StatCard,
  Tabs,
  Textarea,
  notify,
} from '../../components/ui';
import type {
  MockDifficultyFilter,
  MockInterviewOptionsResponse,
  MockInterviewResultResponse,
  MockInterviewSessionResponse,
  MockInterviewStatus,
  MockInterviewSummaryResponse,
  MockInterviewType,
  MockSelfRating,
} from '../../types';

const DIFFICULTIES: { value: MockDifficultyFilter; label: string }[] = [
  { value: 'MIXED', label: 'Mixed difficulty' },
  { value: 'EASY', label: 'Easy' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'HARD', label: 'Hard' },
];

const RATINGS: { value: MockSelfRating; label: string; className: string }[] = [
  {
    value: 'NEED_PRACTICE',
    label: 'Need practice',
    className: 'border-warning-300 bg-warning-50 text-warning-700 hover:border-warning-400',
  },
  {
    value: 'PARTIALLY_CONFIDENT',
    label: 'Partly confident',
    className: 'border-info-300 bg-info-50 text-info-700 hover:border-info-400',
  },
  {
    value: 'CONFIDENT',
    label: 'Confident',
    className: 'border-success-300 bg-success-50 text-success-700 hover:border-success-400',
  },
];

const STATUS_VARIANT: Record<MockInterviewStatus, 'info' | 'success' | 'warning'> = {
  IN_PROGRESS: 'info',
  COMPLETED: 'success',
  ABANDONED: 'warning',
};

const HISTORY_PAGE_SIZE = 10;

function formatClock(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const h = Math.floor(safe / 3600);
  const m = Math.floor((safe % 3600) / 60);
  const s = safe % 60;
  const pad = (n: number) => String(n).padStart(2, '0');
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}

function formatDate(value: string | null | undefined): string {
  if (!value) return '-';
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? '-' : d.toLocaleString();
}

function difficultyBadge(value: string) {
  if (value === 'EASY') return <Badge variant="success" size="sm">{value}</Badge>;
  if (value === 'HARD') return <Badge variant="danger" size="sm">{value}</Badge>;
  return <Badge variant="warning" size="sm">{value}</Badge>;
}

// =====================================================================
// Interview screen
// =====================================================================

function InterviewScreen({
  session,
  maxAnswerLength,
  onCompleted,
}: {
  session: MockInterviewSessionResponse;
  maxAnswerLength: number;
  onCompleted: (sessionId: number) => void;
}) {
  const [current, setCurrent] = useState(() => {
    // Resume where the student stopped: the first question still unanswered.
    const index = session.questions.findIndex((q) => !q.answered);
    return index === -1 ? 0 : index;
  });
  const [answers, setAnswers] = useState<Record<number, string>>(() =>
    Object.fromEntries(
      session.questions.map((q) => [q.sessionQuestionId, q.studentAnswer ?? ''])
    )
  );
  const [ratings, setRatings] = useState<Record<number, MockSelfRating | null>>(() =>
    Object.fromEntries(session.questions.map((q) => [q.sessionQuestionId, q.selfRating]))
  );
  const [saveState, setSaveState] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle');
  const [elapsed, setElapsed] = useState(session.elapsedSeconds ?? 0);
  const [busy, setBusy] = useState(false);
  const [confirmFinish, setConfirmFinish] = useState(false);
  const [confirmAbandon, setConfirmAbandon] = useState(false);

  const question = session.questions[current];
  const timers = useRef<{ answer?: number; save?: number }>({});

  // The clock is derived from the server-provided start, so a refresh resumes
  // the real elapsed time rather than restarting a local timer.
  useEffect(() => {
    const startedAt = new Date(session.startedAt).getTime();
    if (Number.isNaN(startedAt)) return;
    const tick = () => setElapsed(Math.max(0, Math.floor((Date.now() - startedAt) / 1000)));
    tick();
    const id = window.setInterval(tick, 1000);
    return () => window.clearInterval(id);
  }, [session.startedAt]);

  // Flush a pending debounced save when the component goes away.
  useEffect(
    () => () => {
      if (timers.current.answer) window.clearTimeout(timers.current.answer);
      if (timers.current.save) window.clearTimeout(timers.current.save);
    },
    []
  );

  const persist = useCallback(
    async (sessionQuestionId: number, text: string, rating?: MockSelfRating | null) => {
      setSaveState('saving');
      try {
        if (text !== undefined) {
          await mockInterviewApi.saveAnswer(session.id, sessionQuestionId, text);
        }
        if (rating !== undefined) {
          await mockInterviewApi.saveSelfRating(session.id, sessionQuestionId, rating);
        }
        setSaveState('saved');
        window.setTimeout(() => setSaveState('idle'), 2000);
      } catch (err) {
        setSaveState('error');
        notify.error(getErrorMessage(err));
      }
    },
    [session.id]
  );

  const onAnswerChange = (value: string) => {
    if (!question) return;
    setAnswers((prev) => ({ ...prev, [question.sessionQuestionId]: value }));
    setSaveState('saving');
    // Autosave after a short idle period, so typing is not interrupted.
    if (timers.current.answer) window.clearTimeout(timers.current.answer);
    timers.current.answer = window.setTimeout(() => {
      persist(question.sessionQuestionId, value);
    }, 1500);
  };

  const flushAnswer = (sessionQuestionId: number, value: string) => {
    if (timers.current.answer) {
      window.clearTimeout(timers.current.answer);
      timers.current.answer = undefined;
    }
    if (value.trim() === '') return;
    void persist(sessionQuestionId, value);
  };

  const onRate = async (rating: MockSelfRating) => {
    if (!question) return;
    const next = ratings[question.sessionQuestionId] === rating ? null : rating;
    setRatings((prev) => ({ ...prev, [question.sessionQuestionId]: next }));
    setSaveState('saving');
    try {
      await mockInterviewApi.saveSelfRating(session.id, question.sessionQuestionId, next);
      setSaveState('saved');
      window.setTimeout(() => setSaveState('idle'), 2000);
    } catch (err) {
      setRatings((prev) => ({ ...prev, [question.sessionQuestionId]: ratings[question.sessionQuestionId] }));
      setSaveState('error');
      notify.error(getErrorMessage(err));
    }
  };

  const goTo = (index: number) => {
    if (index < 0 || index >= session.questions.length) return;
    if (question) flushAnswer(question.sessionQuestionId, answers[question.sessionQuestionId] ?? '');
    setCurrent(index);
  };

  const finish = async () => {
    setBusy(true);
    try {
      if (question) flushAnswer(question.sessionQuestionId, answers[question.sessionQuestionId] ?? '');
      await mockInterviewApi.complete(session.id);
      setConfirmFinish(false);
      onCompleted(session.id);
    } catch (err) {
      notify.error(getErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const abandon = async () => {
    setBusy(true);
    try {
      await mockInterviewApi.abandon(session.id);
      setConfirmAbandon(false);
      onCompleted(session.id);
    } catch (err) {
      notify.error(getErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  if (!question) return null;

  const answeredCount = Object.values(answers).filter((a) => a.trim() !== '').length;
  const length = (answers[question.sessionQuestionId] ?? '').length;
  const overLimit = length > maxAnswerLength;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="teal">{session.interviewTypeLabel}</Badge>
          <Badge variant={STATUS_VARIANT[session.status]}>{session.status.replace('_', ' ')}</Badge>
          <span className="inline-flex items-center gap-1.5 text-[13px] text-text-secondary">
            <Clock3 size={14} />
            <span className="font-mono tabular-nums">{formatClock(elapsed)}</span>
          </span>
        </div>
        <div className="flex items-center gap-2">
          <span
            className="text-[12.5px]"
            aria-live="polite"
            role="status"
            data-testid="save-state"
          >
            {saveState === 'saving' && <span className="text-text-secondary">Saving...</span>}
            {saveState === 'saved' && (
              <span className="inline-flex items-center gap-1 text-success-600">
                <CheckCircle2 size={13} /> Saved
              </span>
            )}
            {saveState === 'error' && (
              <span className="inline-flex items-center gap-1 text-danger-600">
                <AlertCircle size={13} /> Not saved
              </span>
            )}
          </span>
          <Button variant="ghost" size="sm" onClick={() => setConfirmAbandon(true)} disabled={busy}>
            Abandon
          </Button>
          <Button size="sm" onClick={() => setConfirmFinish(true)} disabled={busy}>
            Finish interview
          </Button>
        </div>
      </div>

      <div className="grid gap-5 lg:grid-cols-[1fr_260px]">
        <div className="space-y-4">
          <Card padding="lg">
            <div className="mb-3 flex flex-wrap items-center gap-2">
              <span className="text-[12.5px] font-semibold text-neutral-500">
                Question {current + 1} of {session.questions.length}
              </span>
              {difficultyBadge(question.difficulty)}
              <Badge variant="neutral" size="sm">{question.moduleCode}</Badge>
              <span className="text-[12.5px] text-text-secondary">{question.topicTitle}</span>
            </div>
            <p className="text-[15px] leading-relaxed text-neutral-900">{question.question}</p>
          </Card>

          <Card
            title="Your answer"
            subtitle={`Answers save automatically. Maximum ${maxAnswerLength.toLocaleString()} characters.`}
            icon={<Sparkles size={16} />}
          >
            <Textarea
              label="Answer in your own words"
              rows={10}
              value={answers[question.sessionQuestionId] ?? ''}
              onChange={(e) => onAnswerChange(e.target.value)}
              onBlur={() => flushAnswer(question.sessionQuestionId, answers[question.sessionQuestionId] ?? '')}
              maxLength={maxAnswerLength}
              disabled={busy}
              error={overLimit ? `Maximum ${maxAnswerLength.toLocaleString()} characters.` : undefined}
              data-testid="answer-input"
            />
            <div className="mt-1.5 flex items-center justify-between text-[12px] text-text-secondary">
              <span>{answeredCount} of {session.questions.length} answered</span>
              <span className={overLimit ? 'text-danger-600' : ''}>
                {length.toLocaleString()} / {maxAnswerLength.toLocaleString()}
              </span>
            </div>
          </Card>

          <Card title="How confident are you?" subtitle="Self-assessment only. It is never scored as correctness.">
            <div className="flex flex-wrap gap-2">
              {RATINGS.map((r) => {
                const active = ratings[question.sessionQuestionId] === r.value;
                return (
                  <button
                    key={r.value}
                    type="button"
                    aria-pressed={active}
                    onClick={() => onRate(r.value)}
                    className={`rounded-[9px] border px-3 py-1.5 text-[13px] font-medium transition-all ${r.className} ${
                      active ? 'ring-2 ring-primary-500/20' : 'border-neutral-300 bg-white text-neutral-600'
                    }`}
                  >
                    {r.label}
                  </button>
                );
              })}
            </div>
          </Card>

          <div className="flex items-center justify-between gap-3">
            <Button
              variant="secondary"
              onClick={() => goTo(current - 1)}
              disabled={current === 0}
            >
              <ChevronLeft size={15} /> Previous
            </Button>
            <Button
              onClick={() => goTo(current + 1)}
              disabled={current === session.questions.length - 1}
            >
              Next <ChevronRight size={15} />
            </Button>
          </div>
        </div>

        <Card title="Navigator" className="lg:sticky lg:top-4 lg:self-start">
          <div className="grid grid-cols-5 gap-2 lg:grid-cols-4">
            {session.questions.map((q, index) => {
              const text = answers[q.sessionQuestionId] ?? '';
              const done = text.trim() !== '';
              const isCurrent = index === current;
              return (
                <button
                  key={q.sessionQuestionId}
                  type="button"
                  onClick={() => goTo(index)}
                  aria-current={isCurrent ? 'true' : undefined}
                  title={`Question ${index + 1}${done ? ' (answered)' : ''}`}
                  className={`flex h-9 items-center justify-center rounded-[8px] border text-[12.5px] font-semibold transition-all ${
                    isCurrent
                      ? 'border-primary-500 bg-primary-500 text-white'
                      : done
                        ? 'border-success-200 bg-success-50 text-success-700'
                        : 'border-neutral-200 bg-white text-neutral-500 hover:border-neutral-300'
                  }`}
                >
                  {index + 1}
                </button>
              );
            })}
          </div>
          <p className="mt-3 flex items-center gap-1.5 text-[12px] text-text-secondary">
            <CircleDashed size={12} /> Unanswered questions are left open; nothing is scored for you.
          </p>
        </Card>
      </div>

      <ConfirmDialog
        isOpen={confirmFinish}
        onClose={() => setConfirmFinish(false)}
        onConfirm={finish}
        loading={busy}
        title="Finish this interview?"
        message="Your answers will be locked and the reference guidance becomes available for review. You cannot edit answers afterwards."
        confirmLabel="Finish interview"
      />
      <ConfirmDialog
        isOpen={confirmAbandon}
        onClose={() => setConfirmAbandon(false)}
        onConfirm={abandon}
        loading={busy}
        title="Abandon this interview?"
        message="The interview is kept in your history as abandoned, and its answers are closed. Questions from an abandoned run do not count as practised."
        confirmLabel="Abandon interview"
        variant="danger"
      />
    </div>
  );
}

// =====================================================================
// Results screen
// =====================================================================

function ResultScreen({
  result,
  session,
  onNewInterview,
}: {
  result: MockInterviewResultResponse;
  session: MockInterviewSessionResponse;
  onNewInterview: () => void;
}) {
  const [expanded, setExpanded] = useState<number | null>(session.questions[0]?.sessionQuestionId ?? null);

  return (
    <div className="space-y-5">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Questions" value={result.questionCount} icon={<Mic size={15} />} accent="teal" />
        <StatCard label="Answered" value={result.answeredCount} icon={<CheckCircle2 size={15} />} accent="info" />
        <StatCard label="Left open" value={result.unansweredCount} icon={<CircleDashed size={15} />} />
        <StatCard
          label="Duration"
          value={formatClock(result.durationSeconds ?? 0)}
          icon={<Clock3 size={15} />}
        />
      </div>

      <Card icon={<TriangleAlert size={16} />} variant="secondary">
        <p className="text-[13.5px] leading-relaxed text-neutral-700">
          These questions are descriptive, so there is no correctness score to show. What you see
          below is what you covered, what you answered, and your own confidence. Use the
          reference guidance to compare it with how you answered.
        </p>
      </Card>

      <div className="grid gap-5 lg:grid-cols-2">
        <Card title="Self-assessment" icon={<Target size={16} />}>
          <div className="space-y-2 text-[13.5px]">
            {[
              ['Confident', result.selfAssessment.confident, 'text-success-600'],
              ['Partly confident', result.selfAssessment.partiallyConfident, 'text-info-600'],
              ['Need practice', result.selfAssessment.needPractice, 'text-warning-600'],
              ['Not rated', result.selfAssessment.unrated, 'text-neutral-500'],
            ].map(([label, value, tone]) => (
              <div key={String(label)} className="flex items-center justify-between">
                <span className="text-neutral-600">{label}</span>
                <span className={`font-semibold tabular-nums ${tone}`}>{String(value)}</span>
              </div>
            ))}
          </div>
        </Card>

        <Card title="Module coverage" icon={<Lightbulb size={16} />}>
          <div className="space-y-2.5">
            {result.moduleBreakdown.map((m) => (
              <div key={m.moduleId}>
                <div className="flex items-center justify-between text-[13px]">
                  <span className="truncate text-neutral-700">{m.moduleTitle}</span>
                  <span className="tabular-nums text-text-secondary">
                    {m.answeredCount}/{m.questionCount}
                  </span>
                </div>
                <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-neutral-100">
                  <div
                    className="h-full rounded-full bg-primary-500 transition-all"
                    style={{
                      width: `${m.questionCount === 0 ? 0 : (m.answeredCount / m.questionCount) * 100}%`,
                    }}
                  />
                </div>
              </div>
            ))}
          </div>
        </Card>
      </div>

      {result.areasToReview.length > 0 && (
        <Card title="Areas to review" subtitle="From your own need-practice marks and unanswered questions." icon={<Lightbulb size={16} />}>
          <ul className="space-y-2">
            {result.areasToReview.map((a) => (
              <li
                key={`${a.moduleCode}-${a.topicTitle}`}
                className="flex flex-wrap items-center justify-between gap-2 rounded-[9px] border border-neutral-200 px-3 py-2"
              >
                <span className="text-[13.5px] text-neutral-800">
                  <span className="font-medium">{a.topicTitle}</span>
                  <span className="ml-2 text-[12.5px] text-text-secondary">{a.moduleCode}</span>
                </span>
                <span className="text-[12.5px] text-text-secondary">{a.reason}</span>
              </li>
            ))}
          </ul>
        </Card>
      )}

      <Card
        title="Review your answers"
        subtitle="Reference guidance is now visible. Self-assessment can still be changed."
      >
        <div className="space-y-2.5">
          {session.questions.map((q, index) => {
            const open = expanded === q.sessionQuestionId;
            return (
              <div key={q.sessionQuestionId} className="rounded-[10px] border border-neutral-200">
                <button
                  type="button"
                  onClick={() => setExpanded(open ? null : q.sessionQuestionId)}
                  className="flex w-full items-start gap-2.5 px-3.5 py-3 text-left"
                >
                  <span
                    className={`mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-[12px] font-semibold ${
                      q.answered ? 'bg-success-50 text-success-700' : 'bg-neutral-100 text-neutral-500'
                    }`}
                  >
                    {q.answered ? <CheckCircle2 size={13} /> : index + 1}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block text-[13.5px] leading-relaxed text-neutral-800">{q.question}</span>
                    <span className="mt-1 flex flex-wrap items-center gap-1.5">
                      {difficultyBadge(q.difficulty)}
                      <Badge variant="neutral" size="sm">{q.moduleCode}</Badge>
                      {q.selfRating && (
                        <Badge variant="warning" size="sm">
                          {q.selfRating.replace(/_/g, ' ').toLowerCase()}
                        </Badge>
                      )}
                    </span>
                  </span>
                </button>
                {open && (
                  <div className="space-y-3 border-t border-neutral-100 px-3.5 py-3">
                    <div>
                      <p className="mb-1 text-[12px] font-semibold uppercase tracking-wide text-neutral-500">
                        Your answer
                      </p>
                      <p className="whitespace-pre-wrap text-[13.5px] leading-relaxed text-neutral-800">
                        {q.studentAnswer?.trim() || 'Not answered.'}
                      </p>
                    </div>
                    <div>
                      <p className="mb-1 text-[12px] font-semibold uppercase tracking-wide text-neutral-500">
                        Reference guidance
                      </p>
                      <p className="whitespace-pre-wrap rounded-[9px] bg-info-50/60 px-3 py-2 text-[13.5px] leading-relaxed text-neutral-800">
                        {q.referenceAnswer || 'No guidance available.'}
                      </p>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      {RATINGS.map((r) => (
                        <button
                          key={r.value}
                          type="button"
                          aria-pressed={q.selfRating === r.value}
                          onClick={async () => {
                            const next = q.selfRating === r.value ? null : r.value;
                            try {
                              await mockInterviewApi.saveSelfRating(
                                result.id,
                                q.sessionQuestionId,
                                next
                              );
                              notify.success('Self-assessment updated.');
                              window.location.reload();
                            } catch (err) {
                              notify.error(getErrorMessage(err));
                            }
                          }}
                          className={`rounded-[8px] border px-2.5 py-1 text-[12.5px] font-medium transition-all ${
                            q.selfRating === r.value
                              ? r.className
                              : 'border-neutral-200 bg-white text-neutral-500'
                          }`}
                        >
                          {r.label}
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </Card>

      <div className="flex justify-end">
        <Button onClick={onNewInterview}>
          Start another interview <Send size={15} />
        </Button>
      </div>
    </div>
  );
}

// =====================================================================
// Setup screen
// =====================================================================

function SetupScreen({
  options,
  onStarted,
  resumeLabel,
  onResume,
}: {
  options: MockInterviewOptionsResponse;
  onStarted: (session: MockInterviewSessionResponse) => void;
  resumeLabel: string;
  onResume: () => void;
}) {
  const [mode, setMode] = useState<MockInterviewType>('MIXED');
  const [difficulty, setDifficulty] = useState<MockDifficultyFilter>('MIXED');
  const [count, setCount] = useState<number>(options.allowedQuestionCounts[0] ?? 5);
  const [starting, setStarting] = useState(false);

  const modeOption = useMemo(
    () => options.modes.find((m) => m.code === mode),
    [options.modes, mode]
  );
  const available = modeOption?.availableByDifficulty?.[difficulty] ?? 0;
  const effectiveCount = Math.min(count, available);

  const start = async () => {
    if (effectiveCount === 0) return;
    setStarting(true);
    try {
      const { data } = await mockInterviewApi.start({
        interviewType: mode,
        difficulty,
        questionCount: count,
      });
      if (data.data) onStarted(data.data);
    } catch (err) {
      notify.error(getErrorMessage(err));
    } finally {
      setStarting(false);
    }
  };

  return (
    <div className="grid gap-5 lg:grid-cols-[1fr_320px]">
      <div className="space-y-4">
        <Card title="Interview type" subtitle="Each mode draws on real preparation content.">
          <div className="space-y-2.5">
            {options.modes.map((m) => (
              <button
                key={m.code}
                type="button"
                onClick={() => setMode(m.code)}
                aria-pressed={mode === m.code}
                className={`flex w-full items-start gap-3 rounded-[10px] border px-3.5 py-3 text-left transition-all ${
                  mode === m.code
                    ? 'border-primary-400 bg-primary-50/50'
                    : 'border-neutral-200 bg-white hover:border-neutral-300'
                }`}
              >
                <span className="min-w-0 flex-1">
                  <span className="block text-[14px] font-semibold text-neutral-900">{m.label}</span>
                  <span className="mt-0.5 block text-[12.5px] leading-relaxed text-text-secondary">
                    {m.description}
                  </span>
                  <span className="mt-1.5 flex flex-wrap gap-1">
                    {m.modules.map((mod) => (
                      <span
                        key={mod.id}
                        className="rounded-full bg-neutral-100 px-2 py-0.5 text-[11px] text-neutral-600"
                      >
                        {mod.code}
                      </span>
                    ))}
                  </span>
                </span>
                <span
                  className={`mt-1 h-4 w-4 shrink-0 rounded-full border-2 ${
                    mode === m.code ? 'border-primary-500 bg-primary-500' : 'border-neutral-300'
                  }`}
                  aria-hidden
                />
              </button>
            ))}
          </div>
        </Card>

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Difficulty"
            value={difficulty}
            onChange={(e) => setDifficulty(e.target.value as MockDifficultyFilter)}
            options={DIFFICULTIES.map((d) => ({ label: d.label, value: d.value }))}
          />
          <Select
            label="Number of questions"
            value={count}
            onChange={(e) => setCount(Number(e.target.value))}
            options={options.allowedQuestionCounts.map((n) => ({ label: `${n} questions`, value: n }))}
          />
        </div>

        {available < count && (
          <Card variant="secondary" icon={<AlertCircle size={16} />}>
            <p className="text-[13.5px] leading-relaxed text-neutral-700">
              Only {available} {available === 1 ? 'question is' : 'questions are'} available for this
              mode and difficulty, so this interview will use {effectiveCount}. Questions are never
              repeated to pad a session.
            </p>
          </Card>
        )}
      </div>

      <div className="space-y-4">
        <Card title="Ready when you are" icon={<Mic size={16} />}>
          <div className="space-y-3 text-[13.5px]">
            <div className="flex items-center justify-between">
              <span className="text-neutral-600">Questions</span>
              <span className="font-semibold tabular-nums text-neutral-900">{effectiveCount}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-neutral-600">Scoring</span>
              <span className="text-neutral-900">Not available</span>
            </div>
            <p className="rounded-[9px] bg-neutral-50 px-3 py-2 text-[12.5px] leading-relaxed text-neutral-600">
              The question bank is descriptive, so no correctness score is produced. You get real
              reference guidance and your own self-assessment to review afterwards.
            </p>
            <Button onClick={start} loading={starting} disabled={effectiveCount === 0} className="w-full">
              Start interview
            </Button>
          </div>
        </Card>

        <Card title="In progress" icon={<Clock3 size={16} />}>
          <p className="mb-3 text-[13px] leading-relaxed text-text-secondary">
            {resumeLabel}
          </p>
          <Button variant="secondary" onClick={onResume} className="w-full">
            Resume interview
          </Button>
        </Card>
      </div>
    </div>
  );
}

// =====================================================================
// History panel
// =====================================================================

function HistoryPanel({ onOpen }: { onOpen: (id: number) => void }) {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<MockInterviewStatus | ''>('');
  const [rows, setRows] = useState<MockInterviewSummaryResponse[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    mockInterviewApi
      .history({ page, size: HISTORY_PAGE_SIZE, ...(status ? { status } : {}) })
      .then(({ data }) => {
        if (cancelled) return;
        const payload = data.data;
        setRows(payload.content);
        setTotalPages(payload.totalPages);
        setTotalElements(payload.totalElements);
      })
      .catch((err) => {
        if (!cancelled) setError(getErrorMessage(err));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page, status]);

  if (error) return <ErrorState message={error} onRetry={() => setPage((p) => p)} />;

  return (
    <Card
      title="Interview history"
      subtitle="Every session you have started, newest first."
      icon={<History size={16} />}
      action={
        <Select
          value={status}
          onChange={(e) => {
            setPage(0);
            setStatus(e.target.value as MockInterviewStatus | '');
          }}
          className="w-44"
          options={[
            { label: 'All statuses', value: '' },
            { label: 'In progress', value: 'IN_PROGRESS' },
            { label: 'Completed', value: 'COMPLETED' },
            { label: 'Abandoned', value: 'ABANDONED' },
          ]}
        />
      }
    >
      {loading ? (
        <div className="space-y-2">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-14 w-full" />
          ))}
        </div>
      ) : rows.length === 0 ? (
        <EmptyState
          icon={<Mic size={20} />}
          title="No interviews yet"
          description="Start your first mock interview and it will appear here."
        />
      ) : (
        <>
          <div className="space-y-2">
            {rows.map((r) => (
              <button
                key={r.id}
                type="button"
                onClick={() => onOpen(r.id)}
                className="flex w-full flex-wrap items-center justify-between gap-2 rounded-[10px] border border-neutral-200 px-3.5 py-2.5 text-left transition-all hover:border-neutral-300 hover:bg-neutral-50/60"
              >
                <span className="min-w-0">
                  <span className="flex flex-wrap items-center gap-1.5">
                    <span className="text-[13.5px] font-medium text-neutral-900">
                      {r.interviewTypeLabel}
                    </span>
                    <Badge variant={STATUS_VARIANT[r.status]} size="sm">
                      {r.status.replace('_', ' ')}
                    </Badge>
                    {difficultyBadge(r.difficulty)}
                  </span>
                  <span className="mt-0.5 block text-[12.5px] text-text-secondary">
                    {formatDate(r.startedAt)} &middot; {r.answeredCount}/{r.questionCount} answered
                    {r.durationSeconds != null && <> &middot; {formatClock(r.durationSeconds)}</>}
                  </span>
                </span>
                <ChevronRight size={15} className="text-neutral-400" />
              </button>
            ))}
          </div>
          <Pagination
            page={page}
            totalPages={totalPages}
            totalElements={totalElements}
            pageSize={HISTORY_PAGE_SIZE}
            onPageChange={setPage}
          />
        </>
      )}
    </Card>
  );
}

// =====================================================================
// Page
// =====================================================================

type View = 'setup' | 'interview' | 'results';

export default function MockInterviewPage() {
  const [tab, setTab] = useState<'start' | 'history'>('start');
  const [view, setView] = useState<View>('setup');
  const [options, setOptions] = useState<MockInterviewOptionsResponse | null>(null);
  const [active, setActive] = useState<MockInterviewSessionResponse | null>(null);
  const [session, setSession] = useState<MockInterviewSessionResponse | null>(null);
  const [result, setResult] = useState<MockInterviewResultResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const loadSetup = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [opts, act] = await Promise.all([
        mockInterviewApi.options(),
        mockInterviewApi.active(),
      ]);
      setOptions(opts.data.data);
      const current = act.data.data ?? null;
      setActive(current);
      if (current) {
        setSession(current);
        setView('interview');
      } else {
        setView('setup');
      }
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadSetup();
  }, [loadSetup]);

  const openSession = useCallback(async (id: number) => {
    setLoading(true);
    try {
      const { data } = await mockInterviewApi.session(id);
      setSession(data.data);
      if (data.data.status === 'COMPLETED') {
        const res = await mockInterviewApi.results(id);
        setResult(res.data.data);
        setView('results');
      } else if (data.data.status === 'ABANDONED') {
        setResult(null);
        setView('setup');
        notify.info('That interview was abandoned. Its answers are closed.');
      } else {
        setActive(data.data);
        setView('interview');
      }
    } catch (err) {
      notify.error(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);

  const onStarted = (started: MockInterviewSessionResponse) => {
    setActive(started);
    setSession(started);
    setResult(null);
    setView('interview');
    notify.success('Interview started. Your answers save automatically.');
  };

  const onCompleted = (sessionId: number) => {
    setActive(null);
    notify.success('Interview completed. Reference guidance is now available.');
    void openSession(sessionId);
  };

  const backToSetup = () => {
    setSession(null);
    setResult(null);
    setTab('start');
    void loadSetup();
  };

  if (loading && !options) {
    return (
      <PageContainer>
        <Skeleton className="h-8 w-64" />
        <div className="mt-4 space-y-3">
          <Skeleton className="h-40 w-full" />
          <Skeleton className="h-40 w-full" />
        </div>
      </PageContainer>
    );
  }

  if (error) {
    return (
      <PageContainer>
        <ErrorState message={error} onRetry={() => void loadSetup()} />
      </PageContainer>
    );
  }

  const resumeLabel = active
    ? `You have an interview in progress (${active.questionCount} questions, started ${formatDate(
        active.startedAt
      )}). Only one interview can run at a time.`
    : 'You have no interview in progress right now.';

  return (
    <PageContainer>
      <PageHeader
        title="Mock Interview"
        description="Practise with real questions from your preparation bank. Questions are drawn fresh, your answers save automatically, and you get honest coverage feedback with reference guidance after the interview."
      />

      {view === 'interview' && session && (
        <>
          <div className="mb-4">
            <Button variant="ghost" size="sm" onClick={() => void loadSetup()}>
              <ChevronLeft size={15} /> Back
            </Button>
          </div>
          <InterviewScreen
            session={session}
            maxAnswerLength={options?.maxAnswerLength ?? 5000}
            onCompleted={onCompleted}
          />
        </>
      )}

      {view === 'results' && result && session && (
        <>
          <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
            <Button variant="ghost" size="sm" onClick={backToSetup}>
              <ChevronLeft size={15} /> Back
            </Button>
            <Badge variant="success">
              <ThumbsUp size={12} /> Completed {formatDate(result.completedAt)}
            </Badge>
          </div>
          <ResultScreen result={result} session={session} onNewInterview={backToSetup} />
        </>
      )}

      {view === 'setup' && options && (
        <>
          <Tabs
            tabs={[
              { key: 'start', label: 'Start an interview' },
              { key: 'history', label: 'History' },
            ]}
            active={tab}
            onChange={setTab}
          />
          {tab === 'start' ? (
            <SetupScreen
              options={options}
              resumeLabel={resumeLabel}
              onStarted={onStarted}
              onResume={() => {
                if (active) onStarted(active);
              }}
            />
          ) : (
            <HistoryPanel onOpen={openSession} />
          )}
        </>
      )}

    </PageContainer>
  );
}
