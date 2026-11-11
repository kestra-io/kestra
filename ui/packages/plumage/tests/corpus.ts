import {existsSync, readdirSync, readFileSync, statSync} from "node:fs"
import {join, relative} from "node:path"
import {fileURLToPath} from "node:url"

export interface CorpusFile {
    path: string
    code: string
}

const FIXTURES = fileURLToPath(new URL("./fixtures/", import.meta.url))
const REPO_FLOWS = fileURLToPath(new URL("../../../../core/src/test/resources/flows/", import.meta.url))

function walk(dir: string, accept: (name: string) => boolean): string[] {
    return readdirSync(dir).sort().flatMap(name => {
        const path = join(dir, name)
        return statSync(path).isDirectory() ? walk(path, accept) : accept(name) ? [path] : []
    })
}

function load(root: string, accept: (name: string) => boolean): CorpusFile[] {
    return walk(root, accept).map(path => ({
        path: relative(root, path),
        code: readFileSync(path, "utf8").replace(/\r\n/g, "\n"),
    }))
}

/**
 * The YAML fixtures and the flows of the backend's own tests, read where they are, plus every `.yaml`/`.yml` under
 * `KESTRA_CORPUS` when that variable points at a folder.
 */
export function yamlCorpus(): CorpusFile[] {
    const isYaml = (name: string) => /\.ya?ml$/.test(name)
    const flows = existsSync(REPO_FLOWS) ? load(REPO_FLOWS, isYaml).map(file => ({...file, path: `flows/${file.path}`})) : []
    const extra = process.env.KESTRA_CORPUS ? load(process.env.KESTRA_CORPUS, isYaml) : []
    return [...load(join(FIXTURES, "yaml"), isYaml), ...flows, ...extra]
}

/** Real docs code fences, grouped by Shiki language id (the folder name). */
export function languageCorpus(): Map<string, CorpusFile[]> {
    const root = join(FIXTURES, "languages")
    return new Map(readdirSync(root).sort().map(lang => [lang, load(join(root, lang), () => true)]))
}
