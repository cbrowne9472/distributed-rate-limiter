import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const API = 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/check':     { target: API, changeOrigin: true },
      '/rules':     { target: API, changeOrigin: true },
      '/stats':     { target: API, changeOrigin: true },
      '/actuator':  { target: API, changeOrigin: true },
      '/dashboard': { target: API, changeOrigin: true },
    },
  },
})
