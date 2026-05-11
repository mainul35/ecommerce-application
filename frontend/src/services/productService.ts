import api from './api';
import type { Product, Category, ProductFilters, PagedResponse } from '../types';

export const productService = {
  async getProducts(
    page: number = 0,
    size: number = 12,
    filters: ProductFilters = {}
  ): Promise<PagedResponse<Product>> {
    const params = new URLSearchParams();
    params.append('page', page.toString());
    params.append('size', size.toString());

    if (filters.categoryId) {
      params.append('categoryId', filters.categoryId);
    }
    if (filters.minPrice !== undefined) {
      params.append('minPrice', filters.minPrice.toString());
    }
    if (filters.maxPrice !== undefined) {
      params.append('maxPrice', filters.maxPrice.toString());
    }
    if (filters.search) {
      params.append('search', filters.search);
    }
    if (filters.sortBy) {
      params.append('sortBy', filters.sortBy);
    }

    const response = await api.get<PagedResponse<Product>>(`/products?${params.toString()}`);
    return response.data;
  },

  async getProductById(id: string): Promise<Product> {
    const response = await api.get<Product>(`/products/${id}`);
    return response.data;
  },

  async getProductsByCategory(
    categoryId: string,
    page: number = 0,
    size: number = 12
  ): Promise<PagedResponse<Product>> {
    const response = await api.get<PagedResponse<Product>>(
      `/products/category/${categoryId}?page=${page}&size=${size}`
    );
    return response.data;
  },

  async searchProducts(
    query: string,
    page: number = 0,
    size: number = 12
  ): Promise<PagedResponse<Product>> {
    const response = await api.get<PagedResponse<Product>>(
      `/products/search?q=${encodeURIComponent(query)}&page=${page}&size=${size}`
    );
    return response.data;
  },

  async getFeaturedProducts(): Promise<Product[]> {
    const response = await api.get<Product[]>('/products/featured');
    return response.data;
  },

  async getCategories(): Promise<Category[]> {
    const response = await api.get<Category[]>('/categories');
    return response.data;
  },

  async getCategoryById(id: string): Promise<Category> {
    const response = await api.get<Category>(`/categories/${id}`);
    return response.data;
  },
};
