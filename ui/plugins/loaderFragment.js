import fs from "fs"
import path from "path"
import {fileURLToPath} from "url"

const __dirname = path.dirname(fileURLToPath(import.meta.url))

/**
 * Injects the boot-loader markup (the animated SVG + its wrapper divs) into index.html
 * from a single canonical source file, so OSS and EE don't hand-duplicate the same SVG.
 * ui-ee imports this same plugin via the "kestra" cross-repo path (see its vite.config.js).
 * @returns {import("vite").Plugin}
 */
export function loaderFragment() {
    return {
        name: "loader-fragment",
        transformIndexHtml(html) {
            const fragment = fs.readFileSync(path.resolve(__dirname, "../loader-fragment.html"), "utf-8")
            return html.replace("<!-- LOADER_FRAGMENT -->", fragment)
        },
    }
}
