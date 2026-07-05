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
import {
  OrdersPage,
  OrderDetailPage,
  DisputesPage,
  DisputeDetailPage,
  WalletPage,
} from './pages/Account';
import { SellerVerificationPage } from './pages/Kyc';
import { VerifyAccountPage, VerifyEmailPage } from './pages/Verify';

/**
 * STOREFRONT router. Admin routes deliberately live in a SEPARATE application
 * ({@link ./adminRouter} + admin.html, served on its own port) so the admin
 * dashboard is not reachable from - or bundled into - the storefront. Requesting
 * /admin/* here resolves to the storefront 404, not the admin console.
 */
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
      // Buyer account: orders with escrow/buyer-protection, disputes, wallet.
      // No route guard needed - the APIs are authenticated and the axios
      // interceptor bounces 401s to /login.
      { path: 'account/orders', element: <OrdersPage /> },
      { path: 'account/orders/:id', element: <OrderDetailPage /> },
      { path: 'account/disputes', element: <DisputesPage /> },
      { path: 'account/disputes/:id', element: <DisputeDetailPage /> },
      { path: 'account/wallet', element: <WalletPage /> },
      // Seller e-KYC wizard: register as a seller / verify identity.
      { path: 'sell', element: <SellerVerificationPage /> },
      // Mandatory account verification (email + phone).
      { path: 'verify', element: <VerifyAccountPage /> },
      { path: 'verify-email', element: <VerifyEmailPage /> },
    ],
  },
]);
