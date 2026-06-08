import { Link } from 'react-router-dom';
import { useAppSelector } from '../../store';

/**
 * Persistent reminder shown to signed-in users who haven't finished the
 * mandatory email + phone verification. Hidden once both are done (the
 * user object carries the flags from /api/auth/me).
 */
export function UnverifiedBanner() {
  const { isAuthenticated, user } = useAppSelector((s) => s.auth);

  if (!isAuthenticated || !user) return null;
  const fullyVerified = Boolean(user.emailVerified) && Boolean(user.phoneVerified);
  if (fullyVerified) return null;

  const missing = [
    !user.emailVerified ? 'email' : null,
    !user.phoneVerified ? 'phone' : null,
  ].filter(Boolean).join(' and ');

  return (
    <div className="alert alert-warning border-0 rounded-0 mb-0 py-2">
      <div className="container d-flex align-items-center justify-content-between flex-wrap gap-2">
        <span className="small mb-0">
          <i className="bi bi-exclamation-triangle-fill me-2"></i>
          Verify your {missing} to place orders and sell on the marketplace.
        </span>
        <Link to="/verify" className="btn btn-sm btn-warning fw-semibold">
          Verify now
        </Link>
      </div>
    </div>
  );
}
