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
  createdAt: string;
  totalRecipients: number;
  deliveredCount: number;
  readCount: number;
  upvoteCount: number;
  downvoteCount: number;
}

export interface ContactRequest {
  id: number;
  studentProfileId: number;
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
}

export interface UserStats {
  totalStudents: number;
  activeStudents: number;
  totalPcs: number;
  totalPrs: number;
}
