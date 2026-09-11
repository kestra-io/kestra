import {computed} from "vue"
import moment from "moment"
import {copyToClipboard, fileUtils} from "@kestra-io/design-system"
import {useMiscStore} from "override/stores/misc"

export type Optional<T, K extends keyof T> = Omit<T, K> & Partial<Pick<T, K>>;

export function uid() {
    return String.fromCharCode(Math.floor(Math.random() * 26) + 97) +
        Math.random().toString(16).slice(2) +
        Date.now().toString(16).slice(4)
}

/** Checks whether a value is a supported file URI. */
export function isFile(value: unknown): boolean {
    return fileUtils.isFileUri(value)
}

/**
 * Returns `true` when the value is an Ion internal-storage file (i.e. passes {@link isFile}
 * and the URI ends with a `.ion` extension, case-insensitive).
 *
 * @param value Value to validate.
 * @returns `true` if the value is an Ion file URI.
 */
export function isIon(value: unknown): boolean {
    return isFile(value) && typeof value === "string" && value.toLowerCase().endsWith(".ion")
}

export function flatten(object: Record<string, any>) {
    const result: Record<string, any> = {}

    // Accumulate into one object: the previous `concat(...keys.map())` and
    // `Object.assign({}, ...leaves)` spread one argument per key, which threw RangeError
    // above ~100k leaves and left the outputs table unrenderable (kestra-io/kestra#19316).
    function _flatten(child: Record<string, any> | null, path: string[]): void {
        if (child === null) {
            result[path.join(".")] = null
            return
        }

        const keys = Object.keys(child)

        // An empty container has no leaves, so recursing dropped the key entirely. The `path`
        // guard keeps a top-level `{}` flattening to `{}` rather than gaining a blank key.
        if (path.length > 0 && keys.length === 0) {
            result[path.join(".")] = child
            return
        }

        for (const key of keys) {
            if (typeof child[key] === "object") {
                _flatten(child[key], path.concat([key]))
            } else {
                result[path.concat([key]).join(".")] = child[key]
            }
        }
    }

    _flatten(object, [])
    return result
}

export function executionVars(data: Record<string, any>) {
    if (data === undefined) {
        return []
    }

    const flat = flatten(data)

    return Object.keys(flat).map(key => {
        const rawValue = flat[key]
        if (key === "variables.executionId") {
            return {key, value: rawValue, subflow: true}
        }

        if (typeof rawValue === "string" && rawValue.match(/\d{4}-\d{2}-\d{2}/)) {
            const date = moment(rawValue, moment.ISO_8601)
            if (date.isValid()) {
                return {key, value: rawValue, date: true}
            }
        }

        if (typeof rawValue === "number") {
            return {key, value: number(rawValue)}
        }

        return {key, value: rawValue}

    })
}

const DISPLAY_MAX_CHARS = 256 * 1024
const DISPLAY_MAX_LINES = 200
// Monaco costs per line, so one pathological line is as slow as a whole large document:
// a 2.5 MiB string value pretty-prints to a single line and blocked for ~1 s under the other caps.
export const DISPLAY_MAX_LINE_CHARS = 2000

/**
 * Clip text to what a value viewer can render without wedging the main thread: a few MiB of
 * output values blocked it for seconds (kestra-io/kestra#19316). Compare lengths to detect a clip.
 */
export function capForDisplay(text: string): string {
    const capped = text.slice(0, DISPLAY_MAX_CHARS)

    let cut = -1
    for (let line = 0; line < DISPLAY_MAX_LINES; line++) {
        const next = capped.indexOf("\n", cut + 1)
        if (next === -1) {
            return clipLines(capped)
        }
        cut = next
    }
    return clipLines(capped.slice(0, cut))
}

function clipLines(text: string): string {
    if (text.length <= DISPLAY_MAX_LINE_CHARS) {
        return text
    }
    return text
        .split("\n")
        .map(line => line.length > DISPLAY_MAX_LINE_CHARS ? line.slice(0, DISPLAY_MAX_LINE_CHARS) : line)
        .join("\n")
}

