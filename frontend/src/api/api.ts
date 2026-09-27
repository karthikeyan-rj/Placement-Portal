import api from './axios';
import { invalidate, cacheGet, cacheSet } from './cache';
import type {
  ApiResponse,
  Department,
  DepartmentAggregate,
  StudentProfile,
  Company,
  CompanyOption,
  User,
  MeResponse,
  ProfileResponse,
  StaffProfile,
  UpdateStaffProfileRequest,
  ImportPreviewResponse,
  ImportConfirmResponse,
  ImportRowPreview,
  PaginatedResponse,
  MockInterviewOptionsResponse,
  StartMockInterviewRequest,
  MockInterviewSessionResponse,
  MockInterviewSummaryResponse,
  MockInterviewResultResponse,
  MockInterviewQuestionView,
  MockSelfRating,
} from '../types';

// Cache stable reference data (departments, companies, current user profile) in
// memory, scoped by role+user, TTL 30s, invalidated on the relevant mutations.
const REF_TTL = 30_000;

// Return an AxiosResponse-shaped object so callers using `.data` are unchanged,
// but serve from cache when a fresh entry exists.
async function refGet<T>(url: string): Promise<{ data: T }> {
  const cached = cacheGet<T>(url, {}, { ttl: REF_TTL });
  if (cached !== undefined) return { data: cached };
  const r = await api.get<T>(url);
  cacheSet<T>(url, {}, r.data, REF_TTL);
  return { data: r.data };
}

async function refList<T>(url: string, params: Record<string, unknown> = {}): Promise<{ data: T }> {
  const keyParams = { size: 1000, ...params };
  const cached = cacheGet<T>(url, keyParams, { ttl: REF_TTL });
  if (cached !== undefined) return { data: cached };
  const r = await api.get<T>(url, { params });
  cacheSet<T>(url, keyParams, r.data, REF_TTL);
  return { data: r.data };
}

// Auth
export const authApi = {
  login: (email: string, password: string) =>
    api.post('/auth/login', { email, password }),
  register: (data: {
    registerNumber: string;
    email: string;
    accessCode: string;
    password: string;
  }) => api.post('/auth/register', data),
  requestCode: (data: { email: string; registerNumber: string }) =>
    api.post<{ message: string; maskedEmail: string }>('/auth/registration/request-code', data),
  verifyCode: (data: { email: string; registerNumber: string; code: string }) =>
    api.post<{ message: string; registrationToken: string }>('/auth/registration/verify-code', data),
  completeRegistration: (data: { registrationToken: string; password: string; confirmPassword: string }) =>
    api.post<{ message: string; userId: number; name: string; email: string; role: string }>(
      '/auth/registration/complete',
      data
    ),
  changePassword: (currentPassword: string, newPassword: string) =>
    api.post('/auth/change-password', { currentPassword, newPassword }),
  me: () => api.get<MeResponse>('/auth/me'),
};

// Profile (unified, role-aware, self-derived)
export const profileApi = {
  getMyProfile: () => refGet<ApiResponse<ProfileResponse>>('/profile/me'),
  /**
   * Saves the caller's own staff (PC / PO) profile. There is no userId: the
   * server always edits the authenticated principal's own record. The cached
   * profile read is invalidated so the saved values show up immediately.
   */
  updateMyStaffProfile: async (payload: UpdateStaffProfileRequest) => {
    const r = await api.put<ApiResponse<StaffProfile>>('/profile/me/staff', payload);
    invalidate('/profile');
    return r;
  },
};

// Public
export const publicApi = {
  departments: () => api.get('/public/departments'),
};

// Users
export const userApi = {
  search: (params: {
    search?: string;
    role?: User['role'];
    departmentId?: number;
    page?: number;
    size?: number;
  }) => api.get('/users', { params }),
  getAll: (params?: {
    search?: string;
    role?: User['role'];
    departmentId?: number;
    page?: number;
    size?: number;
  }) => api.get('/users', { params }),
  getById: (id: number) => api.get(`/users/${id}`),
  create: async (data: {
    name: string;
    email: string;
    password: string;
    role: string;
    departmentId?: number;
  }) => {
    const r = await api.post('/users', data);
    invalidate('/users');
    return r;
  },
  assignPc: async (userId: number, departmentId: number) => {
    const r = await api.put(`/users/${userId}/assign-pc`, { departmentId });
    invalidate('/users', '/students', '/departments', '/profile');
    return r;
  },
  promotePr: async (userId: number) => {
    const r = await api.put(`/users/${userId}/promote-pr`);
    invalidate('/users', '/students', '/profile');
    return r;
  },
  demoteStudent: async (userId: number) => {
    const r = await api.put(`/users/${userId}/demote-student`);
    invalidate('/users', '/students', '/profile');
    return r;
  },
  getStats: () => api.get('/users/stats'),
};

