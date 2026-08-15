import {defineConfig} from "vitest/config"
import vue from "@vitejs/plugin-vue"

import {designSystemAutoImport} from "./vite/autoImport.mjs"

export default defineConfig({
    plugins: [vue(), designSystemAutoImport()],
    test: {
        environment: "jsdom",
        globals: true,
        include: ["tests/**/*.test.ts"],
        setupFiles: ["./tests/units/setup.ts"],
    },
})
