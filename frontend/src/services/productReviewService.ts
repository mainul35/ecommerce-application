import api from './api';
import type { ProductReview } from '../types';

export const productReviewService = {
  async list(productId: string): Promise<ProductReview[]> {
    const res = await api.get<ProductReview[]>(`/products/${productId}/reviews`);
    return res.data;
  },

  async create(productId: string, payload: { rating: number; title?: string; body?: string }): Promise<ProductReview> {
    const res = await api.post<ProductReview>(`/products/${productId}/reviews`, payload);
    return res.data;
  },
};