// Departments
export const departmentApi = {
  getAll: () => refList<ApiResponse<Department[]>>('/departments'),
  getActive: () => refList<ApiResponse<Department[]>>('/departments/active'),
  aggregates: () => refGet<ApiResponse<DepartmentAggregate[]>>('/departments/aggregates'),
  getById: (id: number) => refGet<ApiResponse<Department>>(`/departments/${id}`),
  create: async (data: { name: string }) => {
    const r = await api.post('/departments', data);
    invalidate('/departments');
    return r;
  },
  update: async (id: number, data: { name: string }) => {
    const r = await api.put(`/departments/${id}`, data);
    invalidate('/departments');
    return r;
  },
  delete: async (id: number) => {
    const r = await api.delete(`/departments/${id}`);
    invalidate('/departments');
    return r;
  },
  updatePrLimit: async (id: number, maxPrs: number) => {
    const r = await api.put(`/departments/${id}/pr-config`, { maxPrs });
    invalidate('/departments');
    return r;
  },
};

// Students
export const studentApi = {
  search: (params: {
    search?: string;
    departmentId?: number;
    role?: User['role'];
    placementInterested?: boolean;
    placementStatus?: string;
    page?: number;
    size?: number;
  }) => api.get('/students', { params }),
  getAll: (params?: {
    search?: string;
    departmentId?: number;
    role?: User['role'];
    placementInterested?: boolean;
    placementStatus?: string;
    page?: number;
    size?: number;
  }) => api.get('/students', { params }),
  getById: (id: number) => api.get(`/students/${id}`),
  getMyProfile: () => refGet<ApiResponse<StudentProfile>>('/students/me'),
  create: async (data: {
    registerNumber: string;
    userId?: number;
    departmentId: number;
    batch?: string;
    section?: string;
  }) => {
    const r = await api.post('/students', data);
    invalidate('/students', '/profile');
    return r;
  },
  updateProfile: async (
    id: number,
    data: {
      phone?: string;
      dateOfBirth?: string;
      batch?: string;
      section?: string;
      placementInterested?: boolean;
    }
  ) => {
    const r = await api.put(`/students/${id}`, data);
    invalidate('/students', '/profile');
    return r;
  },
  updateAcademic: async (
    id: number,
    data: {
      tenthPercentage?: number;
      twelfthPercentage?: number;
      diplomaPercentage?: number;
      cgpa?: number;
      activeBacklogs?: number;
      historyOfBacklogs?: number;
    }
  ) => {
    const r = await api.put(`/students/${id}/academic`, data);
    invalidate('/students', '/profile');
    return r;
  },
  updateProfessional: async (
    id: number,
    data: {
      skills?: string;
      certifications?: string;
      projects?: string;
      resumeUrl?: string;
      githubUrl?: string;
      linkedinUrl?: string;
      portfolioUrl?: string;
    }
  ) => {
    const r = await api.put(`/students/${id}/professional`, data);
    invalidate('/students', '/profile');
    return r;
  },
};

// Companies
export const companyApi = {
  search: (params: { search?: string; page?: number; size?: number }) =>
    api.get('/companies', { params }),
  getAll: (params?: { search?: string; page?: number; size?: number }) =>
    api.get('/companies', { params }),
  options: () => refGet<ApiResponse<CompanyOption[]>>('/companies/options'),
  getById: (id: number) => refGet<ApiResponse<Company>>(`/companies/${id}`),
  create: async (data: {
    name: string;
    description?: string;
    companyType?: string;
    website?: string;
  }) => {
    const r = await api.post('/companies', data);
    invalidate('/companies');
    return r;
  },
  update: async (
    id: number,
    data: {
      name?: string;
      description?: string;
      companyType?: string;
      website?: string;
    }
  ) => {
    const r = await api.put(`/companies/${id}`, data);
    invalidate('/companies');
    return r;
  },
  delete: async (id: number) => {
    const r = await api.delete(`/companies/${id}`);
    invalidate('/companies');
    return r;
  },
};

// Placement Drives
const setStatus = async (id: number, status: string) => {
  const r = await api.put(`/placement-drives/${id}/status`, null, { params: { status } });
  invalidate('/placement-drives');
  return r;
};

