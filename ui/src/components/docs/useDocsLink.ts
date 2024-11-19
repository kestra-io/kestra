import path from "path-browserify";
import {computed, Ref} from "vue";
import {useStore} from "vuex";

/**
 * converts markdown code links path into
 * vue-router usable route paths
 * @returns normalized path (not a url)
 */
function normalizeDocsPath(inputPath:string)  {
    return inputPath.replaceAll(/(\/|^)\d+?\.(?!\d)/g, "$1").replace(/(?:\/index)?\.md(#.+|$)/, "");
}

/**
 * checks if a link is targeting something outside of the the docs
 * @returns cleaned href
 */
function isRemoteLink(href:string) {
    return href.startsWith("/") || /https?:\/\/.*/.test(href)
}

/**
 * When an href is remote and starts with /, it will target the original website.
 * This function adds kestra.io
 * @returns normalized href
 */
function normalizeRemoteHref(href) {
    return href.startsWith("/") ? "https://kestra.io" + href : href
}

export function useDocsLink(hrefInput: Ref<string>, currentPath: Ref<string>) {
    const store = useStore();

    const pageMetadata = computed(() => store.getters["doc/pageMetadata"]);
    const isRemote = computed(() => isRemoteLink(hrefInput.value));
    const href = computed(() => {
        if(isRemote.value) {
            return normalizeRemoteHref(hrefInput.value)
        }
        let relativeLink = normalizeDocsPath(hrefInput.value);
        if (pageMetadata.value.isIndex === false) {
            relativeLink = "../" + relativeLink;
        }
        return path.normalize(currentPath.value + "/" + relativeLink);
    });

    return {
        href,
        isRemote
    }
}