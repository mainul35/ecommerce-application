import axios, { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios';
import type { ApiError } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

const api: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// --- Access-token refresh --------------------------------------------------
// Access tokens are short-lived; on a 401 we transparently exchange the stored
// refresh token for a fresh access token and retry the original request once.
// The refresh endpoint is chosen by surface so admin and storefront refresh
// against their own audience-scoped tokens.

const isAdminSurface = () => window.location.pathname.startsWith('/admin');
const refreshPath = () => (isAdminSurface() ? '/admin/auth/refresh' : '/auth/refresh');

// Single-flight: many parallel 401s share ONE refresh round-trip.
let refreshInFlight: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = localStorage.getItem('refreshToken');
  if (!refreshToken) return null;
  try {
    // Bare axios (no interceptors) so a failing refresh can't recurse.
    const response = await axios.post(`${API_BASE_URL}${refreshPath()}`, { refreshToken });
    const newToken: string = response.data.token;
    const newRefresh: string | undefined = response.data.refreshToken;
    localStorage.setItem('token', newToken);
    if (newRefresh) localStorage.setItem('refreshToken', newRefresh);
    return newToken;
  } catch {
    return null;
  }
}

function clearSessionAndRedirect() {
  localStorage.removeItem('token');
  localStorage.removeItem('refreshToken');
  // Keep admins on the admin surface - bouncing them to the storefront login
  // from /admin/* pages is disorienting.
  window.location.href = isAdminSurface() ? '/admin/login' : '/login';
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiError>) => {
    const original = error.config as
      | (InternalAxiosRequestConfig & { _retried?: boolean })
      | undefined;
    const requestUrl = original?.url ?? '';
    // 401 on a login/register/refresh call means "bad credentials/expired refresh" -
    // let the caller handle it instead of hijacking the page or looping.
    const isAuthAttempt = requestUrl.includes('/auth/');

    if (error.response?.status === 401 && !isAuthAttempt && original && !original._retried) {
      original._retried = true;
      if (!refreshInFlight) {
        refreshInFlight = refreshAccessToken().finally(() => {
          refreshInFlight = null;
        });
      }
      const newToken = await refreshInFlight;
      if (newToken) {
        original.headers = original.headers ?? {};
        original.headers.Authorization = `Bearer ${newToken}`;
        return api(original);
      }
      // Refresh failed/absent -> the session is truly over.
      clearSessionAndRedirect();
    }

    const errorMessage =
      error.response?.data?.message || error.message || 'An unexpected error occurred';

    return Promise.reject(new Error(errorMessage));
  }
);

export default api;
