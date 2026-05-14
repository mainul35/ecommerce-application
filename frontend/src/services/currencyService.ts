import api from './api';
import type { Currency, Region } from '../types';

export const currencyService = {
  async listActive(): Promise<Currency[]> {
    const response = await api.get<Currency[]>('/currencies');
    return response.data;
  },
  async getBase(): Promise<Currency> {
    const response = await api.get<Currency>('/currencies/base');
    return response.data;
  },
};

export const regionService = {
  async listActive(): Promise<Region[]> {
    const response = await api.get<Region[]>('/regions');
    return response.data;
  },
  /** Returns the configured Region for the country, or null if not configured. */
  async findByCountry(countryCode: string): Promise<Region | null> {
    try {
      const response = await api.get<Region>(`/regions/by-country/${countryCode}`);
      return response.data;
    } catch (e) {
      // 404 is the expected "no region for that country" path; rethrow other errors.
      if (e instanceof Error && /404|not\s+found/i.test(e.message)) return null;
      throw e;
    }
  },
};
