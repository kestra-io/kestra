import { defineConfig } from "vite";
import { builtinModules } from "node:module";

// ensure Node-builtins stay external
const externals = [...builtinModules, ...builtinModules.map((m) => `node:${m}`)];

export default defineConfig({
    build: {
        target: "node18",
        outDir: "dist",
        emptyOutDir: true,
        lib: {
            entry: "src/kestra-devtools-cli.ts",
            formats: ["cjs"],
            fileName: () => "kestra-devtools-cli.cjs",
        },
        rollupOptions: {
            external: externals,
            output: {
                // Make the output an executable CLI
                banner: "#!/usr/bin/env node",
            },
        },
    },
});
