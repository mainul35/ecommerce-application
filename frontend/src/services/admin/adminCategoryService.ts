import api from '../api';
import type { Category, PagedResponse } from '../../types';

export interface CategoryUpsertRequest {
  name: string;
  slug: string;
  description?: string;
  parentId?: string | null;
  imageUrl?: string;
}

const BASE = '/admin/categories';

export const adminCategoryService = {
  async list(): Promise<Category[]> {
    const response = await api.get<Category[] | PagedResponse<Category>>(BASE);
    // Backend returns Flux<CategoryDto> -> JSON array
    const data = response.data as Category[];
    return Array.isArray(data) ? data : (data as PagedResponse<Category>).content;
  },

  async getById(id: string): Promise<Category> {
    const response = await api.get<Category>(`${BASE}/${id}`);
    return response.data;
  },

  async create(payload: CategoryUpsertRequest): Promise<Category> {
    const response = await api.post<Category>(BASE, payload);
    return response.data;
  },

  async update(id: string, payload: CategoryUpsertRequest): Promise<Category> {
    const response = await api.put<Category>(`${BASE}/${id}`, payload);
    return response.data;
  },

  async delete(id: string): Promise<void> {
    await api.delete(`${BASE}/${id}`);
  },
};
