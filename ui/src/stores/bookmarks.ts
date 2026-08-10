import {defineStore} from "pinia"
import {ref} from "vue"

const LOCAL_STORAGE_KEY = "starred.bookmarks"

interface Page {
    path: string;
    label?: string;
}

function readStoredPages(): Page[] {
    try {
        const raw = localStorage.getItem(LOCAL_STORAGE_KEY)
        if (raw == null) {
            return []
        }
        const parsed = JSON.parse(raw)
        return Array.isArray(parsed) ? parsed as Page[] : []
    } catch {
        return []
    }
}

export const useBookmarksStore = defineStore("bookmarks", () => {
    const pages = ref<Page[]>(readStoredPages())

    // Keep in-memory state in sync when another tab updates localStorage.
    if (typeof window !== "undefined") {
        window.addEventListener("storage", (event) => {
            if (event.key === LOCAL_STORAGE_KEY || event.key === null) {
                pages.value = readStoredPages()
            }
        })
    }

    function add(page: Page) {
        // Always merge against the latest persisted list so concurrent tabs
        // don't overwrite each other's bookmarks.
        const currentPages = readStoredPages()
        if (!currentPages.find(p => p.path === page.path)) {
            currentPages.push(page)
            updateAll(currentPages)
            return
        }
        pages.value = currentPages
    }

    function remove(page: Page) {
        const currentPages = readStoredPages()
        const index = currentPages.findIndex(p => p.path === page.path)
        if (index > -1) {
            currentPages.splice(index, 1)
            updateAll(currentPages)
            return
        }
        pages.value = currentPages
    }

    function rename(page: Page) {
        const currentPages = readStoredPages()
        const index = currentPages.findIndex(p => p.path === page.path)
        if (index > -1) {
            currentPages.splice(index, 1, {
                ...currentPages[index],
                label: page.label,
            })
            updateAll(currentPages)
            return
        }
        pages.value = currentPages
    }

    function updateAll(newPages: Array<Page>) {
        pages.value = newPages
        localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(newPages))
    }

    return {
        pages,
        add,
        remove,
        rename,
        updateAll,
    }
})
