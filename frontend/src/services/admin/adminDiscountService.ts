import api from '../api';
import type { Discount, DiscountScope, DiscountType } from '../../types';

export interface DiscountUpsertRequest {
  name: string;
  type: DiscountType;
  value: number;
  scope: DiscountScope;
  scopeTargetId?: string | null;
  startsAt?: string | null;
  endsAt?: string | null;
  isActive?: boolean;
}

const BASE = '/admin/discounts';

export const adminDiscountService = {
  async list(): Promise<Discount[]> {
    const response = await api.get<Discount[]>(BASE);
    return response.data;
  },

  /** Discounts for a specific product or category (scope-restricted). */
  async listForScope(scope: 'PRODUCT' | 'CATEGORY', targetId: string): Promise<Discount[]> {
    const response = await api.get<Discount[]>(
      `${BASE}?scope=${scope}&scopeTargetId=${encodeURIComponent(targetId)}`
    );
    return response.data;
  },

  async getById(id: string): Promise<Discount> {
    const response = await api.get<Discount>(`${BASE}/${id}`);
    return response.data;
  },

  async create(payload: DiscountUpsertRequest): Promise<Discount> {
    const response = await api.post<Discount>(BASE, payload);
    return response.data;
  },

  async update(id: string, payload: DiscountUpsertRequest): Promise<Discount> {
    const response = await api.put<Discount>(`${BASE}/${id}`, payload);
    return response.data;
  },

  async delete(id: string): Promise<void> {
    await api.delete(`${BASE}/${id}`);
  },
};
