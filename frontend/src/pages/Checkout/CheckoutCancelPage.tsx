import { Link } from 'react-router-dom';

export function CheckoutCancelPage() {
  return (
    <div className="container py-5">
      <div className="text-center">
        <i className="bi bi-x-circle-fill text-warning" style={{ fontSize: '4rem' }}></i>
        <h2 className="mt-3">Payment cancelled</h2>
        <p className="text-muted">
          Your order is still pending. Stock remains reserved for 7 days; you can finish payment any
          time before it expires.
        </p>
        <div className="mt-4 d-flex gap-2 justify-content-center">
          <Link to="/orders" className="btn btn-primary">
            Resume payment
          </Link>
          <Link to="/cart" className="btn btn-outline-secondary">
            Back to cart
          </Link>
        </div>
      </div>
    </div>
  );
}
