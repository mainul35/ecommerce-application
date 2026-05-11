import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAppSelector } from '../store';
import { canAccessAdmin } from './permissions';

/**
 * Route guard for the /admin/* surface. Pages under AdminRoute can assume
 * the user is authenticated and authorized - no inline role checks needed
 * inside admin pages or components.
 *
 * On a cold page load the JWT exists in localStorage but the user object
 * has not been hydrated yet (AuthBootstrap is fetching it). In that window
 * we render a loader rather than misclassifying the user as a non-admin
 * and bouncing them to the storefront.
 */
export function AdminRoute() {
  const location = useLocation();
  const { isAuthenticated, user, isLoading } = useAppSelector((state) => state.auth);

  if (!isAuthenticated) {
    return <Navigate to="/admin/login" state={{ from: location.pathname }} replace />;
  }

  if (isAuthenticated && !user) {
    return (
      <div
        className="d-flex align-items-center justify-content-center"
        style={{ minHeight: '100vh' }}
      >
        <div className="spinner-border text-primary" role="status">
          <span className="visually-hidden">{isLoading ? 'Loading...' : 'Initializing...'}</span>
        </div>
      </div>
    );
  }

  if (!canAccessAdmin(user)) {
    // Authenticated as a non-admin (e.g. customer): bounce to admin login so they can
    // sign in with admin credentials, rather than dropping them on the storefront.
    return <Navigate to="/admin/login" replace />;
  }

  return <Outlet />;
}
