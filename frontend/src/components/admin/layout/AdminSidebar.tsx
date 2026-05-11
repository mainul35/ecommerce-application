import { NavLink } from 'react-router-dom';

interface NavItem {
  to: string;
  label: string;
  icon: string;
  end?: boolean;
}

const navItems: NavItem[] = [
  { to: '/admin', label: 'Dashboard', icon: 'bi-speedometer2', end: true },
  { to: '/admin/categories', label: 'Categories', icon: 'bi-tags' },
  { to: '/admin/products', label: 'Products', icon: 'bi-box-seam' },
  { to: '/admin/discounts', label: 'Discounts', icon: 'bi-tag' },
  { to: '/admin/discount-templates', label: 'Templates', icon: 'bi-bookmark' },
  { to: '/admin/coupons', label: 'Coupons', icon: 'bi-ticket-perforated' },
  { to: '/admin/orders', label: 'Orders', icon: 'bi-receipt' },
  { to: '/admin/settings', label: 'Settings', icon: 'bi-gear' },
];

/**
 * AdminLTE 4 sidebar. Uses the standard `.app-sidebar > .sidebar-brand + .sidebar-wrapper` markup.
 * Active link styling is delegated to NavLink + AdminLTE's `.nav-sidebar .active` rule.
 */
export function AdminSidebar() {
  return (
    <aside className="app-sidebar bg-body-secondary shadow" data-bs-theme="dark">
      <div className="sidebar-brand">
        <NavLink to="/admin" className="brand-link">
          <i className="bi bi-shop brand-image opacity-75"></i>
          <span className="brand-text fw-light">Admin Console</span>
        </NavLink>
      </div>

      <div className="sidebar-wrapper">
        <nav className="mt-2">
          <ul className="nav sidebar-menu flex-column" data-lte-toggle="treeview" data-accordion="false">
            {navItems.map((item) => (
              <li key={item.to} className="nav-item">
                <NavLink
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
                >
                  <i className={`nav-icon bi ${item.icon}`}></i>
                  <p>{item.label}</p>
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
      </div>
    </aside>
  );
}
