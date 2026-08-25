import {defineStore} from "pinia"
import {useStorage} from "@vueuse/core"

const LOCAL_STORAGE_KEY = "starred.bookmarks"

interface Page {
    path: string;
    label?: string;
    /**
     * True once the user has typed their own label, which then has to survive a language
     * change. Absent on entries stored before this existed, which are treated as derived.
     */
    custom?: boolean;
}

export const useBookmarksStore = defineStore("bookmarks", () => {
    const pages = useStorage<Page[]>(LOCAL_STORAGE_KEY, [])

    function add(page: Page) {
        if (!pages.value.find(p => p.path === page.path)) {
            pages.value = [...pages.value, page]
        }
    }

    function remove(page: Page) {
        pages.value = pages.value.filter(p => p.path !== page.path)
    }

    function rename(page: Page) {
        pages.value = pages.value.map(p =>
            p.path === page.path ? {...p, label: page.label, custom: true} : p,
        )
    }

    /**
     * Re-derives a bookmark's label from the page it points at. Labels are stored as resolved
     * text — they are composed from a translated title and breadcrumb — so a bookmark otherwise
     * keeps the language it was created in forever. A label the user typed is left alone.
     */
    function refreshLabel(page: Page) {
        pages.value = pages.value.map(p =>
            p.path === page.path && !p.custom && p.label !== page.label
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
