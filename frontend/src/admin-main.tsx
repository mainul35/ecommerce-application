import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Provider } from 'react-redux';
import { RouterProvider } from 'react-router-dom';
import { store } from './store';
import { adminRouter } from './adminRouter';
import { AuthBootstrap } from './routes/AuthBootstrap';
import { CurrencyProvider } from './storefront/CurrencyContext';
import './assets/scss/main.scss';
import 'bootstrap-icons/font/bootstrap-icons.css';
import 'admin-lte/dist/css/adminlte.min.css';
// Bootstrap's interactive components (dropdowns, modals, offcanvas, collapse)
// need its JS bundle. The .bundle.min.js variant ships Popper.js inline,
// which dropdowns and tooltips depend on.
import 'bootstrap/dist/js/bootstrap.bundle.min.js';

// Entry point of the INDEPENDENT admin dashboard application. Mounts only the
// admin router - the storefront app and its routes are not part of this bundle.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Provider store={store}>
      <AuthBootstrap>
        <CurrencyProvider>
          <RouterProvider router={adminRouter} />
        </CurrencyProvider>
      </AuthBootstrap>
    </Provider>
  </StrictMode>
);
