import api from '../api';
import type { ProductMedia } from '../../types';

/** Derives the backend origin from the configured API base URL. */
const BACKEND_ORIGIN = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api').replace(/\/api\/?$/, '');

/**
 * Converts a stored media URL to a full src URL. Local storage yields a relative
 * path (/uploads/products/…) that is prefixed with the backend origin; S3/MinIO
 * yields an absolute public URL (http…) which is used as-is.
 */
export function mediaUrl(relativeUrl: string): string {
  if (/^https?:\/\//i.test(relativeUrl)) {
    return relativeUrl;
  }
  return `${BACKEND_ORIGIN}${relativeUrl}`;
}

export const adminProductMediaService = {
  async list(productId: string): Promise<ProductMedia[]> {
    const res = await api.get<ProductMedia[]>(`/admin/products/${productId}/media`);
    return res.data;
  },

  async upload(productId: string, file: File): Promise<ProductMedia> {
    const form = new FormData();
    form.append('file', file);
    const res = await api.post<ProductMedia>(`/admin/products/${productId}/media`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120_000, // 2 min for large video files
    });
    return res.data;
  },

  async delete(productId: string, mediaId: string): Promise<void> {
    await api.delete(`/admin/products/${productId}/media/${mediaId}`);
  },

  async reorder(productId: string, orderedIds: string[]): Promise<void> {
    await api.put(`/admin/products/${productId}/media/order`, orderedIds);
  },
};
