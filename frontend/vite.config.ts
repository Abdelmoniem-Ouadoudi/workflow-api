import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // No proxy on purpose. Talking to the API cross-origin is what exercises the CORS
    // configuration; a dev proxy would hide it until deployment.
    strictPort: true,
  },
})
