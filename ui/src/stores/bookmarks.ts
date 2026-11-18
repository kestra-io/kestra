import {defineStore} from "pinia"
import {useStorage} from "@vueuse/core"
import {hasInjectionContext} from "vue"
import {useRouter, type Router} from "vue-router"

const LOCAL_STORAGE_KEY = "starred.bookmarks"

function canonicalizePath(router: Router | undefined, path: string) {
    const resolved = router?.resolve(path)

    if (resolved?.path === undefined || resolved.query === undefined || resolved.hash === undefined) {
        return path
    }

    return router?.resolve({
        path: resolved.path,
        query: resolved.query,
        hash: resolved.hash,
    }).fullPath ?? path
}

interface Page {
    path: string;
    label?: string;
    /**
     * Whether the label is the user's own words (`true`) or derived from the page (`false`).
     * Absent means an entry stored before this existed, whose provenance is unknown: it may
     * well have been renamed by hand, so it is never re-derived either.
     */
    custom?: boolean;
}

export const useBookmarksStore = defineStore("bookmarks", () => {
    const router = hasInjectionContext() ? useRouter() : undefined
    const pages = useStorage<Page[]>(LOCAL_STORAGE_KEY, [])

    function normalizePages(newPages: Page[]) {
        return newPages.reduce<Page[]>((acc, page) => {
            const normalizedPage = {...page, path: canonicalizePath(router, page.path)}
            const existingIndex = acc.findIndex(p => p.path === normalizedPage.path)

            if (existingIndex === -1) {
                acc.push(normalizedPage)
                return acc
            }

            if (acc[existingIndex].custom !== true && normalizedPage.custom === true) {
                acc[existingIndex] = normalizedPage
            }

            return acc
        }, [])
    }

    pages.value = normalizePages(pages.value)

    function add(page: Page) {
        const normalizedPage = {...page, path: canonicalizePath(router, page.path)}

        if (!isBookmarked(normalizedPage.path)) {
            // Stamped as derived so `refreshLabel` may re-derive it: without the flag it would be
            // indistinguishable from a pre-existing entry, which is deliberately left alone.
            pages.value = [...pages.value, {custom: false, ...normalizedPage}]
        }
    }

    function remove(page: Page) {
        const path = canonicalizePath(router, page.path)
        pages.value = pages.value.filter(p => p.path !== path)
    }

    function rename(page: Page) {
        const path = canonicalizePath(router, page.path)

        pages.value = pages.value.map(p => {
            // Confirming the editor without changing anything must not freeze the label's
            // language: only a label the user actually altered counts as theirs.
            if (p.path !== path || p.label === page.label) return p

            return {...p, label: page.label, custom: true}
        })
    }

    /**
     * Re-derives a bookmark's label from the page it points at. Labels are stored as resolved
     * text — they are composed from a translated title and breadcrumb — so a bookmark otherwise
     * keeps the language it was created in forever. Only a label this store derived itself is
     * re-derived: one the user typed, and one from before the flag existed, are left alone.
     */
    function refreshLabel(page: Page) {
        const path = canonicalizePath(router, page.path)

        pages.value = pages.value.map(p =>
            p.path === path && p.custom === false && p.label !== page.label
                ? {...p, label: page.label}
                : p,
        )
    }

    function updateAll(newPages: Array<Page>) {
        pages.value = normalizePages(newPages)
    }

    function isBookmarked(path: string) {
        const normalizedPath = canonicalizePath(router, path)
        return pages.value.some(page => page.path === normalizedPath)
    }

    return {
        pages,
        add,
        remove,
        rename,
        refreshLabel,
        updateAll,
        isBookmarked,
    }
})
