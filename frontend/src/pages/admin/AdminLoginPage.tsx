import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../store';
import { adminLogin, clearError } from '../../store/slices/authSlice';
import { canAccessAdmin } from '../../routes/permissions';

/**
 * Dedicated admin login page at /admin/login. Hits /api/admin/auth/login,
 * which rejects any non-ADMIN account on the backend - so customers cannot
 * obtain a session through this surface even if they know an admin URL.
 *
 * Visually distinct from the customer storefront login (dark admin theme,
 * AdminLTE-style card) so it's obvious which surface you're on.
 */
export function AdminLoginPage() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const { isAuthenticated, isLoading, error, user } = useAppSelector((state) => state.auth);

  const [formData, setFormData] = useState({ email: '', password: '' });

  const from = (location.state as { from?: string })?.from;

  useEffect(() => {
    if (!isAuthenticated) return;
    // Only route to admin if the rehydrated user actually has admin access.
    // (A token from a customer session would otherwise land an unprivileged
    // user on /admin and get bounced by AdminRoute.)
    if (canAccessAdmin(user)) {
      navigate(from ?? '/admin', { replace: true });
    }
  }, [isAuthenticated, user, navigate, from]);

  useEffect(() => () => { dispatch(clearError()); }, [dispatch]);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    dispatch(adminLogin(formData));
  };

  return (
    <div
      className="min-vh-100 d-flex align-items-center justify-content-center bg-dark"
      data-bs-theme="dark"
    >
      <div className="card shadow-lg" style={{ width: '100%', maxWidth: '420px' }}>
        <div className="card-body p-4">
          <div className="text-center mb-4">
            <i className="bi bi-shield-lock-fill text-warning" style={{ fontSize: '2.5rem' }}></i>
            <h2 className="h4 mt-2 mb-0">
              <span className="text-warning">Admin</span> Console
            </h2>
            <p className="text-muted small mb-0">Sign in with administrator credentials</p>
          </div>

          {error && (
            <div className="alert alert-danger" role="alert">
              {error}
            </div>
          )}

          <form onSubmit={submit} noValidate>
            <div className="mb-3">
              <label htmlFor="adminIdentifier" className="form-label">
                Username or email
              </label>
              <input
                type="text"
                inputMode="text"
                id="adminIdentifier"
                name="username"
                className="form-control"
                value={formData.email}
                onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                autoComplete="username"
                placeholder="admin"
                required
                autoFocus
              />
            </div>

            <div className="mb-4">
              <label htmlFor="adminPassword" className="form-label">
                Password
              </label>
              <input
                type="password"
                id="adminPassword"
                className="form-control"
                value={formData.password}
                onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                autoComplete="current-password"
                required
              />
            </div>

            <button
              type="submit"
              className="btn btn-warning w-100 fw-semibold"
              disabled={isLoading}
            >
              {isLoading ? (
                <>
                  <span className="spinner-border spinner-border-sm me-2" />
                  Signing in…
                </>
              ) : (
                <>
                  <i className="bi bi-box-arrow-in-right me-2"></i>
                  Sign in
                </>
              )}
            </button>
          </form>

          <hr className="my-4" />
          <p className="text-center text-muted small mb-0">
            Customer?{' '}
            <Link to="/login" className="text-warning">
              Go to storefront login
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
