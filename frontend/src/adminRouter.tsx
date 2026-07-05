import { createBrowserRouter, Navigate } from 'react-router-dom';
import { AdminRoute } from './routes/AdminRoute';
import { AdminOnlyRoute } from './routes/AdminOnlyRoute';
import { AdminLayout } from './components/admin/layout/AdminLayout';
import { AdminLoginPage } from './pages/admin/AdminLoginPage';
import { AdminDashboardPage } from './pages/admin/AdminDashboardPage';
import { AdminCategoriesPage } from './pages/admin/AdminCategoriesPage';
import { AdminProductsPage } from './pages/admin/AdminProductsPage';
import { AdminDiscountsPage } from './pages/admin/AdminDiscountsPage';
import { AdminDiscountTemplatesPage } from './pages/admin/AdminDiscountTemplatesPage';
import { AdminCouponsPage } from './pages/admin/AdminCouponsPage';
import { AdminOrdersPage } from './pages/admin/AdminOrdersPage';
import { AdminOrderDetailPage } from './pages/admin/AdminOrderDetailPage';
import { AdminNewOrderPage } from './pages/admin/AdminNewOrderPage';
import { AdminManagersPage } from './pages/admin/AdminManagersPage';
import { AdminCurrenciesPage } from './pages/admin/AdminCurrenciesPage';
import { AdminRegionsPage } from './pages/admin/AdminRegionsPage';
import { AdminSettingsPage } from './pages/admin/AdminSettingsPage';
import { AdminEscrowPage } from './pages/admin/AdminEscrowPage';
import { AdminDisputesPage } from './pages/admin/AdminDisputesPage';
import { AdminDisputeDetailPage } from './pages/admin/AdminDisputeDetailPage';
import { AdminReturnsPage } from './pages/admin/AdminReturnsPage';
import { AdminKycPage } from './pages/admin/AdminKycPage';
import { AdminKycDetailPage } from './pages/admin/AdminKycDetailPage';

/**
 * ADMIN router - the entry point of the independent admin dashboard application
 * (admin.html + admin-main.tsx, served on its own port, e.g. 5174). It contains
 * ONLY the admin console; none of the storefront routes are reachable here, and
 * this router is not bundled into the storefront app. Paths keep their /admin/*
 * prefix; the app root redirects to /admin/login.
 */
export const adminRouter = createBrowserRouter([
  { path: '/', element: <Navigate to="/admin/login" replace /> },
  // Admin login is OUTSIDE the AdminRoute guard so unauthenticated users can reach it.
  { path: '/admin/login', element: <AdminLoginPage /> },
  {
    path: '/admin',
    element: <AdminRoute />,
    children: [
      {
        element: <AdminLayout />,
        children: [
          // Staff-shared pages (ADMIN or MANAGER):
          { index: true, element: <AdminDashboardPage /> },
          { path: 'categories', element: <AdminCategoriesPage /> },
          { path: 'products', element: <AdminProductsPage /> },
          { path: 'settings', element: <AdminSettingsPage /> },
          // Admin-only pages: nested under AdminOnlyRoute. Managers reaching
          // these paths are bounced to /admin (the dashboard).
          {
            element: <AdminOnlyRoute />,
            children: [
              { path: 'discounts', element: <AdminDiscountsPage /> },
              { path: 'discount-templates', element: <AdminDiscountTemplatesPage /> },
              { path: 'coupons', element: <AdminCouponsPage /> },
              { path: 'orders', element: <AdminOrdersPage /> },
              { path: 'orders/new', element: <AdminNewOrderPage /> },
              { path: 'orders/:id', element: <AdminOrderDetailPage /> },
              { path: 'escrow', element: <AdminEscrowPage /> },
              { path: 'disputes', element: <AdminDisputesPage /> },
              { path: 'disputes/:id', element: <AdminDisputeDetailPage /> },
              { path: 'returns', element: <AdminReturnsPage /> },
              { path: 'kyc', element: <AdminKycPage /> },
              { path: 'kyc/:id', element: <AdminKycDetailPage /> },
              { path: 'managers', element: <AdminManagersPage /> },
              { path: 'currencies', element: <AdminCurrenciesPage /> },
              { path: 'regions', element: <AdminRegionsPage /> },
            ],
          },
        ],
      },
    ],
  },
]);
