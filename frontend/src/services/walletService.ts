import api from './api';
import type { PagedResponse, Wallet, WalletTransaction } from '../types';

/** My in-app wallet: refunds land here when the original payment method can't receive them. */
export const walletService = {
  async getWallet(): Promise<Wallet> {
    const response = await api.get<Wallet>('/wallet');
    return response.data;
  },

  async getTransactions(page = 0, size = 20): Promise<PagedResponse<WalletTransaction>> {
    const response = await api.get<PagedResponse<WalletTransaction>>(
      `/wallet/transactions?page=${page}&size=${size}`
    );
    return response.data;
  },
};
