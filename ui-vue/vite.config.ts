import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5174,
    allowedHosts: ['.monkeycode-ai.online'],
    proxy: {
      '/api': {
        target: 'http://localhost:6039',
        changeOrigin: true,
        secure: false
      },
      '/auth': {
        target: 'http://localhost:6039',
        changeOrigin: true,
        secure: false
      },
      '/admin': {
        target: 'http://localhost:6039',
        changeOrigin: true,
        secure: false
      }
    }
  },
  resolve: {
    alias: {
      '@': '/src'
    }
  }
})
