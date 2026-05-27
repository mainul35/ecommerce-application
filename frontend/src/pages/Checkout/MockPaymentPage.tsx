import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import api from '../../services/api';

export function MockPaymentPage() {
  const [params] = useSearchParams();
  const orderId = params.get('orderId') ?? '';
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handlePay = async () => {
    setProcessing(true);
    setError(null);
    try {
      const res = await api.post<{ redirectUrl: string }>(
        `/payment/mock/${orderId}/confirm`
      );
      window.location.href = res.data.redirectUrl;
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Payment failed');
      setProcessing(false);
    }
  };

  const handleCancel = async () => {
    setProcessing(true);
    try {
      const res = await api.post<{ redirectUrl: string }>(
        `/payment/mock/${orderId}/cancel`
      );
      window.location.href = res.data.redirectUrl;
    } catch {
      window.location.href = '/cart';
    }
  };

  if (!orderId) {
    return (
      <div className="container py-5 text-center">
        <p className="text-danger">Missing order ID.</p>
      </div>
    );
  }

  return (
    <div className="container py-5">
      <div className="row justify-content-center">
        <div className="col-12 col-sm-8 col-md-6 col-lg-5">
          <div className="card border-0 shadow rounded-4 overflow-hidden">
            {/* Header */}
            <div className="card-header text-center py-4"
              style={{ background: 'linear-gradient(135deg,#1a1a2e,#16213e)' }}>
              <i className="bi bi-play-circle-fill text-warning d-block mb-2"
                style={{ fontSize: '2.5rem' }}></i>
              <h4 className="text-white fw-bold mb-0">Simulated Payment</h4>
              <p className="text-white-50 small mb-0">Development mode — no real money moves</p>
            </div>

            <div className="card-body p-4">
              <div className="alert alert-warning d-flex align-items-start gap-2 rounded-3 mb-4 py-2">
                <i className="bi bi-cone-striped flex-shrink-0 mt-1"></i>
                <span className="small">
                  This page simulates a payment gateway. In production, real
                  providers (Stripe, bKash, PayPay…) replace this screen.
                </span>
              </div>

              <p className="text-muted small mb-1">Order reference</p>
              <code className="d-block bg-light rounded px-3 py-2 mb-4 small text-break">{orderId}</code>

              {error && (
                <div className="alert alert-danger py-2 small rounded-3 mb-3">
                  <i className="bi bi-exclamation-triangle-fill me-2"></i>{error}
                </div>
              )}

              <div className="d-grid gap-2">
                <button
                  type="button"
                  className="btn btn-success fw-bold py-2"
                  onClick={handlePay}
                  disabled={processing}
                >
                  {processing
                    ? <><span className="spinner-border spinner-border-sm me-2"></span>Processing…</>
                    : <><i className="bi bi-check-circle-fill me-2"></i>Simulate Successful Payment</>}
                </button>
                <button
                  type="button"
                  className="btn btn-outline-secondary"
                  onClick={handleCancel}
                  disabled={processing}
                >
                  <i className="bi bi-x-circle me-2"></i>Simulate Cancellation
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
