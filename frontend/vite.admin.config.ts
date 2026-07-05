import { defineConfig, type Plugin, type Connect } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'

/**
 * ADMIN dashboard dev/build config - an INDEPENDENT application on its own port
 * (5174), separate from the storefront (5173). Because it is a distinct server
 * process serving only admin.html + admin-main.tsx, the storefront app cannot
 * reach or import the admin console. Combined with backend token-audience and
 * CORS restrictions on /api/admin/**, the admin surface is origin-isolated.
 *
 * Run: `npm run dev:admin` (dev) / `npm run build:admin` (prod bundle).
 */

// SPA history-fallback to admin.html: serve the admin entry for any HTML
// navigation (e.g. /admin/login, /admin/kyc) in both dev and preview. Asset and
// module requests (accept != text/html) pass straight through.
function adminSpaFallback(): Plugin {
  const rewrite: Connect.NextHandleFunction = (req, _res, next) => {
    if (req.method === 'GET' && req.headers.accept?.includes('text/html')) {
      req.url = '/admin.html'
    }
    next()
  }
  return {
    name: 'admin-spa-fallback',
    configureServer(server) {
      server.middlewares.use(rewrite)
    },
    configurePreviewServer(server) {
      server.middlewares.use(rewrite)
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react({
      babel: {
        plugins: ['babel-plugin-react-compiler'],
      },
    }),
    adminSpaFallback(),
  ],
  server: {
    port: 5174,
    strictPort: true,
  },
  preview: {
    port: 5174,
    strictPort: true,
  },
  build: {
    outDir: 'dist-admin',
    rollupOptions: {
      input: fileURLToPath(new URL('./admin.html', import.meta.url)),
    },
  },
  css: {
    preprocessorOptions: {
      scss: {
        quietDeps: true,
      },
    },
  },
})
