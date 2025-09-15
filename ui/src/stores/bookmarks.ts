import {defineStore} from "pinia"

const LOCAL_STORAGE_KEY = "starred.bookmarks"

const initialPages = localStorage.getItem(LOCAL_STORAGE_KEY) ?? "[]"
interface Page {
    path: string;
    label?: string;
}

interface State {
    pages: Array<Page>;
}

export const useBookmarksStore = defineStore("bookmarks", {
    state: (): State => ({
        pages: JSON.parse(initialPages),
    }),

    actions: {
        add(page: Page ) {
            const pages = this.pages
            if (!pages.find(p => p.path === page.path)) {
                pages.push(page)
                this.updateAll(pages)
            }
        },
        remove(page: Page) {
            const pages = this.pages
            const index = pages.findIndex(p => p.path === page.path)
            if (index > -1) {
                pages.splice(index, 1)
                this.updateAll(pages)
            }
        },
        rename(page: Page) {
            const pages = this.pages
            const index = pages.findIndex(p => p.path === page.path)
            if (index > -1) {
                pages.splice(index, 1, {
                    ...pages[index],
                    label: page.label
                })
                this.updateAll(pages)
            }

        },
        updateAll(pages: Array<Page>) {
            this.pages = pages
            localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(pages))
        }
    },
})