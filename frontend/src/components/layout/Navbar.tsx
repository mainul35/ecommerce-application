import { useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAppSelector, useAppDispatch } from '../../store';
import { selectCartItemCount } from '../../store/slices/cartSlice';
import { logout } from '../../store/slices/authSlice';
import { canAccessAdmin } from '../../routes/permissions';
import { CurrencyPicker } from '../../storefront/CurrencyPicker';
import { RegionSelector } from '../../storefront/RegionSelector';

export function Navbar() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { isAuthenticated, user } = useAppSelector((state) => state.auth);
  const cartItemCount = useAppSelector(selectCartItemCount);
  const [searchQuery, setSearchQuery] = useState('');

  const handleLogout = () => {
    dispatch(logout());
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      navigate(`/search?q=${encodeURIComponent(searchQuery.trim())}`);
      setSearchQuery('');
    }
  };

  return (
    <nav className="navbar navbar-expand-lg navbar-light bg-white shadow-sm sticky-top">
      <div className="container">
        <Link className="navbar-brand" to="/">
          E-Commerce
        </Link>

        <button
          className="navbar-toggler"
          type="button"
          data-bs-toggle="collapse"
          data-bs-target="#navbarNav"
          aria-controls="navbarNav"
          aria-expanded="false"
          aria-label="Toggle navigation"
        >
          <span className="navbar-toggler-icon"></span>
        </button>

        <div className="collapse navbar-collapse" id="navbarNav">
          <ul className="navbar-nav me-auto">
            <li className="nav-item">
              <NavLink className="nav-link" to="/">
                Home
              </NavLink>
            </li>
            <li className="nav-item">
              <NavLink className="nav-link" to="/products">
                Products
              </NavLink>
            </li>
            <li className="nav-item">
              <NavLink className="nav-link" to="/categories">
                Categories
              </NavLink>
            </li>
          </ul>

          {/* Search bar — visible on md+ inline; full-width inside collapse on mobile */}
          <form className="d-none d-md-flex me-2" role="search" onSubmit={handleSearch}>
            <div className="input-group">
              <input
                className="form-control"
                type="search"
                placeholder="Search products…"
                aria-label="Search"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
              <button className="btn btn-outline-secondary" type="submit" aria-label="Submit search">
                <i className="bi bi-search"></i>
              </button>
            </div>
          </form>

          {/* Mobile search — shown inside the collapsed menu */}
          <form className="d-md-none mb-2" role="search" onSubmit={handleSearch}>
            <div className="input-group">
              <input
                className="form-control"
                type="search"
                placeholder="Search products…"
                aria-label="Search"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
              <button className="btn btn-outline-secondary" type="submit" aria-label="Submit search">
                <i className="bi bi-search"></i>
              </button>
            </div>
          </form>

          <div className="d-flex align-items-center gap-3">
            <RegionSelector />
            <CurrencyPicker />

            <Link
              to="/cart"
              className="btn btn-outline-primary position-relative"
              aria-label="Open cart"
            >
              <i className="bi bi-cart"></i>
              {cartItemCount > 0 && <span className="cart-badge">{cartItemCount}</span>}
            </Link>

            {isAuthenticated ? (
              <div className="dropdown">
                <button
                  className="btn btn-outline-secondary dropdown-toggle"
                  type="button"
                  data-bs-toggle="dropdown"
                  aria-expanded="false"
                >
                  {user?.firstName || 'Account'}
                </button>
                <ul className="dropdown-menu dropdown-menu-end">
                  <li>
                    <Link className="dropdown-item" to="/profile">
                      Profile
                    </Link>
                  </li>
                  <li>
                    <Link className="dropdown-item" to="/account/orders">
                      My Orders
                    </Link>
                  </li>
                  <li>
                    <Link className="dropdown-item" to="/account/disputes">
                      My Disputes
                    </Link>
                  </li>
                  <li>
                    <Link className="dropdown-item" to="/account/wallet">
                      Wallet
                    </Link>
                  </li>
                  {canAccessAdmin(user) && (
                    <>
                      <li>
                        <hr className="dropdown-divider" />
                      </li>
                      <li>
                        <Link className="dropdown-item text-primary fw-semibold" to="/admin">
                          Admin Console
                        </Link>
                      </li>
                    </>
                  )}
                  <li>
                    <hr className="dropdown-divider" />
                  </li>
                  <li>
                    <button className="dropdown-item" onClick={handleLogout}>
                      Logout
                    </button>
                  </li>
                </ul>
              </div>
            ) : (
              <div className="d-flex gap-2">
                <Link className="btn btn-outline-primary" to="/login">
                  Login
                </Link>
                <Link className="btn btn-primary" to="/register">
                  Register
                </Link>
              </div>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
}
