import { NavLink } from 'react-router-dom';
import { useAppSelector } from '../../../store';
import { canManageManagers, canManagePromotions, canManageOrders } from '../../../routes/permissions';

interface NavItem {
  to: string;
  label: string;
  icon: string;
  end?: boolean;
  /** Returns true if the current user should see this item. */
  visibleFor: (visible: { isAdminUser: boolean }) => boolean;
}

const navItems: NavItem[] = [
  { to: '/admin', label: 'Dashboard', icon: 'bi-speedometer2', end: true, visibleFor: () => true },
  { to: '/admin/categories', label: 'Categories', icon: 'bi-tags', visibleFor: () => true },
  { to: '/admin/products', label: 'Products', icon: 'bi-box-seam', visibleFor: () => true },
  { to: '/admin/discounts', label: 'Discounts', icon: 'bi-tag', visibleFor: ({ isAdminUser }) => isAdminUser },
  { to: '/admin/discount-templates', label: 'Templates', icon: 'bi-bookmark', visibleFor: ({ isAdminUser }) => isAdminUser },
  { to: '/admin/coupons', label: 'Coupons', icon: 'bi-ticket-perforated', visibleFor: ({ isAdminUser }) => isAdminUser },
  { to: '/admin/orders', label: 'Orders', icon: 'bi-receipt', visibleFor: ({ isAdminUser }) => isAdminUser },
  { to: '/admin/escrow', label: 'Escrow', icon: 'bi-shield-lock', visibleFor: ({ isAdminUser }) => isAdminUser },
  { to: '/admin/disputes', label: 'Disputes', icon: 'bi-chat-square-text', visibleFor: ({ isAdminUser }) => isAdminUser },
  { to: '/admin/returns', label: 'Returns', icon: 'bi-arrow-counterclockwise', visibleFor: ({ isAdminUser }) => isAdminUser },
  { to: '/admin/kyc', label: 'KYC Review', icon: 'bi-person-badge', visibleFor: ({ isAdminUser }) => isAdminUser },
  { to: '/admin/currencies', label: 'Currencies', icon: 'bi-currency-exchange', visibleFor: ({ isAdminUser }) => isAdminUser },
  { to: '/admin/regions', label: 'Regions', icon: 'bi-globe', visibleFor: ({ isAdminUser }) => isAdminUser },
  { to: '/admin/managers', label: 'Managers', icon: 'bi-people', visibleFor: ({ isAdminUser }) => isAdminUser },
  { to: '/admin/settings', label: 'Settings', icon: 'bi-gear', visibleFor: () => true },
];

/**
 * AdminLTE 4 sidebar. Uses the standard `.app-sidebar > .sidebar-brand + .sidebar-wrapper` markup.
 * Items are filtered by capability so managers see only what they can act on.
 */
export function AdminSidebar() {
  const { user } = useAppSelector((state) => state.auth);
  const isAdminUser =
    canManageManagers(user) && canManagePromotions(user) && canManageOrders(user);

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
            {navItems
              .filter((item) => item.visibleFor({ isAdminUser }))
              .map((item) => (
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
