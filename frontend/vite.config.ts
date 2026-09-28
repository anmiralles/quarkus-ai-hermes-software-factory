import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// ADR-003: the frontend is same-origin with the API only when it is served through
// this proxy. Both the dev server (5173) and the preview server (4173) forward
// /coffees to the backend, so src/api/client.ts can use relative paths.
const DEFAULT_BACKEND_URL = 'http://localhost:8080'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', 'VITE_')
  const backendUrl = env.VITE_BACKEND_URL || DEFAULT_BACKEND_URL

  const proxy = {
    '/coffees': {
      target: backendUrl,
      changeOrigin: true,
    },
  }

  return {
    plugins: [react()],
    server: {
      port: 5173,
      strictPort: true,
      proxy,
    },
    preview: {
      port: 4173,
      strictPort: true,
      proxy,
    },
    test: {
      environment: 'jsdom',
      globals: true,
      css: false,
      setupFiles: ['./src/test/setup.ts'],
      environmentOptions: {
        jsdom: { url: 'http://localhost:3000/' },
      },
    },
  }
})
