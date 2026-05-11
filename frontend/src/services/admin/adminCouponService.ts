import api from '../api';
import type { Coupon, CouponType } from '../../types';

export interface CouponUpsertRequest {
  code: string;
  name?: string;
  type: CouponType;
  value?: number | null;
  minOrderAmount?: number | null;
  maxUses?: number | null;
  maxUsesPerUser?: number | null;
  validFrom?: string | null;
  validUntil?: string | null;
  isActive?: boolean;
}

const BASE = '/admin/coupons';

export const adminCouponService = {
  async list(): Promise<Coupon[]> {
    const response = await api.get<Coupon[]>(BASE);
    return response.data;
  },
  async getById(id: string): Promise<Coupon> {
    const response = await api.get<Coupon>(`${BASE}/${id}`);
    return response.data;
  },
  async create(payload: CouponUpsertRequest): Promise<Coupon> {
    const response = await api.post<Coupon>(BASE, payload);
    return response.data;
  },
  async update(id: string, payload: CouponUpsertRequest): Promise<Coupon> {
    const response = await api.put<Coupon>(`${BASE}/${id}`, payload);
    return response.data;
  },
  async delete(id: string): Promise<void> {
    await api.delete(`${BASE}/${id}`);
  },
};
