import api from './api';
import type { PagedResponse, Product } from '../types';

export const searchService = {
  async search(query: string, page = 0, size = 20): Promise<PagedResponse<Product>> {
    const response = await api.get<PagedResponse<Product>>('/search', {
      params: { q: query, page, size },
    });
    return response.data;
  },
};