export const PREVIEW_MAX_ENTRIES = 100
export const PREVIEW_MAX_NODES = 1000
export const PREVIEW_MAX_CHARS = 32 * 1024
export const PREVIEW_MAX_STRING_CHARS = 500

// What a scalar and an entry's punctuation and indent cost, charged against the character budget.
const SCALAR_PREVIEW_CHARS = 8
const ENTRY_PREVIEW_CHARS = 4
const INDENT_PREVIEW_CHARS = 2

export interface BoundedValue {
    value: unknown;
    truncated: boolean;
}

/**
 * Shrink a parsed value to a preview that stays valid JSON: clipping the serialized text instead
 * cuts mid-token and Monaco then reports the preview as a syntax error (kestra-io/kestra#19316).
 * Omitted entries are named by an `…` marker carrying how many were dropped.
 */
export function boundForDisplay(value: unknown): BoundedValue {
    let nodes = PREVIEW_MAX_NODES
    let chars = PREVIEW_MAX_CHARS
    let truncated = false

    // Keys count too: one long enough key is the single-line document Monaco chokes on.
    function clip(text: string): string {
        const kept = text.length <= PREVIEW_MAX_STRING_CHARS
            ? text
            : `${text.slice(0, PREVIEW_MAX_STRING_CHARS)}…`
        if (kept !== text) {
            truncated = true
        }
        chars -= kept.length
        return kept
    }

    function hasRoom(taken: number, total: number): boolean {
        return taken < total && taken < PREVIEW_MAX_ENTRIES && nodes > 0 && chars > 0
    }

    // An entry costs its own punctuation plus the indent its depth earns it, which is what stops a
    // deeply nested value: the indent alone is megabytes long before any leaf is reached.
    function entryCost(depth: number): number {
        return ENTRY_PREVIEW_CHARS + depth * INDENT_PREVIEW_CHARS
    }

    function bound(node: unknown, depth: number): unknown {
        if (typeof node === "string") {
            return clip(node)
        }

        if (node === null || typeof node !== "object") {
            chars -= SCALAR_PREVIEW_CHARS
            return node
        }

        // The container's own closing line is indented too, which is half the cost at depth.
        chars -= depth * INDENT_PREVIEW_CHARS

        if (Array.isArray(node)) {
            const bounded: unknown[] = []
            while (hasRoom(bounded.length, node.length)) {
                nodes--
                chars -= entryCost(depth)
                bounded.push(bound(node[bounded.length], depth + 1))
            }
            if (bounded.length < node.length) {
                truncated = true
                bounded.push(`… ${node.length - bounded.length}`)
            }
            return bounded
        }

        const keys = Object.keys(node)
        const bounded: Record<string, unknown> = {}
        let index = 0
        let shown = 0
        while (index < keys.length && hasRoom(shown, keys.length)) {
            nodes--
            chars -= entryCost(depth)
            const key = keys[index]
            index++
            const clipped = clip(key)
            // Two keys clipped to the same text would have the second overwrite the first, showing
            // fewer entries than the value has; drop it instead so the `…` count stays honest.
            if (clipped in bounded) {
                continue
            }
            bounded[clipped] = bound((node as Record<string, unknown>)[key], depth + 1)
            shown++
        }
        if (shown < keys.length) {
            truncated = true
            bounded["…"] = keys.length - shown
        }
        return bounded
    }

    return {value: bound(value, 1), truncated}
}

/** Size of `text` on the wire: a character count understates a multi-byte value. */
export function humanTextSize(text: string): string {
    return humanFileSize(new TextEncoder().encode(text).length)
}

/**
 * Format bytes as human-readable text.
 *
 * @param bytes Number of bytes.
 * @param si True to use metric (SI) units, aka powers of 1000. False to use
 *           binary (IEC), aka powers of 1024.
 * @param dp Number of decimal places to display.
 *
 * @return Formatted string.
 */
