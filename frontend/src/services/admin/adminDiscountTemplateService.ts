import api from '../api';
import type { DiscountTemplate, DiscountType } from '../../types';

export interface DiscountTemplateUpsertRequest {
  name: string;
  description?: string | null;
  type: DiscountType;
  value: number;
  defaultDurationDays?: number | null;
}

const BASE = '/admin/discount-templates';

export const adminDiscountTemplateService = {
  async list(): Promise<DiscountTemplate[]> {
    const response = await api.get<DiscountTemplate[]>(BASE);
    return response.data;
  },
  async getById(id: string): Promise<DiscountTemplate> {
    const response = await api.get<DiscountTemplate>(`${BASE}/${id}`);
    return response.data;
  },
  async create(payload: DiscountTemplateUpsertRequest): Promise<DiscountTemplate> {
    const response = await api.post<DiscountTemplate>(BASE, payload);
    return response.data;
  },
  async update(id: string, payload: DiscountTemplateUpsertRequest): Promise<DiscountTemplate> {
    const response = await api.put<DiscountTemplate>(`${BASE}/${id}`, payload);
    return response.data;
  },
  async delete(id: string): Promise<void> {
    await api.delete(`${BASE}/${id}`);
  },
};
