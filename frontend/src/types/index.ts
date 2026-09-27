export interface User {
  id: number;
  name: string;
  email: string;
  role: 'PO' | 'PC' | 'PR' | 'STUDENT';
  departmentId: number | null;
  departmentName: string | null;
  active: boolean;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  type: string;
  email: string;
  name: string;
  role: string;
  departmentId: number | null;
  departmentName: string | null;
}

export interface MeResponse {
  id: number;
  name: string;
  email: string;
  role: 'PO' | 'PC' | 'PR' | 'STUDENT';
  departmentId: number | null;
  departmentName: string | null;
  active: boolean;
}

export interface ProfileResponse {
  id: number;
  name: string;
  email: string;
  role: 'PO' | 'PC' | 'PR' | 'STUDENT';
  active: boolean;
  departmentId: number | null;
  departmentName: string | null;
  /** Populated for STUDENT and PR only; always null for PC and PO. */
  studentProfile: StudentProfile | null;
  /**
   * Populated for PC and PO only; always null for STUDENT and PR. All-null when
   * the staff member has not saved anything yet.
   */
  staffProfile: StaffProfile | null;
}

/** Editable professional profile owned by PC and PO accounts. */
export interface StaffProfile {
  phone: string | null;
  designation: string | null;
  officeLocation: string | null;
  bio: string | null;
  linkedinUrl: string | null;
  expertise: string[] | null;
}

/** Self-service update payload. Every field is optional and independently editable. */
export interface UpdateStaffProfileRequest {
  phone?: string;
  designation?: string;
  officeLocation?: string;
  bio?: string;
  linkedinUrl?: string;
  expertise?: string[];
}

export interface RegisterResponse {
  userId: number;
  name: string;
  email: string;
  role: string;
  departmentId: number;
  departmentName: string;
}

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp: string;
}

export interface PaginatedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface Department {
  id: number;
  name: string;
  active: boolean;
  prLimit: number | null;
}

export interface StudentProfile {
  id: number;
  userId: number;
  userName: string;
  userEmail: string;
  registerNumber: string;
  phone: string | null;
  dateOfBirth: string | null;
  departmentId: number;
  departmentName: string;
  batch: string | null;
  section: string | null;
  tenthPercentage: number | null;
  twelfthPercentage: number | null;
  diplomaPercentage: number | null;
  cgpa: number | null;
  activeBacklogs: number | null;
  historyOfBacklogs: number | null;
  skills: string | null;
  certifications: string | null;
  projects: string | null;
  resumeUrl: string | null;
  githubUrl: string | null;
  linkedinUrl: string | null;
  portfolioUrl: string | null;
  placementInterested: boolean | null;
  placementStatus: string | null;
  interviewsAttended: number | null;
  placedCompanyId: number | null;
  placedCompanyName: string | null;
  packageLpa: number | null;
}

export interface Company {
  id: number;
  name: string;
  description: string | null;
  companyType: string | null;
  website: string | null;
  active: boolean;
}

export interface EligibilityCriteria {
  id: number;
  minCgpa: number | null;
  maxActiveBacklogs: number | null;
  minTenthPct: number | null;
  minTwelfthPct: number | null;
  minDiplomaPct: number | null;
  allowedDepartmentIds: number[] | null;
  allowedDepartmentNames: string[] | null;
}

export interface PlacementDrive {
  id: number;
  companyId: number;
  companyName: string;
  companyType: string | null;
  jobRole: string;
  packageLpa: number | null;
  driveDate: string | null;
  registrationDeadline: string | null;
  location: string | null;
  jobDescription: string | null;
  status: string;
  eligibilityCriteria: EligibilityCriteria | null;
}

export interface Message {
  id: number;
  senderName: string;
  senderRole: string;
  title: string;
  content: string;
  messageType: string | null;
  importance?: 'NORMAL' | 'HIGH' | string;
  createdAt: string;
  totalRecipients: number;
  deliveredCount: number;
  readCount: number;
  upvoteCount: number;
  downvoteCount: number;
  clarificationCount?: number;
  openClarificationCount?: number;
  answeredClarificationCount?: number;
  readByRecipient?: boolean;
  myReaction?: 'UPVOTE' | 'DOWNVOTE' | null;
}

export interface ClarificationEntry {
  id: number;
  authorId: number;
  authorName: string;
  content: string;
  createdAt: string;
}

export interface ClarificationThread {
  threadId: number;
  messageId: number;
  messageTitle: string;
  requesterId: number;
  requesterName: string;
  senderId: number;
  senderName: string;
  senderRole: string;
  status: 'OPEN' | 'ANSWERED';
  createdAt: string | null;
  updatedAt: string | null;
  entries?: ClarificationEntry[];
  totalEntries?: number;
}

export interface ClarificationCounts {
  total: number;
  open: number;
  answered: number;
}

