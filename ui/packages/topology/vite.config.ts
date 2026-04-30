import vue from "@vitejs/plugin-vue"
import {defineConfig} from "vite"
import path from "path"

export default defineConfig({
    plugins: [vue()],
    css: {
        preprocessorOptions: {
            scss: {
                loadPaths: [path.resolve(__dirname, "../../node_modules")],
            },
        },
    },
})
