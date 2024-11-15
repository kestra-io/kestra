/**
 * converts markdown code links path into
 * vue-router usable route paths
 * @returns normalized path (not a url)
 */
export function normalizeDocsPath(inputPath:string)  {
    return inputPath.replaceAll(/(\/|^)\d+?\.(?!\d)/g, "$1").replace(/(?:\/index)?\.md(#.+|$)/, "");
}

/**
 * checks if a link is targeting something outside of the the docs
 * @returns cleaned href
 */
export function isRemoteLink(href:string) {
    return href.startsWith("/") || /https?:\/\/.*/.test(href)
}

/**
 * When an href is remote and starts with /, it will target the original website.
 * This function adds kestra.io
 * @returns normalized href
 */
export function normalizeRemoteHref(href) {
    return href.startsWith("/") ? "https://kestra.io" + href : href
}