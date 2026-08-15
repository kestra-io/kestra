import {defineStore} from "pinia"
import {useStorage} from "@vueuse/core"

const LOCAL_STORAGE_KEY = "starred.bookmarks"

interface Page {    
    path: string;    
    label?: string;
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
            p.path === page.path ? {...p, label: page.label} : p)
    }

    function updateAll(newPages: Array<Page>) {
        pages.value = [...newPages]
    }
    return {
        pages,
        add,
        remove,
        rename,
        updateAll,
    }
})
