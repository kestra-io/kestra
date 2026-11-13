import fs from "fs"
import {createRequire} from "module"

const require = createRequire(import.meta.url)

export function rapidocStandalone() {
    return {
        name: "rapidoc-standalone",
        apply: "build",
        generateBundle() {
            this.emitFile({
                type: "asset",
                fileName: "vendor/rapidoc-min.js",
                source: fs.readFileSync(require.resolve("rapidoc")),
            })
        },
    }
}
