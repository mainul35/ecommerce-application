import api from './api';
import type { VerificationStatus } from '../types';

/**
 * Mandatory account verification: email (real) + phone (dummy OTP for now).
 * Both must be verified before checkout / placing orders / starting seller KYC.
 */
export const verificationService = {
  async getStatus(): Promise<VerificationStatus> {
    const response = await api.get<VerificationStatus>('/verification/status');
    return response.data;
  },

  /** Confirm the email link token (public endpoint - works without a session). */
  async verifyEmail(token: string): Promise<void> {
    await api.post('/verification/email/verify', { token });
  },

  async resendEmail(): Promise<void> {
    await api.post('/verification/email/resend');
  },

  /** Save the phone number and trigger the (dummy) OTP. */
  async sendPhoneCode(phone: string): Promise<void> {
    await api.post('/verification/phone/send', { phone });
  },

  async verifyPhone(code: string): Promise<void> {
    await api.post('/verification/phone/verify', { code });
  },
};
