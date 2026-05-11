import api from './api';

export const checkoutService = {
  /**
   * Ask the backend to create a Stripe Checkout Session for the given order
   * and return the hosted-checkout URL the browser should redirect to.
   */
  async createSession(orderId: string): Promise<string> {
    const response = await api.post<{ checkoutUrl: string }>(
      `/orders/${orderId}/checkout/session`
    );
    return response.data.checkoutUrl;
  },
};
