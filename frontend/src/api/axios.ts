import axios from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      const status = error.response.status;
      if (status === 401) {
        const url: string = error.config?.url || '';
        const isAuthEndpoint = url.includes('/auth/login') || url.includes('/auth/register');
        const hasToken = !!localStorage.getItem('token');

        if (!isAuthEndpoint) {
          if (hasToken) {
            localStorage.removeItem('token');
            localStorage.removeItem('user');
            window.location.href = '/login';
          } else {
            const devMode = import.meta.env.VITE_DEV_MODE === 'true';
            const previewing = devMode && !!localStorage.getItem('dev-preview-role');
            if (previewing) {
              localStorage.removeItem('dev-preview-role');
              window.location.href = '/login';
            }
          }
        }
      }
    }
    return Promise.reject(error);
  }
);

export function getErrorMessage(error: any): string {
  if (!error.response) {
    return 'Unable to connect to the server. Please check your connection.';
  }
  const data = error.response.data;
  if (data?.message) return data.message;
  if (data?.error) return data.error;
  switch (error.response.status) {
    case 400: return 'Invalid request. Please check your input.';
    case 401: return 'Your session has expired. Please log in again.';
    case 403: return "You don't have permission to perform this action.";
    case 404: return 'The requested resource was not found.';
    case 409: return data?.message || 'A conflict occurred. Please try again.';
    case 500: return 'Something went wrong on the server. Please try again.';
    default: return 'An unexpected error occurred.';
  }
}

export default api;
