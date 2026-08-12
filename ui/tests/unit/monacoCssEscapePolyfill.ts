// monaco-editor 0.56 sanitizes class names/URLs via the native CSS.escape(), which jsdom doesn't implement.
// Polyfill per the CSSOM spec: https://drafts.csswg.org/cssom/#serialize-an-identifier
function cssEscape(value: string): string {
    const string = String(value)
    const length = string.length
    const firstCodeUnit = string.charCodeAt(0)
    if (length === 1 && firstCodeUnit === 0x002d) {
        return `\\${string}`
    }
    let result = ""
    for (let index = 0; index < length; index++) {
        const codeUnit = string.charCodeAt(index)
        if (codeUnit === 0x0000) {
            result += "�"
        } else if (
            (codeUnit >= 0x0001 && codeUnit <= 0x001f) || codeUnit === 0x007f ||
            (index === 0 && codeUnit >= 0x0030 && codeUnit <= 0x0039) ||
            (index === 1 && codeUnit >= 0x0030 && codeUnit <= 0x0039 && firstCodeUnit === 0x002d)
        ) {
            result += `\\${codeUnit.toString(16)} `
        } else if (
            codeUnit >= 0x0080 || codeUnit === 0x002d || codeUnit === 0x005f ||
            (codeUnit >= 0x0030 && codeUnit <= 0x0039) ||
            (codeUnit >= 0x0041 && codeUnit <= 0x005a) ||
            (codeUnit >= 0x0061 && codeUnit <= 0x007a)
        ) {
            result += string.charAt(index)
        } else {
            result += `\\${string.charAt(index)}`
        }
    }
    return result
}

export function installMonacoCssEscapePolyfill(): void {
    if (typeof globalThis.CSS === "undefined") {
        (globalThis as any).CSS = {}
    }
    if (typeof globalThis.CSS.escape !== "function") {
        (globalThis as any).CSS.escape = cssEscape
    }
}
