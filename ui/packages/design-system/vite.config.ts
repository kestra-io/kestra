/// <reference types="vitest/config" />
import vue from "@vitejs/plugin-vue"
import {defineConfig} from "vite"

import {designSystemAutoImport} from "./vite/autoImport.mjs"

export default defineConfig({
  plugins: [
    vue(),
    // Components use each other by tag name; nothing registers them globally any more.
    designSystemAutoImport(),
  ],
})
