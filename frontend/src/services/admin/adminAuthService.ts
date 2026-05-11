import api from '../api';
import type { AuthResponse, LoginRequest } from '../../types';

/**
 * Admin-only authentication client. Hits /api/admin/auth/login, which
 * rejects non-ADMIN accounts on the backend - so this is the ONLY entry
 * point for an admin to obtain a session.
 */
export const adminAuthService = {
  async login(credentials: LoginRequest): Promise<AuthResponse> {
    const response = await api.post<AuthResponse>('/admin/auth/login', credentials);
    return response.data;
  },
};
