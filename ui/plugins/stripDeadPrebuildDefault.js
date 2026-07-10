/**
 * Strip dead `.default` branches from module-federation virtual prebuild shims.
 *
 * Module-federation's virtual prebuild shims always emit:
 *   `export default __mfPrebuildNamespace.default ?? __mfPrebuildNamespace`
 *
 * This is safe when a source module has a real default export. But when it doesn't,
 * the `.default` branch is statically undefined and Rolldown warns
 * (IMPORT_IS_UNDEFINED). Drop the dead branch in that case only; leave real defaults untouched.
 * @returns {import("rolldown").Plugin}
 */
export function stripDeadPrebuildDefault() {
    const SHIM = "export default __mfPrebuildNamespace.default ?? __mfPrebuildNamespace"

    return {
        name: "strip-dead-prebuild-default",
        apply: "build",
        // Run after @module-federation/vite has produced the shim's source.
        // @ts-expect-error: `enforce` is not in the type, but it works.
        enforce: "post",
        async transform(code, id) {
            if (!id.includes("virtual:mf:") || !id.includes("__prebuild__") || !code.includes(SHIM)) {
                return
            }

            const importMatch = code.match(/import \* as __mfPrebuildNamespace from "((?:[^"\\]|\\.)*)"/)
            if (!importMatch) return
            const importSource = JSON.parse(`"${importMatch[1]}"`)

            const resolved = await this.resolve(importSource, id, {skipSelf: true})
            if (!resolved) return

            try {
                const info = await this.load(resolved)
                // Only optimize if exports is available (build mode with Rollup/Rolldown)
                if (info.exports?.includes("default")) return
            } catch {
                // In dev mode or if exports is not supported, skip optimization
                return
            }

            return {
                code: code.replace(SHIM, "export default __mfPrebuildNamespace"),
                map: null,
            }
        },
    }
}
