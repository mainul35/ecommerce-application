import api from '../api';
import type {
  Dispute,
  DisputeMessage,
  DisputeStatus,
  EscrowStatus,
  EscrowTransaction,
  PagedResponse,
  ReturnRequest,
  ReturnStatus,
} from '../../types';

/** Admin surfaces for escrow, the dispute resolution queue, and return decisions. */
export const adminEscrowService = {
  // ---- Escrow ----

  async listEscrow(
    status?: EscrowStatus,
    page = 0,
    size = 20
  ): Promise<PagedResponse<EscrowTransaction>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) params.set('status', status);
    const response = await api.get<PagedResponse<EscrowTransaction>>(
      `/admin/escrow?${params.toString()}`
    );
    return response.data;
  },

  async releaseEscrow(id: string): Promise<EscrowTransaction> {
    const response = await api.post<EscrowTransaction>(`/admin/escrow/${id}/release`);
    return response.data;
  },

  // ---- Disputes ----

  async listDisputes(
    status?: DisputeStatus,
    page = 0,
    size = 20
  ): Promise<PagedResponse<Dispute>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) params.set('status', status);
    const response = await api.get<PagedResponse<Dispute>>(`/admin/disputes?${params.toString()}`);
    return response.data;
  },

  async getDispute(id: string): Promise<Dispute> {
    const response = await api.get<Dispute>(`/admin/disputes/${id}`);
    return response.data;
  },

  async getDisputeMessages(id: string): Promise<DisputeMessage[]> {
    const response = await api.get<DisputeMessage[]>(`/admin/disputes/${id}/messages`);
    return response.data;
  },

  /** Reply in the thread as support staff (with optional attachments). */
  async postDisputeMessage(id: string, body: string, files: File[] = []): Promise<DisputeMessage> {
    const formData = new FormData();
    if (body) formData.append('body', body);
    files.forEach((file) => formData.append('files', file));
    const response = await api.post<DisputeMessage>(`/admin/disputes/${id}/messages`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000,
    });
    return response.data;
  },

  /** RELEASE to the seller, or REFUND the buyer (full by default, partial via refundAmount). */
  async resolveDispute(
    id: string,
    action: 'RELEASE' | 'REFUND',
    options: { refundAmount?: number; note?: string } = {}
  ): Promise<Dispute> {
    const response = await api.post<Dispute>(`/admin/disputes/${id}/resolve`, {
      action,
      refundAmount: options.refundAmount,
      note: options.note,
    });
    return response.data;
  },

  // ---- Returns ----

  async listReturns(
    status?: ReturnStatus,
    page = 0,
    size = 20
  ): Promise<PagedResponse<ReturnRequest>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) params.set('status', status);
    const response = await api.get<PagedResponse<ReturnRequest>>(
      `/admin/returns?${params.toString()}`
    );
    return response.data;
  },

  async approveReturn(id: string): Promise<ReturnRequest> {
    const response = await api.post<ReturnRequest>(`/admin/returns/${id}/approve`);
    return response.data;
  },

  async rejectReturn(id: string, reason: string): Promise<ReturnRequest> {
    const response = await api.post<ReturnRequest>(`/admin/returns/${id}/reject`, { reason });
    return response.data;
  },
};