export const placementDriveApi = {
  search: (params: {
    status?: string;
    search?: string;
    page?: number;
    size?: number;
  }) => api.get('/placement-drives', { params }),
  getAll: (params?: {
    search?: string;
    page?: number;
    size?: number;
  }) => api.get('/placement-drives', { params }),
  getById: (id: number) => api.get(`/placement-drives/${id}`),
  getEmailStatus: (id: number) => api.get(`/placement-drives/${id}/email-status`),
  getEmailRecipientCount: (id: number) => api.get(`/placement-drives/${id}/email-recipient-count`),
  create: async (data: {
    companyId: number;
    jobRole: string;
    packageLpa?: number;
    driveDate?: string;
    registrationDeadline?: string;
    location?: string;
    jobDescription?: string;
    status?: string;
  }) => {
    const r = await api.post('/placement-drives', data);
    invalidate('/placement-drives');
    return r;
  },
  setStatus: async (id: number, status: string) => {
    const r = await api.put(`/placement-drives/${id}/status`, null, { params: { status } });
    invalidate('/placement-drives');
    return r;
  },
  open: (id: number) => setStatus(id, 'REGISTRATION_OPEN'),
  close: (id: number) => setStatus(id, 'REGISTRATION_CLOSED'),
  cancel: (id: number) => setStatus(id, 'CANCELLED'),
  setEligibility: async (
    id: number,
    data: {
      minCgpa?: number;
      maxActiveBacklogs?: number;
      minTenthPct?: number;
      minTwelfthPct?: number;
      minDiplomaPct?: number;
      allowedDepartmentIds?: number[];
    }
  ) => {
    const r = await api.post(`/placement-drives/${id}/eligibility`, data);
    invalidate('/placement-drives');
    return r;
  },
  checkEligibility: (driveId: number, studentId: number) =>
    api.get(`/placement-drives/${driveId}/eligibility/${studentId}`),
};

// Messages
export const messageApi = {
  send: async (data: {
    title: string;
    content: string;
    messageType?: string;
    recipientIds?: number[];
    departmentId?: number;
    targetRole?: string;
    everyone?: boolean;
    importance?: string;
  }) => {
    const r = await api.post('/messages', data);
    invalidate('/messages');
    return r;
  },
  countRecipients: (params: {
    everyone?: boolean;
    departmentId?: number;
    targetRole?: string;
    recipientIds?: number[];
  }) => api.get('/messages/recipients/count', { params }),
  getEmailStatus: (messageId: number) =>
    api.get(`/messages/${messageId}/email-status`),
  getAll: (params?: { page?: number; size?: number }) =>
    api.get('/messages', { params }),
  getSent: (params?: { page?: number; size?: number }) =>
    api.get('/messages/sent', { params }),
  getReceived: (params?: { page?: number; size?: number }) =>
    api.get('/messages/received', { params }),
  markAsRead: async (messageId: number) => {
    const r = await api.post(`/messages/${messageId}/read`);
    invalidate('/messages');
    return r;
  },
  getUnreadCount: () => api.get('/messages/unread-count'),
  react: async (messageId: number, reaction: 'UPVOTE' | 'DOWNVOTE') => {
    const r = await api.post(`/messages/${messageId}/reaction`, { reaction });
    invalidate('/messages');
    return r;
  },
  getAnalytics: (messageId: number, type: string) =>
    api.get(`/messages/${messageId}/analytics/${type}`),
  getMyReaction: (messageId: number) =>
    api.get(`/messages/${messageId}/my-reaction`),
};

// Clarifications (on PO/PC-sent messages)
export const clarificationApi = {
  create: (messageId: number, content: string) =>
    api.post(`/messages/${messageId}/clarifications`, { content }),
  listForMessage: (messageId: number, params?: { page?: number; size?: number }) =>
    api.get(`/messages/${messageId}/clarifications`, { params }),
  counts: (messageId: number) => api.get(`/messages/${messageId}/clarification-counts`),
  getThread: (threadId: number, params?: { page?: number; size?: number }) =>
    api.get(`/clarifications/${threadId}`, { params }),
  reply: (threadId: number, content: string) =>
    api.post(`/clarifications/${threadId}/replies`, { content }),
  incoming: (params?: { page?: number; size?: number }) =>
    api.get('/clarifications/incoming', { params }),
};

// Contact Requests
export const contactRequestApi = {
  create: (data: {
    targetUserId: number;
    subject: string;
    message: string;
  }) => api.post('/contact-requests', data),
  getIncoming: (params?: { page?: number; size?: number }) =>
    api.get('/contact-requests/incoming', { params }),
  getMine: (params?: { page?: number; size?: number }) =>
    api.get('/contact-requests/mine', { params }),
  updateStatus: (id: number, status: string) =>
    api.put(`/contact-requests/${id}/status`, { status }),
  getTargets: () => api.get('/contact-requests/targets'),
  getCounts: (view: 'incoming' | 'mine') =>
    api.get('/contact-requests/counts', { params: { view } }),
};

