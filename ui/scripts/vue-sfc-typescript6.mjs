import {createRequire} from "node:module"
import {pathToFileURL} from "node:url"

// compiler-sfc resolves imported prop types with the TS 6 API (ts.sys, resolveModuleName), which TS 7.1 does not export.
const loadTypeScript6 = () => createRequire(import.meta.url)("@typescript/typescript6")

for (const from of new Set([import.meta.url, pathToFileURL(`${process.cwd()}/`).href])) {
    try {
        createRequire(from)("vue/compiler-sfc").registerTS(loadTypeScript6)
    } catch {}
}
