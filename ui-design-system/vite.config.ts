import {defineConfig} from "vite"
import vue from "@vitejs/plugin-vue"
import dts from "vite-plugin-dts"
import {resolve} from "path"

export default defineConfig({
    plugins: [vue(), dts({tsconfigPath: "./tsconfig.json", exclude: ["tests/**"]})],
    build: {
        lib: {
            entry: resolve(__dirname, "src/index.ts"),
            name: "KstraUiDesignSystem",
            formats: ["es", "cjs"],
            fileName: (format) => `index.${format === "es" ? "js" : "cjs"}`,
        },
        rollupOptions: {
            // Externalize peer deps – they must be provided by the host app
            external: ["vue", /^vue\//, /^element-plus/],
            output: {
                globals: {
                    vue: "Vue",
                    "element-plus": "ElementPlus",
                },
                // Preserve named exports for tree-shaking
                exports: "named",
            },
        },
        // Generate sourcemaps for easier debugging
        sourcemap: true,
    },
})
