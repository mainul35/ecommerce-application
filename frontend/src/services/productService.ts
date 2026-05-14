import api from './api';
import type { Product, Category, ProductFilters, PagedResponse } from '../types';
import { getCurrentRegionId } from '../storefront/CurrencyContext';

/**
 * Read the visitor's region id (set by CurrencyContext) and append it to
 * URLSearchParams when present. Region filtering is purely additive on the
 * backend - omitting the param means "no region filter" (admin behaviour).
 */
function appendRegion(params: URLSearchParams): void {
  const regionId = getCurrentRegionId();
  if (regionId) params.append('regionId', regionId);
}

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
    appendRegion(params);

    const response = await api.get<PagedResponse<Product>>(`/products?${params.toString()}`);
    return response.data;
  },

  async getProductById(id: string): Promise<Product> {
    const params = new URLSearchParams();
    appendRegion(params);
    const qs = params.toString();
    const response = await api.get<Product>(`/products/${id}${qs ? `?${qs}` : ''}`);
    return response.data;
  },

  async getProductsByCategory(
    categoryId: string,
    page: number = 0,
    size: number = 12
  ): Promise<PagedResponse<Product>> {
    const params = new URLSearchParams();
    params.append('page', page.toString());
    params.append('size', size.toString());
    appendRegion(params);
    const response = await api.get<PagedResponse<Product>>(
      `/products/category/${categoryId}?${params.toString()}`
    );
    return response.data;
  },

  async searchProducts(
    query: string,
    page: number = 0,
    size: number = 12
  ): Promise<PagedResponse<Product>> {
    const params = new URLSearchParams();
    params.append('q', query);
    params.append('page', page.toString());
    params.append('size', size.toString());
    appendRegion(params);
    const response = await api.get<PagedResponse<Product>>(`/products/search?${params.toString()}`);
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
