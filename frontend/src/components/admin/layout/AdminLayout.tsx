import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { AdminNavbar } from './AdminNavbar';
import { AdminSidebar } from './AdminSidebar';

/**
 * AdminLTE 4 layout shell. Markup follows the AdminLTE convention:
 *   .app-wrapper > .app-header + .app-sidebar + .app-main + .app-footer
 *
 * The sidebar collapse is handled with a body class toggle so we don't need
 * AdminLTE's bundled JS - React owns interactions, AdminLTE owns the styling.
 */
export function AdminLayout() {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <div className={`app-wrapper ${collapsed ? 'sidebar-collapse' : ''}`}>
      <AdminNavbar onToggleSidebar={() => setCollapsed((c) => !c)} />
      <AdminSidebar />
      <main className="app-main">
        <div className="app-content py-3">
          <div className="container-fluid">
            <Outlet />
          </div>
        </div>
      </main>
      <footer className="app-footer">
        <div className="float-end d-none d-sm-inline">Admin Console</div>
        <strong>&copy; {new Date().getFullYear()} E-Commerce Platform.</strong> All rights reserved.
      </footer>
    </div>
  );
}
