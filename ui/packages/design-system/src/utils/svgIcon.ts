import DOMPurify from "dompurify"

export interface SvgIcon {
    attrs: Record<string, string>;
    innerHtml: string;
}

const FALLBACK_SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" " +
    "xmlns:xlink=\"http://www.w3.org/1999/xlink\" aria-hidden=\"true\" " +
    "focusable=\"false\" width=\"0.75em\" height=\"1em\" style=\"-ms-transform: " +
    "rotate(360deg); -webkit-transform: rotate(360deg); transform: rotate(360deg);\" " +
    "preserveAspectRatio=\"xMidYMid meet\" viewBox=\"0 0 384 512\">" +
    "<path d=\"M288 32H0v448h384V128l-96-96zm64 416H32V64h224l96 96v288z\" fill=\"currentColor\"/>" +
    "</svg>"

// Attributes KsTaskIcon renders itself — never take these from the source icon markup.
const EXCLUDED_ATTRS = new Set(["class", "style", "role", "aria-hidden", "aria-label", "width", "height", "xmlns"])

// Icon strings are stable per plugin class, so sanitize/parse each unique icon once per session.
// This lives in a plain module (not a <script setup> top-level const) so the cache and the
// instance counter below are genuinely shared across every KsTaskIcon instance on the page —
// top-level bindings inside <script setup> run once per component instance, not once per module.
const sanitizedCache = new Map<string, SvgIcon>()

let instanceSeq = 0

/** One unique id per KsTaskIcon instance, used to namespace `id`s so two icons' <defs> never collide. */
export function nextInstanceId(): string {
    return `ks-task-icon-${instanceSeq++}`
}

function ensureViewBox(svg: string): string {
    const svgTagMatch = svg.match(/<svg\b[^>]*>/i)
    if (!svgTagMatch || /\sviewBox=/i.test(svgTagMatch[0])) {
        return svg
    }

    const widthMatch = svgTagMatch[0].match(/\swidth="([\d.]+)/i)
    const heightMatch = svgTagMatch[0].match(/\sheight="([\d.]+)/i)
    if (!widthMatch || !heightMatch) {
        return svg
    }

    const svgTagWithViewBox = svgTagMatch[0].replace(/<svg\b/i, `<svg viewBox="0 0 ${widthMatch[1]} ${heightMatch[1]}"`)
    return svg.replace(svgTagMatch[0], svgTagWithViewBox)
}

// Parses the sanitized markup once per unique icon so its root <svg> attributes (viewBox, …) can be
// bound directly onto the <svg> element KsTaskIcon renders — no wrapping <div> is needed.
function parseSanitized(rawBase64: string | undefined): SvgIcon {
    const cacheKey = rawBase64 ?? "__fallback__"
    const cached = sanitizedCache.get(cacheKey)
    if (cached !== undefined) {
        return cached
    }

    const rawSvg = rawBase64 ? window.atob(rawBase64) : FALLBACK_SVG
    const sanitized = DOMPurify.sanitize(ensureViewBox(rawSvg), {USE_PROFILES: {svg: true, svgFilters: true}})

    // Real-world plugin icons are rarely strictly well-formed XML (HTML entities like &nbsp;,
    // Illustrator/Inkscape cruft, etc.), so parse as HTML — the same forgiving grammar the browser
    // uses when this markup is later assigned via v-html — rather than as strict XML, which throws
    // out the whole icon on the first well-formedness violation.
    const parsed = new DOMParser().parseFromString(sanitized, "text/html")
    const svgEl = parsed.body.querySelector("svg")

    let result: SvgIcon = {attrs: {}, innerHtml: ""}
    if (svgEl) {
        const attrs: Record<string, string> = {}
        for (const attr of Array.from(svgEl.attributes)) {
            if (!EXCLUDED_ATTRS.has(attr.name)) {
                attrs[attr.name] = attr.value
            }
        }
        result = {attrs, innerHtml: svgEl.innerHTML}
    }

    sanitizedCache.set(cacheKey, result)
    return result
}

// Icons exported from design tools commonly reuse generic ids (e.g. "Layer_1", "gradient0") for
// <defs> — gradients, clipPaths, masks. Once inlined into the same document, two icons sharing an
// id can resolve to each other's def. A background-image data: URI never had this problem because
// each icon rendered in its own isolated resource; inlining means KsTaskIcon has to namespace ids
// itself. Only rewrite ids actually referenced by *this* icon, prefixed with a per-instance id.
function namespaceIds(source: SvgIcon, prefix: string): SvgIcon {
    const ids = new Set<string>()
    if (source.attrs.id) {
        ids.add(source.attrs.id)
    }
    for (const match of source.innerHtml.matchAll(/\sid="([^"]+)"/g)) {
        ids.add(match[1])
    }
    if (ids.size === 0) {
        return source
    }

    let innerHtml = source.innerHtml
    const attrs = {...source.attrs}
    for (const id of ids) {
        const namespaced = `${prefix}-${id}`
        innerHtml = innerHtml
            .replaceAll(`id="${id}"`, `id="${namespaced}"`)
            .replaceAll(`"#${id}"`, `"#${namespaced}"`)
            .replaceAll(`(#${id})`, `(#${namespaced})`)
        if (attrs.id === id) {
            attrs.id = namespaced
        }
    }

    return {attrs, innerHtml}
}

/** Sanitizes (cached per unique icon) and namespaces the ids of a base64-encoded plugin icon. */
export function getSvgIcon(rawBase64: string | undefined, instanceId: string): SvgIcon {
    return namespaceIds(parseSanitized(rawBase64), instanceId)
}