export function humanFileSize(bytes: number, si = false, dp = 1) {
    if (bytes === undefined) {
        // when the size is 0 it arrives as undefined here!
        return "0B"
    }
    const thresh = si ? 1000 : 1024

    if (Math.abs(bytes) < thresh) {
        return bytes + " B"
    }

    const units = si ?
        ["kB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"] :
        ["KiB", "MiB", "GiB", "TiB", "PiB", "EiB", "ZiB", "YiB"]
    let u = -1
    const r = 10 ** dp

    do {
        bytes /= thresh
        ++u
    } while (Math.round(Math.abs(bytes) * r) / r >= thresh && u < units.length - 1)


    return bytes.toFixed(dp) + " " + units[u]
}

export function number(n: number) {
    return n.toString().replace(/(\d)(?=(\d{3})+(?!\d))/g, "$1 ")
}

export function hexToRgba(hex: string, opacity: number) {
    let c: any
    if (/^#([A-Fa-f0-9]{3}){1,2}$/.test(hex)) {
        c = hex.substring(1).split("")
        if (c.length === 3) {
            c = [c[0], c[0], c[1], c[1], c[2], c[2]]
        }
        c = "0x" + c.join("")
        return "rgba(" + [(c >> 16) & 255, (c >> 8) & 255, c & 255].join(",") + "," + (opacity || 1) + ")"
    }
    throw new Error("Bad Hex")
}

/** Offer text as a file, so a value too large to render whole is still obtainable in full. */
export function downloadText(text: string, filename: string): void {
    const type = filename.endsWith(".json") ? "application/json" : "text/plain"
    const url = window.URL.createObjectURL(new Blob([text], {type}))
    downloadUrl(url, filename)
    // Revoking in the same tick as the click cancels the download in some browsers.
    setTimeout(() => window.URL.revokeObjectURL(url), 0)
}

export function downloadUrl(url: string, filename: string) {
    const link = document.createElement("a")
    link.href = url
    link.setAttribute("download", filename)
    link.setAttribute("target", "_blank")
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
}

/**
 * Extracts a filename from an HTTP `Content-Disposition` header.
 *
 * @param header  the header value
 */
export function extractFileNameFromContentDisposition(header: string | null | undefined): string | null {
    if (!header) return null

    const filenameRegex = /filename\*=UTF-8''(.+)|filename="(.+?)"|filename=(.+)/
    const matches = header.match(filenameRegex)

    // Check for UTF-8 encoded filename first
    if (matches && matches[1]) {
        return decodeURIComponent(matches[1])
    }
    // Fallback to quoted or unquoted filename
    if (matches && matches[2]) {
        return matches[2]
    }
    if (matches && matches[3]) {
        return matches[3]
    }

    return null // Return null if no filename is found
}

export function switchTheme(miscStore: {theme: SelectedTheme}, theme?: SelectedTheme) {
    // default theme
    if (theme === undefined) {
        if (localStorage.getItem("theme")) {
            theme = localStorage.getItem("theme") as SelectedTheme
        } else if (window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches) {
            theme = "dark"
        } else {
            theme = "light"
        }
    }

    const disableTransitions = document.createElement("style")
    disableTransitions.appendChild(document.createTextNode("*,*::before,*::after{transition:none !important}"))
    document.head.appendChild(disableTransitions)

    // class name
    const htmlClass = document.getElementsByTagName("html")[0].classList

    const themeClasses = ["dark", "light", "syncWithSystem", "dark-2"]
    function removeClasses() {
        themeClasses.forEach((cls) => htmlClass.remove(cls))
    }
    removeClasses()

    if (theme === "syncWithSystem") {
        const systemTheme = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light"
        htmlClass.add(theme, systemTheme)
    }
    else if (theme === "dark-2") {
        htmlClass.add("dark", "dark-2")
    }
    else {
        htmlClass.add(theme)
    }

    miscStore.theme = theme

    localStorage.setItem("theme", theme)

    void document.body.offsetHeight
    requestAnimationFrame(() => disableTransitions.remove())
}

