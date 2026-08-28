import api from './axios';

// Auth
export const authApi = {
  login: (email: string, password: string) =>
    api.post('/auth/login', { email, password }),
  register: (data: {
    name: string;
    registerNumber: string;
    email: string;
    departmentId: number;
    batch?: string;
    password: string;
  }) => api.post('/auth/register', data),
};

// Public
export const publicApi = {
  departments: () => api.get('/public/departments'),
};

// Users
export const userApi = {
  search: (params: { search?: string; page?: number; size?: number }) =>
    api.get('/users', { params }),
  getAll: (params?: { search?: string; page?: number; size?: number }) =>
    api.get('/users', { params }),
  getById: (id: number) => api.get(`/users/${id}`),
  create: (data: {
    name: string;
    email: string;
    password: string;
    role: string;
    departmentId?: number;
  }) => api.post('/users', data),
  assignPc: (userId: number, departmentId: number) =>
    api.put(`/users/${userId}/assign-pc`, { departmentId }),
  promotePr: (userId: number) => api.put(`/users/${userId}/promote-pr`),
  demoteStudent: (userId: number) => api.put(`/users/${userId}/demote-student`),
  getStats: () => api.get('/users/stats'),
};

// Departments
export const departmentApi = {
  getAll: () => api.get('/departments'),
  getActive: () => api.get('/departments/active'),
  getById: (id: number) => api.get(`/departments/${id}`),
  create: (data: { name: string }) => api.post('/departments', data),
  update: (id: number, data: { name: string }) =>
    api.put(`/departments/${id}`, data),
  delete: (id: number) => api.delete(`/departments/${id}`),
  updatePrLimit: (id: number, maxPrs: number) =>
    api.put(`/departments/${id}/pr-config`, { maxPrs }),
};

// Students
export const studentApi = {
  search: (params: {
    search?: string;
    departmentId?: number;
    page?: number;
    size?: number;
  }) => api.get('/students', { params }),
  getAll: (params?: {
    search?: string;
    departmentId?: number;
    page?: number;
    size?: number;
  }) => api.get('/students', { params }),
  getById: (id: number) => api.get(`/students/${id}`),
  getMyProfile: () => api.get('/students/me'),
  create: (data: {
    registerNumber: string;
    userId?: number;
    departmentId: number;
    batch?: string;
    section?: string;
  }) => api.post('/students', data),
  updateProfile: (
    id: number,
    data: {
      phone?: string;
      dateOfBirth?: string;
      batch?: string;
      section?: string;
      placementInterested?: boolean;
      githubUrl?: string;
      linkedinUrl?: string;
      portfolioUrl?: string;
      resumeUrl?: string;
    }
  ) => api.put(`/students/${id}`, data),
  updateAcademic: (
    id: number,
    data: {
      tenthPercentage?: number;
      twelfthPercentage?: number;
      diplomaPercentage?: number;
      cgpa?: number;
      activeBacklogs?: number;
      historyOfBacklogs?: number;
    }
  ) => api.put(`/students/${id}/academic`, data),
  updateProfessional: (
    id: number,
    data: {
      skills?: string;
      certifications?: string;
      projects?: string;
    }
  ) => api.put(`/students/${id}/professional`, data),
};

// Companies
export const companyApi = {
  search: (params: { search?: string; page?: number; size?: number }) =>
    api.get('/companies', { params }),
  getAll: (params?: { search?: string; page?: number; size?: number }) =>
    api.get('/companies', { params }),
  getById: (id: number) => api.get(`/companies/${id}`),
  create: (data: {
    name: string;
    description?: string;
    companyType?: string;
    website?: string;
  }) => api.post('/companies', data),
  update: (
    id: number,
    data: {
      name?: string;
      description?: string;
      companyType?: string;
      website?: string;
    }
  ) => api.put(`/companies/${id}`, data),
  delete: (id: number) => api.delete(`/companies/${id}`),
};

// Placement Drives
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
  create: (data: {
    companyId: number;
    jobRole: string;
    packageLpa?: number;
    driveDate?: string;
    registrationDeadline?: string;
    location?: string;
    jobDescription?: string;
  }) => api.post('/placement-drives', data),
  open: (id: number) =>
    api.put(`/placement-drives/${id}/status`, null, {
      params: { status: 'OPEN' },
    }),
  close: (id: number) =>
    api.put(`/placement-drives/${id}/status`, null, {
      params: { status: 'CLOSED' },
    }),
  cancel: (id: number) =>
    api.put(`/placement-drives/${id}/status`, null, {
      params: { status: 'CANCELLED' },
    }),
  setEligibility: (
    id: number,
    data: {
      minCgpa?: number;
      maxActiveBacklogs?: number;
      minTenthPct?: number;
      minTwelfthPct?: number;
      minDiplomaPct?: number;
      allowedDepartmentIds?: number[];
    }
  ) => api.post(`/placement-drives/${id}/eligibility`, data),
  checkEligibility: (driveId: number, studentId: number) =>
    api.get(`/placement-drives/${driveId}/eligibility/${studentId}`),
};

// Messages
export const messageApi = {
  send: (data: {
    title: string;
    content: string;
    messageType?: string;
    recipientIds: number[];
  }) => api.post('/messages', data),
  getAll: (params?: { page?: number; size?: number }) =>
    api.get('/messages', { params }),
  getSent: (params?: { page?: number; size?: number }) =>
    api.get('/messages/sent', { params }),
  getReceived: (params?: { page?: number; size?: number }) =>
    api.get('/messages/received', { params }),
  markAsRead: (messageId: number) => api.post(`/messages/${messageId}/read`),
  upvote: (messageId: number) =>
    api.post(`/messages/${messageId}/analytics/upvote`),
  downvote: (messageId: number) =>
    api.post(`/messages/${messageId}/analytics/downvote`),
  getAnalytics: (messageId: number, type: string) =>
    api.get(`/messages/${messageId}/analytics/${type}`),
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
    api.put(`/contact-requests/${id}/status`, null, {
      params: { status },
    }),
};

// Student Interviews
export const studentInterviewApi = {
  getByDrive: (driveId: number, params?: { page?: number; size?: number }) =>
    api.get(`/placement-drives/${driveId}/interviews`, { params }),
  getByStudent: (
    studentId: number,
    params?: { page?: number; size?: number }
  ) => api.get(`/students/${studentId}/interviews`, { params }),
  create: (
    driveId: number,
    data: {
      studentProfileId: number;
      roundName: string;
      status: string;
      attended?: boolean;
      remarks?: string;
      interviewDate?: string;
    }
  ) => api.post(`/placement-drives/${driveId}/interviews`, data),
  update: (
    driveId: number,
    interviewId: number,
    data: {
      roundName?: string;
      status?: string;
      attended?: boolean;
      remarks?: string;
      interviewDate?: string;
    }
  ) =>
    api.put(
      `/placement-drives/${driveId}/interviews/${interviewId}`,
      data
    ),
};

// Reports
export const reportApi = {
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
