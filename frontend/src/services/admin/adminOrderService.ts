import api from '../api';
import type { Order, OrderStatus, PagedResponse, User, Address } from '../../types';

export interface OrderItemRequest {
  productId: string;
  quantity: number;
}

export interface OrderCreateRequest {
  items: OrderItemRequest[];
  shippingAddress: Address;
  billingAddress: Address;
  paymentMethod: string;
  notes?: string;
}

const BASE = '/admin/orders';

export const adminOrderService = {
  async list(status?: OrderStatus, page = 0, size = 20): Promise<PagedResponse<Order>> {
    const params = new URLSearchParams();
    params.append('page', page.toString());
    params.append('size', size.toString());
    if (status) params.append('status', status);
    const response = await api.get<PagedResponse<Order>>(`${BASE}?${params.toString()}`);
    return response.data;
  },

  async getById(id: string): Promise<Order> {
    const response = await api.get<Order>(`${BASE}/${id}`);
    return response.data;
  },

  async createForCustomer(customerId: string, payload: OrderCreateRequest): Promise<Order> {
    const response = await api.post<Order>(`${BASE}?customerId=${customerId}`, payload);
    return response.data;
  },

  async transitionStatus(id: string, status: OrderStatus): Promise<Order> {
    const response = await api.patch<Order>(`${BASE}/${id}/status`, { status });
    return response.data;
  },

  async cancel(id: string, reason?: string): Promise<Order> {
    const response = await api.post<Order>(`${BASE}/${id}/cancel`, { reason: reason ?? '' });
    return response.data;
  },

  async markPaid(id: string, reference?: string): Promise<Order> {
    const response = await api.post<Order>(`${BASE}/${id}/mark-paid`, {
      reference: reference ?? 'manual:admin',
    });
    return response.data;
  },

  async searchCustomers(q: string): Promise<User[]> {
    const response = await api.get<User[]>(`/admin/customers?q=${encodeURIComponent(q)}`);
    return response.data;
  },
};
