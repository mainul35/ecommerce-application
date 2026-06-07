import api from './api';
import type { EscrowTransaction, ReturnRequest } from '../types';

/** Buyer-facing escrow surface of an order. */
export const escrowService = {
  /** Escrow status of one of my orders, one entry per seller group. */
  async getOrderEscrow(orderId: string): Promise<EscrowTransaction[]> {
    const response = await api.get<EscrowTransaction[]>(`/orders/${orderId}/escrow`);
    return response.data;
  },

  /** Confirm receipt: releases all held (non-disputed) funds to the sellers. */
  async confirmReceipt(orderId: string): Promise<EscrowTransaction[]> {
    const response = await api.post<EscrowTransaction[]>(`/orders/${orderId}/confirm-receipt`);
    return response.data;
  },

  /** Request a (partial) return of one delivered item. */
  async requestReturn(
    orderId: string,
    orderItemId: string,
    quantity: number,
    reason: string
  ): Promise<ReturnRequest> {
    const response = await api.post<ReturnRequest>(
      `/orders/${orderId}/items/${orderItemId}/returns`,
      { quantity, reason }
    );
    return response.data;
  },
};
