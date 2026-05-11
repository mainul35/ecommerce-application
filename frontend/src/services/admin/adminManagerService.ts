import api from '../api';
import type { User } from '../../types';

export interface ManagerCreateRequest {
  email: string;
  firstName: string;
  lastName: string;
  password: string;
}

const BASE = '/admin/managers';

export const adminManagerService = {
  async list(): Promise<User[]> {
    const response = await api.get<User[]>(BASE);
    return response.data;
  },

  async create(payload: ManagerCreateRequest): Promise<User> {
    const response = await api.post<User>(BASE, payload);
    return response.data;
  },

  async setActive(managerId: string, active: boolean): Promise<User> {
    const response = await api.patch<User>(`${BASE}/${managerId}/active`, { active });
    return response.data;
  },
};
