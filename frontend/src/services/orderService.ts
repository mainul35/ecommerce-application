import api from './api';
import type { Order, Address, PagedResponse, CartItem } from '../types';

export interface CreateOrderRequest {
  items: Array<{
    productId: string;
    quantity: number;
  }>;
  shippingAddress: Address;
  billingAddress: Address;
  paymentMethod: string;
}

export const orderService = {
  async createOrder(orderData: CreateOrderRequest): Promise<Order> {
    const response = await api.post<Order>('/orders', orderData);
    return response.data;
  },

  async getOrders(page: number = 0, size: number = 10): Promise<PagedResponse<Order>> {
    const response = await api.get<PagedResponse<Order>>(`/orders?page=${page}&size=${size}`);
    return response.data;
  },

  async getOrderById(id: string): Promise<Order> {
    const response = await api.get<Order>(`/orders/${id}`);
    return response.data;
  },

  async cancelOrder(id: string): Promise<Order> {
    const response = await api.put<Order>(`/orders/${id}/cancel`);
    return response.data;
  },

  async getOrderTracking(id: string): Promise<{ status: string; history: Array<{ status: string; timestamp: string; description: string }> }> {
    const response = await api.get(`/orders/${id}/tracking`);
    return response.data;
  },

  prepareOrderItems(cartItems: CartItem[]): CreateOrderRequest['items'] {
    return cartItems.map((item) => ({
      productId: item.product.id,
      quantity: item.quantity,
    }));
  },
};
