// @ts-check

import {promises as fs} from "fs"
import path from "path"
import os from "os"
import zlib from "zlib"
import {promisify} from "util"

const gzip = promisify(zlib.gzip)
const brotli = promisify(zlib.brotliCompress)

// Content-addressed text-ish outputs worth precompressing. Fonts (woff/woff2) and
// raster images are already compressed; index.html is rewritten at runtime by the
// webserver (UiIndexService) so a precompressed variant of it would go stale.
const COMPRESSIBLE = /\.(js|mjs|css|svg|json|txt|map|webmanifest|html)$/
const MIN_SIZE = 1024
// Only keep a variant that meaningfully shrinks the file, mirroring the webserver's
// former on-the-fly threshold; disk in the jar is cheap but not free.
const MAX_RATIO = 0.9

/**
 * Emits `<file>.gz` and `<file>.br` siblings for every compressible build output, so
 * the webserver can serve precompressed bytes as-is (`Content-Encoding` negotiation)
 * without ever compressing on the request path.
 * @returns {import("vite").Plugin}
 */
export const precompressAssets = () => {
    /** @type {string} */
    let outDir

    return {
        name: "kestra-precompress-assets",
        apply: "build",
        configResolved(config) {
            outDir = path.resolve(config.root, config.build.outDir)
        },
        async closeBundle() {
            /** @type {string[]} */
            const targets = []
            /** @param {string} dir */
            const walk = async (dir) => {
                for (const entry of await fs.readdir(dir, {withFileTypes: true})) {
                    const full = path.join(dir, entry.name)
                    if (entry.isDirectory()) await walk(full)
                    else if (COMPRESSIBLE.test(entry.name)
                        && entry.name !== "index.html"
                        && !entry.name.endsWith(".gz")
                        && !entry.name.endsWith(".br")) targets.push(full)
                }
            }
            await walk(outDir)

            let emitted = 0
            let raw = 0
            let compressed = 0
            const pool = Math.max(2, os.cpus().length - 1)
            /** @param {string} file */
            const compressOne = async (file) => {
                const content = await fs.readFile(file)
                if (content.length < MIN_SIZE) return
                const bound = content.length * MAX_RATIO
                const [gz, br] = await Promise.all([
                    gzip(content, {level: zlib.constants.Z_BEST_COMPRESSION}),
                    brotli(content, {
                        params: {
                            [zlib.constants.BROTLI_PARAM_QUALITY]: 11,
                            [zlib.constants.BROTLI_PARAM_SIZE_HINT]: content.length,
                        },
                    }),
                ])
                if (gz.length <= bound) {
                    await fs.writeFile(`${file}.gz`, gz)
                    emitted++
                    raw += content.length
                    compressed += gz.length
                }
                if (br.length <= bound) await fs.writeFile(`${file}.br`, br)
            }
            for (let i = 0; i < targets.length; i += pool) {
                await Promise.all(targets.slice(i, i + pool).map(compressOne))
            }
            this.info(`precompressed ${emitted}/${targets.length} files (${(raw / 1e6).toFixed(1)}MB -> ${(compressed / 1e6).toFixed(1)}MB gzip)`)
        },
    }
}
