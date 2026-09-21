import {defineStore} from "pinia"
import {useStorage} from "@vueuse/core"

const LOCAL_STORAGE_KEY = "starred.bookmarks"

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
    const pages = useStorage<Page[]>(LOCAL_STORAGE_KEY, [])

    function add(page: Page) {
        if (!pages.value.find(p => p.path === page.path)) {
            // Stamped as derived so `refreshLabel` may re-derive it: without the flag it would be
            // indistinguishable from a pre-existing entry, which is deliberately left alone.
            pages.value = [...pages.value, {custom: false, ...page}]
        }
    }

    function remove(page: Page) {
        pages.value = pages.value.filter(p => p.path !== page.path)
    }

    function rename(page: Page) {
        pages.value = pages.value.map(p => {
            // Confirming the editor without changing anything must not freeze the label's
            // language: only a label the user actually altered counts as theirs.
            if (p.path !== page.path || p.label === page.label) return p

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
        pages.value = pages.value.map(p =>
            p.path === page.path && p.custom === false && p.label !== page.label
                ? {...p, label: page.label}
                : p,
        )
    }

    function updateAll(newPages: Array<Page>) {
        pages.value = [...newPages]
    }

    return {
        pages,
        add,
        remove,
        rename,
        refreshLabel,
        updateAll,
    }
})
