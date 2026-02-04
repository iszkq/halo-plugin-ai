import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { defineHaloUiPluginConfig } from '@halo-dev/ui-plugin-bundler-kit'

export default defineConfig(
  defineHaloUiPluginConfig({
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    build: {
      lib: {
        entry: fileURLToPath(new URL('./src/index.ts', import.meta.url)),
        formats: ['es'],
        fileName: () => 'index.js',
      },
      rollupOptions: {
        external: ['vue', '@halo-dev/components', '@halo-dev/console-shared'],
      },
    },
  })
)

