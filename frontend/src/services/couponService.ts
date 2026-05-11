import api from './api';
import type { CouponValidationResponse } from '../types';

interface CouponItem {
  productId: string;
  quantity: number;
}

export const couponService = {
  /**
   * Preview the effect of {@code code} on a cart of {@code items}. The same
   * checks run again at order create time, so this preview is informational only.
   */
  async validate(code: string, items: CouponItem[]): Promise<CouponValidationResponse> {
    const response = await api.post<CouponValidationResponse>('/coupons/validate', {
      code,
      items,
    });
    return response.data;
  },
};