// Student Interviews
export interface CreateInterviewBody {
  studentProfileId: number;
  placementDriveId: number;
  roundName: string;
  status?: string;
  attended?: boolean;
  remarks?: string;
  interviewDate?: string;
}

export const studentInterviewApi = {
  getMine: () => api.get('/interviews'),
  getByDrive: (driveId: number) => api.get(`/interviews/drive/${driveId}`),
  getByStudent: (studentProfileId: number) =>
    api.get(`/interviews/student/${studentProfileId}`),
  create: async (data: CreateInterviewBody) => {
    const r = await api.post('/interviews', data);
    invalidate('/interviews', '/students');
    return r;
  },
};

// Reports
export const reportApi = {
  summary: (departmentId?: number) =>
    api.get('/reports/summary', {
      params: departmentId ? { departmentId } : {},
    }),
  students: (departmentId?: number) =>
    api.get('/reports/students/csv', {
      params: departmentId ? { departmentId } : {},
      responseType: 'blob',
    }),
  placements: () =>
    api.get('/reports/placement/csv', { responseType: 'blob' }),
  messages: (messageId: number) =>
    api.get(`/reports/messages/${messageId}/csv`, {
      responseType: 'blob',
    }),
};

// Audit Logs
export const auditLogApi = {
  getAll: (params?: { page?: number; size?: number }) =>
    api.get('/audit-logs', { params }),
};

// Resume Analyzer (student-facing preparation tool)
export const resumeAnalyzerApi = {
  analyze: (file: File) => {
    const data = new FormData();
    data.append('file', file);
    return api.post('/resume-analyzer/analyze', data, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  history: () => api.get('/resume-analyzer/me/history'),
  detail: (analysisId: number) =>
    api.get(`/resume-analyzer/me/history/${analysisId}`),
};

// PO Student CSV Import (Phase 7S.1)
export const studentImportApi = {
  template: () =>
    api.get<Blob>('/students/import/template', { responseType: 'blob' }),
  preview: (file: File) => {
    const data = new FormData();
    data.append('file', file);
    return api.post<ImportPreviewResponse>('/students/import/preview', data, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  confirm: (rows: ImportRowPreview[]) =>
    api.post<ImportConfirmResponse>('/students/import/confirm', {
      rows: rows.map((r) => ({
        rowNumber: r.rowNumber,
        email: r.email,
        name: r.name,
        registerNumber: r.registerNumber,
        department: r.department,
      })),
    }),
};

// Mock Interview (Phase 7N.2B)
// Reuses the existing preparation bank; there is no separate question corpus.
// Options are never cached here, because availability changes with the bank and
// the setup screen must show real counts.
export const mockInterviewApi = {
  options: () => api.get<ApiResponse<MockInterviewOptionsResponse>>('/mock-interviews/options'),

  start: (payload: StartMockInterviewRequest) =>
    api.post<ApiResponse<MockInterviewSessionResponse>>('/mock-interviews', payload),

  /** Returns null data when there is no interview in progress (a normal state). */
  active: () => api.get<ApiResponse<MockInterviewSessionResponse>>('/mock-interviews/me/active'),

  history: (params: { page?: number; size?: number; status?: string } = {}) =>
    api.get<ApiResponse<PaginatedResponse<MockInterviewSummaryResponse>>>('/mock-interviews/me', { params }),

  session: (sessionId: number) =>
    api.get<ApiResponse<MockInterviewSessionResponse>>(`/mock-interviews/${sessionId}`),

  results: (sessionId: number) =>
    api.get<ApiResponse<MockInterviewResultResponse>>(`/mock-interviews/${sessionId}/results`),

  saveAnswer: (sessionId: number, sessionQuestionId: number, studentAnswer: string) =>
    api.put<ApiResponse<MockInterviewQuestionView>>(
      `/mock-interviews/${sessionId}/questions/${sessionQuestionId}/answer`,
      { studentAnswer }
    ),

  saveSelfRating: (sessionId: number, sessionQuestionId: number, selfRating: MockSelfRating | null) =>
    api.put<ApiResponse<MockInterviewQuestionView>>(
      `/mock-interviews/${sessionId}/questions/${sessionQuestionId}/self-rating`,
      { selfRating }
    ),

  complete: (sessionId: number) =>
    api.post<ApiResponse<MockInterviewSummaryResponse>>(`/mock-interviews/${sessionId}/complete`),

  abandon: (sessionId: number) =>
    api.post<ApiResponse<MockInterviewSummaryResponse>>(`/mock-interviews/${sessionId}/abandon`),
};