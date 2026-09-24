import type {DataChip} from "./types"
import {escapePebbleLiteral} from "./pebbleExpression"

const READ_ONLY_EXTENSIONS = new Set([
    "sql", "py", "js", "mjs", "ts", "sh", "bash", "groovy", "java", "kt", "rb", "go", "php",
    "html", "htm", "css", "scss", "less", "ini", "cfg", "conf", "properties", "log",
    "graphql", "gql", "sparql", "md", "markdown", "yaml", "yml",
])

const FILE_URI_ONLY_EXTENSIONS = new Set([
    "json", "csv", "tsv", "parquet", "avro", "orc", "xlsx", "xls", "docx", "pptx",
    "zip", "gz", "tgz", "tar", "pdf", "png", "jpg", "jpeg", "gif", "svg", "webp",
    "bin", "dat", "db", "sqlite", "ion",
])

function extensionOf(path: string): string {
    const base = path.split("/").pop() ?? path
    const dot = base.lastIndexOf(".")
    return dot > 0 ? base.slice(dot + 1).toLowerCase() : ""
}

export function namespaceFileChips(path: string): DataChip[] {
    const displayPath = path.replace(/^\/+/, "")
    const extension = extensionOf(displayPath)
    const offerRead = !FILE_URI_ONLY_EXTENSIONS.has(extension)
    const offerFileUri = !READ_ONLY_EXTENSIONS.has(extension)
    const escapedPath = escapePebbleLiteral(displayPath)

    const chips: DataChip[] = []
    if (offerRead) chips.push({label: `read('${displayPath}')`, expr: `{{ read('${escapedPath}') }}`})
    if (offerFileUri) chips.push({label: `fileURI('${displayPath}')`, expr: `{{ fileURI('${escapedPath}') }}`})
    return chips
}
