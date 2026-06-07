import api from './api';
import type { ReturnRequest } from '../types';

/** My return requests. Creation lives on escrowService.requestReturn. */
export const returnService = {
  async getMyReturns(): Promise<ReturnRequest[]> {
    const response = await api.get<ReturnRequest[]>('/returns');
    return response.data;
  },

  async cancelReturn(id: string): Promise<ReturnRequest> {
    const response = await api.post<ReturnRequest>(`/returns/${id}/cancel`);
    return response.data;
  },
};
