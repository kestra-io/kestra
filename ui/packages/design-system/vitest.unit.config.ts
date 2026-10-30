import {defineConfig} from "vitest/config"
import vue from "@vitejs/plugin-vue"

/** Node 26's own `localStorage` shadows jsdom's and stays undefined, so let jsdom provide storage. */
process.env.NODE_OPTIONS = `${process.env.NODE_OPTIONS ?? ""} --no-experimental-webstorage`.trim()

export default defineConfig({
    plugins: [vue()],
    test: {
        environment: "jsdom",
        globals: true,
        include: ["src/**/*.test.ts"],
        setupFiles: ["./tests/units/setup.ts"],
        coverage: {
            reporter: ["text", "html", "lcov"],
            include: ["src/**/*.{ts,vue}"],
            exclude: ["src/**/*.{test,stories}.{ts,tsx}"],
        },
    },
})
