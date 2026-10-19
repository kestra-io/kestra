import { defineConfig } from "vitest/config";
import vue from "@vitejs/plugin-vue";

// Runs the story `play` functions in jsdom through portable stories (tests/unit/stories.spec.ts),
// next to the plain parsing unit tests. Deliberately does not extend the module federation vite.config.ts.
export default defineConfig({
    plugins: [vue()],
    test: {
        environment: "jsdom",
        include: ["tests/**/*.spec.ts"],
        setupFiles: ["tests/unit/setup.ts"],
        server: { deps: { inline: [/@kestra-io\/design-system/, /element-plus/] } },
    },
});