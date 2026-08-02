import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config'

// Kept separate from vite.config.ts so `vite dev` / `vite build` never load
// test-only config. We merge the real Vite config in (rather than re-declaring
// the `@` -> src alias by hand) so the two can never drift apart.
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      // Order matters — polyfills.ts repairs `localStorage` before setup.ts's
      // import graph loads any store that persists to it. See polyfills.ts.
      setupFiles: ['./src/test/polyfills.ts', './src/test/setup.ts'],
      globals: true,
      // Tests assert behaviour, not styling; skipping CSS keeps runs fast and
      // avoids pulling Tailwind through the transform pipeline per test file.
      css: false,
      include: ['src/**/*.{test,spec}.{ts,tsx}'],
    },
  }),
)
