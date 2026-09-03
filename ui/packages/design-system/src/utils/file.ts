import type {Component} from "vue"
import CodeJson from "vue-material-design-icons/CodeJson.vue"
import FileCodeOutline from "vue-material-design-icons/FileCodeOutline.vue"
import FileDelimitedOutline from "vue-material-design-icons/FileDelimitedOutline.vue"
import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue"
import FileExcelOutline from "vue-material-design-icons/FileExcelOutline.vue"
import FileImageOutline from "vue-material-design-icons/FileImageOutline.vue"
import FileMusicOutline from "vue-material-design-icons/FileMusicOutline.vue"
import FileOutline from "vue-material-design-icons/FileOutline.vue"
import FilePdfBox from "vue-material-design-icons/FilePdfBox.vue"
import FilePowerpointOutline from "vue-material-design-icons/FilePowerpointOutline.vue"
import FileTableOutline from "vue-material-design-icons/FileTableOutline.vue"
import FileVideoOutline from "vue-material-design-icons/FileVideoOutline.vue"
import FileWordOutline from "vue-material-design-icons/FileWordOutline.vue"
import FolderZipOutline from "vue-material-design-icons/FolderZipOutline.vue"
import LanguageMarkdownOutline from "vue-material-design-icons/LanguageMarkdownOutline.vue"

const FILE_URI_PREFIXES = ["kestra:///", "file://", "nsfile://"]

const ICONS_BY_GROUP: [Component, string[]][] = [
    [FileImageOutline, ["png", "jpg", "jpeg", "gif", "bmp", "svg", "webp", "avif", "ico", "tif", "tiff"]],
    [FileVideoOutline, ["mp4", "mov", "avi", "mkv", "webm", "mpg", "mpeg"]],
    [FileMusicOutline, ["mp3", "wav", "flac", "ogg", "m4a", "aac"]],
    [FolderZipOutline, ["zip", "tar", "gz", "tgz", "bz2", "xz", "7z", "rar"]],
    [FileDelimitedOutline, ["csv", "tsv", "psv"]],
    [FileTableOutline, ["ion", "parquet", "avro", "orc"]],
    [FileExcelOutline, ["xls", "xlsx", "xlsm", "ods"]],
    [FileWordOutline, ["doc", "docx", "odt", "rtf"]],
    [FilePowerpointOutline, ["ppt", "pptx", "odp"]],
    [FilePdfBox, ["pdf"]],
    [LanguageMarkdownOutline, ["md", "markdown"]],
    [CodeJson, ["json", "jsonl", "ndjson", "yaml", "yml", "xml", "toml", "ini", "properties", "conf", "env"]],
    [FileCodeOutline, ["js", "mjs", "cjs", "ts", "jsx", "tsx", "vue", "html", "htm", "css", "scss", "py", "java", "kt", "rb", "go", "rs", "c", "cpp", "h", "cs", "php", "sh", "bash", "zsh", "ps1", "sql", "r", "scala", "swift", "pl"]],
    [FileDocumentOutline, ["txt", "log", "out", "err"]],
]

const ICONS_BY_EXTENSION: Record<string, Component> = Object.fromEntries(
    ICONS_BY_GROUP.flatMap(([icon, extensions]) => extensions.map((extension) => [extension, icon])),
)

/** Matches the internal-storage URI schemes a task output, an input or a log attachment can carry. */
export function isFileUri(value: unknown): value is string {
    return typeof value === "string" && FILE_URI_PREFIXES.some((prefix) => value.startsWith(prefix))
}

/** Last path segment of a URI, without its query string or fragment. */
export function fileName(uri: string): string {
    const segment = uri.split(/[?#]/)[0].split("/").filter(Boolean).pop() ?? ""
    try {
        return decodeURIComponent(segment)
    } catch {
        return segment
    }
}

/** Lowercased extension of a URI or file name, empty when it carries none. */
export function fileExtension(uri: string): string {
    const name = fileName(uri)
    const dot = name.lastIndexOf(".")
    return dot > 0 ? name.slice(dot + 1).toLowerCase() : ""
}

/** Icon for a URI or file name, falling back to a generic file for unknown extensions. */
export function fileIcon(uri: string): Component {
    return ICONS_BY_EXTENSION[fileExtension(uri)] ?? FileOutline
}
