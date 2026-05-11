import api from '../api';
import type { User } from '../../types';

export interface AdminProfileUpdateRequest {
  email: string;
  firstName: string;
  lastName: string;
  currentPassword?: string;
  newPassword?: string;
}

const BASE = '/admin/me';

export const adminMeService = {
  async get(): Promise<User> {
    const response = await api.get<User>(BASE);
    return response.data;
  },

  async update(payload: AdminProfileUpdateRequest): Promise<User> {
    // Strip empty optional fields so backend's @Size(min=6) doesn't fire on empty strings
    const body: AdminProfileUpdateRequest = {
      email: payload.email,
      firstName: payload.firstName,
      lastName: payload.lastName,
    };
    if (payload.newPassword && payload.newPassword.length > 0) {
      body.currentPassword = payload.currentPassword;
      body.newPassword = payload.newPassword;
    }
    const response = await api.put<User>(BASE, body);
    return response.data;
  },
};
