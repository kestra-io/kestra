/// <reference types="vitest/config" />
import vue from "@vitejs/plugin-vue"
import "../../scripts/vue-sfc-typescript6.mjs"
import {defineConfig} from "vite"

export default defineConfig({
  plugins: [
    vue(),
  ],
})
