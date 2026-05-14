import api from '../api';
import type { Region } from '../../types';

export interface RegionUpsertRequest {
  name: string;
  countryCode: string;
  currencyCode: string;
  isActive?: boolean;
}

const BASE = '/admin/regions';

export const adminRegionService = {
  async list(): Promise<Region[]> {
    const response = await api.get<Region[]>(BASE);
    return response.data;
  },
  async create(payload: RegionUpsertRequest): Promise<Region> {
    const response = await api.post<Region>(BASE, payload);
    return response.data;
  },
  async update(id: string, payload: RegionUpsertRequest): Promise<Region> {
    const response = await api.put<Region>(`${BASE}/${id}`, payload);
    return response.data;
  },
  async delete(id: string): Promise<void> {
    await api.delete(`${BASE}/${id}`);
  },
};
