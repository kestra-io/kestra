/// <reference types="vitest/config" />
import vue from "@vitejs/plugin-vue"
import dts from "vite-plugin-dts"
import {defineConfig} from "vite"

export default defineConfig({
  plugins: [
    vue(),
    dts({
        tsconfigPath: "./tsconfig.json", 
        exclude: ["tests/**"]
    })
  ],
})
