import { createBrowserRouter } from 'react-router-dom';
import { Layout } from './components/layout';
import { HomePage } from './pages/Home/HomePage';
import { ProductsPage } from './pages/Products/ProductsPage';
import { ProductDetailPage } from './pages/ProductDetail/ProductDetailPage';
import { CartPage } from './pages/Cart/CartPage';
import { LoginPage } from './pages/Auth/LoginPage';
import { RegisterPage } from './pages/Auth/RegisterPage';
import { CheckoutPage } from './pages/Checkout/CheckoutPage';
import { CheckoutSuccessPage } from './pages/Checkout/CheckoutSuccessPage';
import { CheckoutCancelPage } from './pages/Checkout/CheckoutCancelPage';
import { MockPaymentPage } from './pages/Checkout/MockPaymentPage';
import { SearchPage } from './pages/Search/SearchPage';
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

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'products', element: <ProductsPage /> },
      { path: 'products/:id', element: <ProductDetailPage /> },
      { path: 'cart', element: <CartPage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'register', element: <RegisterPage /> },
      { path: 'checkout', element: <CheckoutPage /> },
      { path: 'checkout/success', element: <CheckoutSuccessPage /> },
      { path: 'checkout/cancel', element: <CheckoutCancelPage /> },
      { path: 'checkout/mock-pay', element: <MockPaymentPage /> },
      { path: 'search', element: <SearchPage /> },
    ],
  },
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
