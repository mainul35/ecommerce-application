import api from './api';
import type { Dispute, DisputeMessage } from '../types';

/**
 * Dispute conversation between buyer and seller, with escalation to support.
 * Posting a message uses multipart so image/video evidence can ride along.
 */
export const disputeService = {
  async openDispute(
    escrowTransactionId: string,
    reason: string,
    orderItemId?: string
  ): Promise<Dispute> {
    const response = await api.post<Dispute>('/disputes', {
      escrowTransactionId,
      orderItemId,
      reason,
    });
    return response.data;
  },

  /** Disputes I participate in - as buyer or seller. */
  async getMyDisputes(): Promise<Dispute[]> {
    const response = await api.get<Dispute[]>('/disputes');
    return response.data;
  },

  async getDispute(id: string): Promise<Dispute> {
    const response = await api.get<Dispute>(`/disputes/${id}`);
    return response.data;
  },

  async getMessages(id: string): Promise<DisputeMessage[]> {
    const response = await api.get<DisputeMessage[]>(`/disputes/${id}/messages`);
    return response.data;
  },

  /** Post a message with optional image/video attachments. */
  async postMessage(id: string, body: string, files: File[] = []): Promise<DisputeMessage> {
    const formData = new FormData();
    if (body) formData.append('body', body);
    files.forEach((file) => formData.append('files', file));
    const response = await api.post<DisputeMessage>(`/disputes/${id}/messages`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      // Video evidence can be large - give uploads more room than the default.
      timeout: 120000,
    });
    return response.data;
  },

  /** Forward the dispute and its conversation to admin/support. */
  async escalate(id: string): Promise<Dispute> {
    const response = await api.post<Dispute>(`/disputes/${id}/escalate`);
    return response.data;
  },

  /** Withdraw the dispute (opener only). */
  async withdraw(id: string): Promise<Dispute> {
    const response = await api.post<Dispute>(`/disputes/${id}/withdraw`);
    return response.data;
  },
};
