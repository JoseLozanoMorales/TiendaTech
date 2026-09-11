import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config'

export default mergeConfig(viteConfig, defineConfig({
  // Probar las fuentes TypeScript, no sus copias JavaScript generadas.
  resolve: { extensions: ['.ts', '.tsx', '.mjs', '.js', '.jsx', '.json'] },
  test: {
    environment: 'jsdom',
    setupFiles: ['./tests/setup.ts'],
    include: ['tests/**/*.test.ts?(x)'],
    restoreMocks: true,
    mockReset: true,
    coverage: {
      provider: 'v8',
      include: ['src/views/CartView.tsx', 'src/views/CheckoutView.tsx', 'src/views/AdminView.tsx'],
      reporter: ['text', 'html', 'lcov', 'json-summary'],
      reportsDirectory: './coverage',
    },
  },
}))
