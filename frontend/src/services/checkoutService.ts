import api from './api';

export const checkoutService = {
  /**
   * Create a checkout session for the given order via the specified gateway.
   * Returns the hosted-checkout URL the browser should redirect to.
   */
  async createSession(orderId: string, gatewayId: string = 'stripe'): Promise<string> {
    const response = await api.post<{ checkoutUrl: string }>(
      `/orders/${orderId}/checkout/session?gateway=${encodeURIComponent(gatewayId)}`
    );
    return response.data.checkoutUrl;
  },
};