export interface EmailStatusResponse {
  pending: number;
  /** Provider accepted the message; not proof of inbox delivery. */
  submitted: number;
  /** Confirmed by a provider delivery event. */
  delivered: number;
  delayed: number;
  bounced: number;
  complained: number;
  /** Provider suppressed the message; delivery will not be retried. */
  suppressed: number;
  failed: number;
  skippedInvalid: number;
  configError: number;
  total: number;
}

/** Aggregate notification counts for one Placement Drive; no recipient identity. */
export interface DriveEmailStatus {
  driveId: number;
  eventKey: string;
  eligibleRecipients: number;
  queued: number;
  pending: number;
  submitted: number;
  delivered: number;
  delayed: number;
  bounced: number;
  complained: number;
  /** Provider suppressed the message; delivery will not be retried. */
  suppressed: number;
  failed: number;
  skippedInvalid: number;
  configError: number;
  total: number;
}

export interface ContactRequest {
  id: number;
  studentProfileId: number;
  /** User id of the requester, so a coordinator can address them directly. */
  requesterUserId: number;
  studentName: string;
  registerNumber: string;
  departmentName: string;
  targetUserId: number;
  targetUserName: string;
  subject: string;
  message: string;
  status: string;
  createdAt: string;
  resolvedAt: string | null;
}

/** A coordinator the current user may request contact with (server-resolved scope). */
export interface ContactRequestTarget {
  id: number;
  name: string;
  role: string;
  departmentName: string | null;
}

/** Per-status totals for one contact-request view. `rejected` displays as "Declined". */
export interface ContactRequestCounts {
  pending: number;
  accepted: number;
  rejected: number;
  resolved: number;
  total: number;
}

/** Metadata-only SSE payload for CONTACT_REQUEST_* events. */
export interface ContactRequestEventInfo {
  type: string;
  requestId: number;
}

export interface StudentInterview {
  id: number;
  studentProfileId: number;
  studentName: string;
  registerNumber: string;
  placementDriveId: number;
  driveJobRole: string;
  companyName: string;
  roundName: string;
  status: string;
  attended: boolean | null;
  remarks: string | null;
  interviewDate: string | null;
  driveDate: string | null;
  driveLocation: string | null;
  packageLpa: number | null;
}

export interface UserStats {
  totalStudents: number;
  totalPcs: number;
  totalPrs: number;
  totalPOs: number;
}

export interface DepartmentAggregate {
  departmentId: number;
  departmentName: string;
  active: boolean;
  prLimit: number | null;
  studentCount: number;
  pcCount: number;
  prCount: number;
}

export interface CompanyOption {
  id: number;
  name: string;
}

export interface ReportDepartmentRow {
  departmentId: number | null;
  departmentName: string;
  studentCount: number;
  interestedCount: number;
  placedCount: number;
  placementRate: number;
}

export interface ReportBatchRow {
  batch: string;
  studentCount: number;
  interestedCount: number;
  placedCount: number;
  placementRate: number;
}

export interface ReportSummary {
  totalStudentPopulation: number;
  placementInterested: number;
  placed: number;
  notPlaced: number;
  blocked: number;
  placementRate: number;
  activeDrives: number;
  completedDrives: number;
  activeCompanies: number;
  byDepartment: ReportDepartmentRow[];
  byBatch: ReportBatchRow[];
}

export interface AuditLog {
  id: number;
  userEmail: string;
  action: string;
  entityType: string;
  entityId: number | null;
  details: string | null;
  createdAt: string;
}

export interface ResumeSectionCheck {
  name: string;
  found: boolean;
}

export interface ResumeContactChecks {
  emailPresent: boolean;
  phonePresent: boolean;
  linkedinPresent: boolean;
  githubPresent: boolean;
  portfolioPresent: boolean;
}

export interface ResumeRecommendation {
  level: 'HIGH' | 'MEDIUM' | 'LOW';
  text: string;
}

export interface ResumeAnalysis {
  id: number;
  fileName: string;
  fileSize: number | null;
  pageCount: number | null;
  readinessScore: number;
  atsCompatibility: number;
  categoryScores: {
    profileCompleteness: number;
    contentQuality: number;
    impact: number;
    formatting: number;
    professionalLinks: number;
  };
  sections: ResumeSectionCheck[];
  contactChecks: ResumeContactChecks;
  detectedSkills: string[];
  warnings: string[];
  recommendations: ResumeRecommendation[];
  createdAt: string;
}

export interface ResumeAnalysisSummary {
  id: number;
  fileName: string;
  pageCount: number | null;
  readinessScore: number;
  createdAt: string;
}

// PO Student CSV Import (Phase 7S.1)
export type ImportRowStatus =
  | 'READY'
  | 'DUPLICATE'
  | 'ALREADY_REGISTERED'
  | 'ALREADY_AUTHORIZED'
  | 'INVALID';

export interface ImportRowPreview {
  rowNumber: number;
  email: string;
  name: string;
  registerNumber: string;
  department: string;
  status: ImportRowStatus;
  error?: string | null;
}

