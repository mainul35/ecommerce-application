import api from './api';

export interface PaymentGatewayInfo {
  id: string;
  displayName: string;
  description: string;
  iconClass: string;
  /** True when real credentials are wired up and the gateway can process payments. */
  configured: boolean;
}

export const paymentGatewayService = {
  async getForCountry(countryCode: string): Promise<PaymentGatewayInfo[]> {
    const res = await api.get<PaymentGatewayInfo[]>(
      `/payment-gateways?countryCode=${encodeURIComponent(countryCode)}`
    );
    return res.data;
  },
};
