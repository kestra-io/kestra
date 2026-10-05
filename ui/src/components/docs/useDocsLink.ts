import path from "path-browserify"
import {computed, Ref} from "vue"
import {useDocStore} from "../../stores/doc"

const KESTRA_DOCS_PREFIX = /^(?:https?:\/\/(?:www\.)?kestra\.io)?\/docs(?:\/|(?=[#?]|$))/

function isKestraDocsLink(href: string) {
    return KESTRA_DOCS_PREFIX.test(href)
}

function toIntegratedDocsPath(href: string, currentPath: string) {
    const rest = href
        .replace(KESTRA_DOCS_PREFIX, "")
        .replace(/[#?].*$/, "")
        .replace(/\/$/, "")
    const docsRoot = currentPath.replace(/(^|\/)docs(\/.*)?$/, "$1docs")
    return path.normalize(docsRoot + "/" + rest).replace(/\/$/, "")
}

/**
 * converts markdown code links path into
 * vue-router usable route paths
 * @returns normalized path (not a url)
 */
function normalizeDocsPath(inputPath:string)  {
    return inputPath.replaceAll(/(\/|^)\d+?\.(?!\d)/g, "$1").replace(/(?:\/index)?\.md(#.+|$)/, "")
}

/**
 * checks if a link is targeting something outside of the the docs
 * @returns cleaned href
 */
function isRemoteLink(href:string) {
    if (isKestraDocsLink(href)) return false
    return href.startsWith("/") || /https?:\/\/.*/.test(href)
}

/**
 * When an href is remote and starts with /, it will target the original website.
 * This function adds kestra.io
 * @returns normalized href
 */
function normalizeRemoteHref(href: string) {
    return href.startsWith("/") ? "https://kestra.io" + href  + "?utm_source=app&utm_medium=referral&utm_campaign=embed-docs" : href
}

export function useDocsLink(hrefInput: Ref<string>, currentPath: Ref<string>) {
    const docStore = useDocStore()

    const pageMetadata = computed(() => docStore.pageMetadata)
    const isRemote = computed(() => isRemoteLink(hrefInput.value))
    const href = computed(() => {
        if(isRemote.value) {
            return normalizeRemoteHref(hrefInput.value)
        }
        if (isKestraDocsLink(hrefInput.value)) {
            return toIntegratedDocsPath(hrefInput.value, currentPath.value)
        }
        let relativeLink = normalizeDocsPath(hrefInput.value)
        if (pageMetadata.value?.isIndex === false) {
            relativeLink = "../" + relativeLink
        }
        return path.normalize(currentPath.value + "/" + relativeLink)
    })

    return {
        href,
        isRemote,
    }
}
