import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// STOREFRONT dev/build config. Serves index.html (main.tsx) on port 5173.
// The admin dashboard is a SEPARATE app with its own config (vite.admin.config.ts)
// on port 5174 - see `npm run dev:admin`. The default build input is index.html,
// so admin.html is never bundled into the storefront output.
// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react({
      babel: {
        plugins: ['babel-plugin-react-compiler'],
      },
    }),
  ],
  server: {
    port: 5173,
    strictPort: true,
  },
  preview: {
    port: 5173,
    strictPort: true,
  },
  css: {
    preprocessorOptions: {
      scss: {
        quietDeps: true,
      },
    },
  },
})
