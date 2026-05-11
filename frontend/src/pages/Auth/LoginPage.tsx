import { useState, useEffect } from 'react';
import { Link, Navigate, useNavigate, useLocation } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../store';
import { login, clearError } from '../../store/slices/authSlice';
import { canAccessAdmin } from '../../routes/permissions';

export function LoginPage() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const { isAuthenticated, isLoading, error, user } = useAppSelector((state) => state.auth);

  const [formData, setFormData] = useState({
    email: '',
    password: '',
  });

  const from = (location.state as { from?: { pathname: string } })?.from?.pathname;

  useEffect(() => {
    if (!isAuthenticated) return;
    if (canAccessAdmin(user)) return; // handled by the early Navigate below
    // Customer-only login: this page is rendered for shoppers. Admin sign-in
    // lives at /admin/login and the backend rejects ADMIN accounts here.
    navigate(from ?? '/', { replace: true });
  }, [isAuthenticated, user, navigate, from]);

  // An admin already holds a session - block them from this customer surface
  // entirely (don't even render the form). They get bounced to /admin.
  if (isAuthenticated && canAccessAdmin(user)) {
    return <Navigate to="/admin" replace />;
  }

  useEffect(() => {
    return () => {
      dispatch(clearError());
    };
  }, [dispatch]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    dispatch(login(formData));
  };

  return (
    <div className="container py-5">
      <div className="auth-form">
        <h2 className="auth-form-title">Welcome Back</h2>

        {error && (
          <div className="alert alert-danger" role="alert">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="mb-3">
            <label htmlFor="email" className="form-label">
              Email
            </label>
            <input
              type="text"
              className="form-control"
              id="email"
              name="email"
              value={formData.email}
              onChange={handleChange}
              autoComplete="username"
              placeholder="you@example.com"
              required
            />
          </div>

          <div className="mb-3">
            <label htmlFor="password" className="form-label">
              Password
            </label>
            <input
              type="password"
              className="form-control"
              id="password"
              name="password"
              value={formData.password}
              onChange={handleChange}
              required
            />
          </div>

          <div className="d-flex justify-content-between align-items-center mb-4">
            <div className="form-check">
              <input
                type="checkbox"
                className="form-check-input"
                id="rememberMe"
              />
              <label className="form-check-label" htmlFor="rememberMe">
                Remember me
              </label>
            </div>
            <Link to="/forgot-password" className="text-primary">
              Forgot password?
            </Link>
          </div>

          <button
            type="submit"
            className="btn btn-primary w-100 mb-3"
            disabled={isLoading}
          >
            {isLoading ? (
              <>
                <span className="spinner-border spinner-border-sm me-2" />
                Signing in...
              </>
            ) : (
              'Sign In'
            )}
          </button>
        </form>

        <p className="text-center text-muted mb-2">
          Don't have an account?{' '}
          <Link to="/register" className="text-primary">
            Sign up
          </Link>
        </p>
        <p className="text-center text-muted small mb-0">
          Administrator?{' '}
          <Link to="/admin/login" className="text-primary">
            Sign in to admin console
          </Link>
        </p>
      </div>
    </div>
  );
}
