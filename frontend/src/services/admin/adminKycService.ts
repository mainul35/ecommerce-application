import api from '../api';
import type { KycCase, KycStatus, PagedResponse } from '../../types';

/** Admin review queue for KYC cases automation could not auto-approve. */
export const adminKycService = {
  async list(status?: KycStatus, page = 0, size = 20): Promise<PagedResponse<KycCase>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) params.set('status', status);
    const response = await api.get<PagedResponse<KycCase>>(`/admin/kyc?${params.toString()}`);
    return response.data;
  },

  async getCase(id: string): Promise<KycCase> {
    const response = await api.get<KycCase>(`/admin/kyc/${id}`);
    return response.data;
  },

  /** Marks the account id_verified and purges the evidence. */
  async approve(id: string): Promise<KycCase> {
    const response = await api.post<KycCase>(`/admin/kyc/${id}/approve`);
    return response.data;
  },

  /** Rejects with a reason shown to the user; purges the evidence. */
  async reject(id: string, reason: string): Promise<KycCase> {
    const response = await api.post<KycCase>(`/admin/kyc/${id}/reject`, { reason });
    return response.data;
  },

  /** Evidence images stream through the shared party-checked endpoint. */
  async fetchDocumentObjectUrl(caseId: string, documentId: string): Promise<string> {
    const response = await api.get<Blob>(
      `/kyc/cases/${caseId}/documents/${documentId}/file`,
      { responseType: 'blob', timeout: 60000 }
    );
    return URL.createObjectURL(response.data);
  },
};
