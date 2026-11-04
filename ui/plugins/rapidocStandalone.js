import fs from "fs"

/**
 * Copy RapiDoc's self-contained ES module bundle to a stable path for the backend's standalone /api page (ApiController),
 * which cannot import the hashed chunk the in-app docs use.
 * @returns {import("vite").Plugin}
 */
export function rapidocStandalone() {
    return {
        name: "rapidoc-standalone",
        apply: "build",
        async generateBundle() {
            const resolved = await this.resolve("rapidoc")
            if (!resolved) {
                this.error("rapidoc cannot be resolved, so the /api page would render no API reference")
            }

            this.emitFile({
                type: "asset",
                fileName: "vendor/rapidoc-min.js",
                source: fs.readFileSync(resolved.id),
            })
        },
    }
}
