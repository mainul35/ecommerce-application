import { Navigate, Outlet } from 'react-router-dom';
import { useAppSelector } from '../store';
import { isAdmin } from './permissions';

/**
 * Stricter inner guard for routes inside the admin console that managers
 * must NOT reach (discounts, coupons, templates, orders, managers, customers).
 *
 * Sits *inside* AdminRoute so authentication and ADMIN-or-MANAGER access are
 * already verified. This component only does the second-tier admin check.
 * Managers landing here are bounced to the dashboard.
 */
export function AdminOnlyRoute() {
  const { user } = useAppSelector((state) => state.auth);

  if (!isAdmin(user)) {
    return <Navigate to="/admin" replace />;
  }

  return <Outlet />;
}
