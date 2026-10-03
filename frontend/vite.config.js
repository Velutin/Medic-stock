import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// In development the API is called through /api on the same origin as the page, so the
// session cookie (HttpOnly, SameSite=Strict) works without CORS. VITE_API_TARGET overrides the backend URL.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const target = env.VITE_API_TARGET || 'http://127.0.0.1:8085';
  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, ''),
        },
      },
    },
  };
});
