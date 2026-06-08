import api from './api';
import type { KycCase, KycDocType, KycDocumentMeta, SellerProfile } from '../types';

/**
 * Seller self-service e-KYC. Evidence files are transient on the backend
 * (purged on decision or after 72h); only users.idVerified persists.
 */
export const kycService = {
  // ---- Profile ----

  async upsertProfile(profile: SellerProfile): Promise<SellerProfile> {
    const response = await api.put<SellerProfile>('/kyc/profile', profile);
    return response.data;
  },

  async getProfile(): Promise<SellerProfile> {
    const response = await api.get<SellerProfile>('/kyc/profile');
    return response.data;
  },

  // ---- Case ----

  async openCase(): Promise<KycCase> {
    const response = await api.post<KycCase>('/kyc/cases');
    return response.data;
  },

  /** Latest case - poll while status is SUBMITTED/CHECKING. */
  async getCurrentCase(): Promise<KycCase> {
    const response = await api.get<KycCase>('/kyc/cases/current');
    return response.data;
  },

  /** Upload one evidence slot; re-upload replaces it. Selfies come from the camera as Blobs. */
  async uploadDocument(
    caseId: string,
    docType: KycDocType,
    file: File | Blob,
    fileName = 'capture.jpg'
  ): Promise<KycDocumentMeta> {
    const formData = new FormData();
    formData.append('file', file, file instanceof File ? file.name : fileName);
    const response = await api.post<KycDocumentMeta>(
      `/kyc/cases/${caseId}/documents?docType=${docType}`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 60000 }
    );
    return response.data;
  },

  /** Starts automated checks and the 72h retention clock. */
  async submit(caseId: string): Promise<KycCase> {
    const response = await api.post<KycCase>(`/kyc/cases/${caseId}/submit`);
    return response.data;
  },

  /** Authenticated evidence fetch (owner or staff) - returns an object URL. */
  async fetchDocumentObjectUrl(caseId: string, documentId: string): Promise<string> {
    const response = await api.get<Blob>(
      `/kyc/cases/${caseId}/documents/${documentId}/file`,
      { responseType: 'blob', timeout: 60000 }
    );
    return URL.createObjectURL(response.data);
  },
};