export interface ImportPreviewResponse {
  totalRows: number;
  validRows: number;
  invalidRows: number;
  duplicateRows: number;
  alreadyRegistered: number;
  alreadyAuthorized: number;
  rows: ImportRowPreview[];
}

export interface ImportAccessCodeRow {
  rowNumber: number;
  name: string;
  email: string;
  registerNumber: string;
  department: string;
  accessCode: string;
}

export interface ImportConfirmResponse {
  imported: number;
  skipped: number;
  alreadyRegistered: number;
  alreadyAuthorized: number;
  codes: ImportAccessCodeRow[];
  errors: ImportRowPreview[];
}

  // ----- Mock Interview (Phase 7N.2B)
  // The preparation bank is descriptive only, so `objectiveScoringSupported` is
  // always false and the UI must never present an aggregate correctness score.

  export type MockInterviewType = 'TECHNICAL' | 'HR_BEHAVIORAL' | 'MIXED';
  export type MockDifficultyFilter = 'EASY' | 'MEDIUM' | 'HARD' | 'MIXED';
  export type MockInterviewStatus = 'IN_PROGRESS' | 'COMPLETED' | 'ABANDONED';
  export type MockSelfRating = 'NEED_PRACTICE' | 'PARTIALLY_CONFIDENT' | 'CONFIDENT';

  export interface MockInterviewModuleOption {
    id: number;
    code: string;
    title: string;
    questionCount: number;
  }

  export interface MockInterviewModeOption {
    code: MockInterviewType;
    label: string;
    description: string;
    modules: MockInterviewModuleOption[];
    /** Active availability per difficulty, plus `MIXED` for the unfiltered total. */
    availableByDifficulty: Record<string, number>;
  }

  export interface MockInterviewOptionsResponse {
    modes: MockInterviewModeOption[];
    allowedQuestionCounts: number[];
    maxAnswerLength: number;
    objectiveScoringSupported: boolean;
  }

  export interface StartMockInterviewRequest {
    interviewType: MockInterviewType;
    difficulty: MockDifficultyFilter;
    questionCount: number;
    moduleIds?: number[];
  }

  export interface MockInterviewQuestionView {
    sessionQuestionId: number;
    position: number;
    questionId: number;
    question: string;
    difficulty: string;
    moduleId: number;
    moduleCode: string;
    moduleTitle: string;
    topicId: number;
    topicTitle: string;
    studentAnswer: string | null;
    selfRating: MockSelfRating | null;
    answered: boolean;
    answeredAt: string | null;
    /** Null until the session is COMPLETED. */
    referenceAnswer: string | null;
  }

  export interface MockInterviewSessionResponse {
    id: number;
    interviewType: MockInterviewType;
    interviewTypeLabel: string;
    difficulty: MockDifficultyFilter;
    status: MockInterviewStatus;
    questionCount: number;
    answeredCount: number;
    startedAt: string;
    completedAt: string | null;
    durationSeconds: number | null;
    /** Server-derived clock, so a refresh keeps counting from the real start. */
    elapsedSeconds: number;
    hasObjectiveQuestions: boolean;
    questions: MockInterviewQuestionView[];
  }

  export interface MockInterviewSummaryResponse {
    id: number;
    interviewType: MockInterviewType;
    interviewTypeLabel: string;
    difficulty: MockDifficultyFilter;
    status: MockInterviewStatus;
    questionCount: number;
    answeredCount: number;
    startedAt: string;
    completedAt: string | null;
    durationSeconds: number | null;
  }

  export interface MockInterviewModuleBreakdown {
    moduleId: number;
    moduleCode: string;
    moduleTitle: string;
    questionCount: number;
    answeredCount: number;
  }

  export interface MockInterviewTopicBreakdown {
    topicId: number;
    topicCode: string;
    topicTitle: string;
    moduleCode: string;
    questionCount: number;
    answeredCount: number;
    needPracticeCount: number;
  }

  export interface MockInterviewSelfAssessmentSummary {
    confident: number;
    partiallyConfident: number;
    needPractice: number;
    unrated: number;
    ratedCount: number;
  }

  export interface MockInterviewReviewArea {
    moduleCode: string;
    topicTitle: string;
    reason: string;
    needPracticeCount: number;
    unansweredCount: number;
  }

  export interface MockInterviewResultResponse {
    id: number;
    interviewType: MockInterviewType;
    interviewTypeLabel: string;
    difficulty: MockDifficultyFilter;
    status: MockInterviewStatus;
    questionCount: number;
    answeredCount: number;
    unansweredCount: number;
    startedAt: string;
    completedAt: string | null;
    durationSeconds: number | null;
    objectiveScoringSupported: boolean;
    moduleBreakdown: MockInterviewModuleBreakdown[];
    topicBreakdown: MockInterviewTopicBreakdown[];
    selfAssessment: MockInterviewSelfAssessmentSummary;
    areasToReview: MockInterviewReviewArea[];
  }