import { Link, useSearchParams } from 'react-router-dom';

export function CheckoutSuccessPage() {
  const [params] = useSearchParams();
  const sessionId = params.get('orderId') ?? params.get('session_id') ?? '';

  return (
    <div className="container py-5">
      <div className="text-center">
        <i className="bi bi-check-circle-fill text-success" style={{ fontSize: '4rem' }}></i>
        <h2 className="mt-3">Payment received</h2>
        <p className="text-muted">
          Thanks for your order. Your stock has been reserved and your order is being prepared.
        </p>
        {sessionId && (
          <p className="text-muted small">
            Reference: <code>{sessionId}</code>
          </p>
        )}
        <div className="mt-4 d-flex gap-2 justify-content-center">
          <Link to="/orders" className="btn btn-primary">
            View my orders
          </Link>
          <Link to="/products" className="btn btn-outline-secondary">
            Continue shopping
          </Link>
        </div>
      </div>
    </div>
  );
}
