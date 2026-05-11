import { Link } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../../store';
import { logout } from '../../../store/slices/authSlice';

interface AdminNavbarProps {
  onToggleSidebar: () => void;
}

/**
 * AdminLTE 4 top navigation bar. Uses the standard `.app-header.navbar` markup;
 * the hamburger triggers the parent layout's sidebar-collapse toggle.
 */
export function AdminNavbar({ onToggleSidebar }: AdminNavbarProps) {
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);

  return (
    <nav className="app-header navbar navbar-expand bg-body">
      <div className="container-fluid">
        <ul className="navbar-nav">
          <li className="nav-item">
            <button
              type="button"
              className="nav-link"
              onClick={onToggleSidebar}
              aria-label="Toggle sidebar"
            >
              <i className="bi bi-list" style={{ fontSize: '1.25rem' }}></i>
            </button>
          </li>
          <li className="nav-item d-none d-md-block">
            <Link to="/admin" className="nav-link">
              Home
            </Link>
          </li>
          <li className="nav-item d-none d-md-block">
            <Link to="/" className="nav-link">
              View Storefront
            </Link>
          </li>
        </ul>

        <ul className="navbar-nav ms-auto">
          <li className="nav-item dropdown user-menu">
            <button
              type="button"
              className="nav-link dropdown-toggle d-flex align-items-center gap-2"
              data-bs-toggle="dropdown"
              aria-expanded="false"
            >
              <span className="d-flex align-items-center justify-content-center rounded-circle bg-primary text-white" style={{ width: '32px', height: '32px' }}>
                <i className="bi bi-person"></i>
              </span>
              <span className="d-none d-md-inline">
                {user?.firstName} {user?.lastName}
              </span>
            </button>
            <ul className="dropdown-menu dropdown-menu-end">
              <li className="px-3 py-2 text-muted small">
                Signed in as <strong>{user?.email}</strong>
              </li>
              <li>
                <hr className="dropdown-divider" />
              </li>
              <li>
                <Link className="dropdown-item" to="/admin/settings">
                  <i className="bi bi-gear me-2"></i>
                  Account Settings
                </Link>
              </li>
              <li>
                <hr className="dropdown-divider" />
              </li>
              <li>
                <button className="dropdown-item" onClick={() => dispatch(logout())}>
                  <i className="bi bi-box-arrow-right me-2"></i>
                  Sign out
                </button>
              </li>
            </ul>
          </li>
        </ul>
      </div>
    </nav>
  );
}
