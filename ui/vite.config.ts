import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { viteConfig } from '@halo-dev/ui-plugin-bundler-kit'

export default viteConfig({
  vite: {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    build: {
      outDir: 'build/dist',
    },
  },
})

