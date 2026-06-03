import path from "node:path"
import {readdirSync} from "node:fs"

function findVueFiles(dir: string): string[] {
    const results: string[] = []
    for (const entry of readdirSync(dir, {withFileTypes: true})) {
        if (entry.isDirectory()) {
            results.push(...findVueFiles(path.join(dir, entry.name)))
        } else if (entry.name.endsWith(".vue")) {
            results.push(path.join(dir, entry.name))
        }
    }
    return results
}

const componentsDir = path.resolve(import.meta.dirname, "src/components")
export const componentEntries = Object.fromEntries(
    findVueFiles(componentsDir).map(file => {
        const key = path.relative(path.resolve(import.meta.dirname, "src"), file)
        return [key, "./" + path.relative(import.meta.dirname, file).replace(/\\/g, "/")]
    }),
)