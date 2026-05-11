import { useEffect, type ReactNode } from 'react';
import { useAppDispatch, useAppSelector } from '../store';
import { fetchCurrentUser } from '../store/slices/authSlice';

/**
 * Rehydrates the current user on app boot. We persist only the JWT in
 * localStorage; the user profile (role, name, email) is fetched on every
 * cold start so role-based routing decisions don't run against a stale
 * or missing user object.
 *
 * Without this, AdminRoute would see "authenticated but user is null" on
 * a page refresh and bounce admins back to the storefront.
 */
export function AuthBootstrap({ children }: Readonly<{ children: ReactNode }>) {
  const dispatch = useAppDispatch();
  const { token, user, isLoading } = useAppSelector((state) => state.auth);

  useEffect(() => {
    if (token && !user && !isLoading) {
      dispatch(fetchCurrentUser());
    }
  }, [token, user, isLoading, dispatch]);

  return <>{children}</>;
}
