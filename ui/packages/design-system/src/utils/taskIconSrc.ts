const FALLBACK_SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" " +
    "xmlns:xlink=\"http://www.w3.org/1999/xlink\" aria-hidden=\"true\" " +
    "focusable=\"false\" width=\"0.75em\" height=\"1em\" style=\"-ms-transform: " +
    "rotate(360deg); -webkit-transform: rotate(360deg); transform: rotate(360deg);\" " +
    "preserveAspectRatio=\"xMidYMid meet\" viewBox=\"0 0 384 512\">" +
    "<path d=\"M288 32H0v448h384V128l-96-96zm64 416H32V64h224l96 96v288z\" fill=\"currentColor\"/>" +
    "</svg>"

// atob/btoa operate on Latin1 "binary strings" — a raw byte can't round-trip through them directly
// once it decodes to a multi-byte UTF-8 character (e.g. non-ASCII text in a plugin author's name
// inside an icon's <title>/comment). Route through TextEncoder/TextDecoder instead.
function decodeBase64Utf8(base64: string): string {
    const bytes = Uint8Array.from(atob(base64), c => c.charCodeAt(0))
    return new TextDecoder().decode(bytes)
}

function encodeUtf8Base64(text: string): string {
    const bytes = new TextEncoder().encode(text)
    let binary = ""
    for (const byte of bytes) {
        binary += String.fromCharCode(byte)
    }
    return btoa(binary)
}

// An <img src="data:..."> renders in an isolated image document: scripts never execute, it can't
// reach the host page, and colliding ids across icons are structurally impossible since each
// instance gets its own separate document. That sandboxing is why no sanitization step is needed
// here, unlike inlining the SVG into the page via v-html. The trade-off is the one this component
// always had: that isolation also means the image has no access to the host page's CSS, so
// `currentColor` must be baked into the SVG source itself rather than resolved via the cascade.
//
// Icon strings are stable per plugin class, so cache the built data: URI per (icon, resolved color)
// pair — this is a plain module (not a <script setup> top-level const) so the cache is genuinely
// shared across every KsTaskIcon instance on the page, not reset per component instance.
const srcCache = new Map<string, string>()

export function getTaskIconSrc(rawBase64: string | undefined, color: string): string {
    const cacheKey = `${rawBase64 ?? "__fallback__"}::${color}`
    const cached = srcCache.get(cacheKey)
    if (cached !== undefined) {
        return cached
    }

    const rawSvg = rawBase64 ? decodeBase64Utf8(rawBase64) : FALLBACK_SVG
    const coloredSvg = rawSvg.replaceAll("currentColor", color)
    const src = `data:image/svg+xml;base64,${encodeUtf8Base64(coloredSvg)}`

    srcCache.set(cacheKey, src)
    return src
}
