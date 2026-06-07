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

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    // 401 on a login/register call means "wrong credentials" - let the form
    // show the error instead of hijacking the page. Only 401s on protected
    // resources mean "session expired/invalid" and warrant a redirect.
    const requestUrl = error.config?.url ?? '';
    const isAuthAttempt = requestUrl.includes('/auth/');

    if (error.response?.status === 401 && !isAuthAttempt) {
      localStorage.removeItem('token');
      // Keep admins on the admin surface - bouncing them to the storefront
      // login from /admin/* pages is disorienting.
      window.location.href = window.location.pathname.startsWith('/admin')
        ? '/admin/login'
        : '/login';
    }

    const errorMessage =
      error.response?.data?.message || error.message || 'An unexpected error occurred';

    return Promise.reject(new Error(errorMessage));
  }
);

export default api;
