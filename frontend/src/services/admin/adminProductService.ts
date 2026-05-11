import api from '../api';
import type { Product, PagedResponse } from '../../types';

export interface ProductUpsertRequest {
  name: string;
  description?: string;
  price: number;
  originalPrice?: number;
  imageUrl?: string;
  images?: string[];
  categoryId: string;
  attributes?: Record<string, unknown>;
  stock: number;
  sku: string;
}

const BASE = '/admin/products';

export const adminProductService = {
  async list(page = 0, size = 20, sortBy?: string): Promise<PagedResponse<Product>> {
    const params = new URLSearchParams();
    params.append('page', page.toString());
    params.append('size', size.toString());
    if (sortBy) params.append('sortBy', sortBy);
    const response = await api.get<PagedResponse<Product>>(`${BASE}?${params.toString()}`);
    return response.data;
  },

  async getById(id: string): Promise<Product> {
    const response = await api.get<Product>(`${BASE}/${id}`);
    return response.data;
  },

  async create(payload: ProductUpsertRequest): Promise<Product> {
    const response = await api.post<Product>(BASE, payload);
    return response.data;
  },

  async update(id: string, payload: ProductUpsertRequest): Promise<Product> {
    const response = await api.put<Product>(`${BASE}/${id}`, payload);
    return response.data;
  },

  async delete(id: string): Promise<void> {
    await api.delete(`${BASE}/${id}`);
  },
};