export type SelectedTheme = "syncWithSystem" | "dark" | "dark-2" | "light"

export function getSelectedTheme(): SelectedTheme {
    return (localStorage.getItem("theme") as SelectedTheme | null) ?? "syncWithSystem"
}

export function getTheme(): "light" | "dark" {
    let theme = getSelectedTheme()

    if (theme === "syncWithSystem") {
        return window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light"
    }

    return theme === "light" ? "light" : "dark"
}

export function getLang() {
    return localStorage.getItem("lang") || "en"
}

/**
 * The stored language as a valid BCP 47 tag ("pt_BR" -> "pt-BR") for Intl APIs and the html lang
 * attribute, which reject the underscore form getLang() returns.
 */
export function getLanguageTag() {
    return getLang().replace(/_/g, "-")
}

export function splitFirst(str: string, separator: string) {
    return str.split(separator).slice(1).join(separator)
}

export function asArray(objOrArray: any | any[]) {
    if (objOrArray === undefined) {
        return []
    }

    return Array.isArray(objOrArray) ? objOrArray : [objOrArray]
}

export async function copy(text: string) {
    await copyToClipboard(text)
}

export function toFormData(obj: FormData | Record<string, any>) {
    if (!(obj instanceof FormData)) {
        const formData = new FormData()
        for (const key in obj) {
            formData.append(key, obj[key])
        }
        return formData
    }
    return obj
}

export interface DateGrouping {
    format: string;
    unit: "month" | "week" | "day" | "hour" | "minute";
}

export function getDateGrouping(startDate: moment.MomentInput, endDate: moment.MomentInput, timeRange: string | undefined): DateGrouping {
    if ((!startDate || !endDate) && timeRange === undefined) {
        return {format: "yyyy-MM-DD", unit: "day"}
    }

    const duration = timeRange === undefined
        ? moment.duration(moment(endDate).diff(moment(startDate)))
        : moment.duration(timeRange)

    if (duration.asDays() > 365) {
        return {format: "yyyy-MM", unit: "month"}
    } else if (duration.asDays() > 180) {
        return {format: "yyyy-'W'ww", unit: "week"}
    } else if (duration.asDays() > 1) {
        return {format: "yyyy-MM-DD", unit: "day"}
    } else if (duration.asHours() > 1) {
        return {format: "yyyy-MM-DD HH:00", unit: "hour"}
    } else {
        return {format: "yyyy-MM-DD HH:mm", unit: "minute"}
    }
}

export function getParentNamespaces(namespace: string): string[] {
    if (!namespace) return []

    const parts = namespace.split(".")
    const parents: string[] = []

    for (let i = 1; i <= parts.length; i++) {
        parents.push(parts.slice(0, i).join("."))
    }

    return parents
}

export const useTheme = () => {
    const miscStore = useMiscStore()
    return computed<"light" | "dark">(() => {
        void miscStore.theme
        return getTheme()
    })
}

export function resolve$ref(fullSchema: Record<string, any>, obj: Record<string, any>) {
    if (obj === undefined || obj === null) {
        return obj
    }
    if (obj.$ref) {
        return getValueAtJsonPath(fullSchema, obj.$ref)
    }
    return obj
}

export function getValueAtJsonPath(fullSchema: Record<string, any>, path: string): any {
    if (!fullSchema || !path || typeof path !== "string") {
        return undefined
    }

    const keys = path.replace(/^#\//, "").split("/")
    let current = fullSchema

    for (const key of keys) {
        if (current && key in current) {
            current = resolve$ref(fullSchema, current[key])
        } else {
            return undefined
        }
    }

    return current
}

export function deepEqual(x: any, y: any): boolean {
    const ok = Object.keys, tx = typeof x, ty = typeof y
    return x && y && tx === "object" && tx === ty ? (
        ok(x).length === ok(y).length &&
        ok(x).every(key => deepEqual(x[key], y[key]))
    ) : (x === y)
}
