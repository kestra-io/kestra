import {defineStore} from "pinia"
import {ref} from "vue"

const LOCAL_STORAGE_KEY = "starred.bookmarks"

const initialPages = localStorage.getItem(LOCAL_STORAGE_KEY) ?? "[]"
interface Page {
    path: string;
    label?: string;
}

export const useBookmarksStore = defineStore("bookmarks", () => {
    const pages = ref<Page[]>(JSON.parse(initialPages))

    function add(page: Page ) {
        const currentPages = pages.value
        if (!currentPages.find(p => p.path === page.path)) {
            currentPages.push(page)
            updateAll(currentPages)
        }
    }
    function remove(page: Page) {
        const currentPages = pages.value
        const index = currentPages.findIndex(p => p.path === page.path)
        if (index > -1) {
            currentPages.splice(index, 1)
            updateAll(currentPages)
        }
    }
    function rename(page: Page) {
        const currentPages = pages.value
        const index = currentPages.findIndex(p => p.path === page.path)
        if (index > -1) {
            currentPages.splice(index, 1, {
                ...currentPages[index],
                label: page.label,
            })
            updateAll(currentPages)
        }

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
