import api from '../api';
import type { Currency } from '../../types';

export interface CurrencyUpsertRequest {
  code: string;
  name: string;
  symbol: string;
  exchangeRate: number;
  isActive?: boolean;
}

const BASE = '/admin/currencies';

export const adminCurrencyService = {
  async list(): Promise<Currency[]> {
    const response = await api.get<Currency[]>(BASE);
    return response.data;
  },
  async create(payload: CurrencyUpsertRequest): Promise<Currency> {
    const response = await api.post<Currency>(BASE, payload);
    return response.data;
  },
  async update(code: string, payload: CurrencyUpsertRequest): Promise<Currency> {
    const response = await api.put<Currency>(`${BASE}/${code}`, payload);
    return response.data;
  },
  async setBase(code: string): Promise<Currency> {
    const response = await api.post<Currency>(`${BASE}/${code}/set-base`);
    return response.data;
  },
  async delete(code: string): Promise<void> {
    await api.delete(`${BASE}/${code}`);
  },
};
