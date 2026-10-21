import {describe, expect, it, beforeEach, afterEach} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {createApp, type App} from "vue"
import {createMemoryHistory, createRouter} from "vue-router"

import {useBookmarksStore} from "./bookmarks"

const STORAGE_KEY = "starred.bookmarks"
const decodedFilterPath = "/main/flows/edit/tutorial/getting-started-elt-pipeline/overview?filters[timeRange][EQUALS]=PT24H"
const encodedFilterPath = "/main/flows/edit/tutorial/getting-started-elt-pipeline/overview?filters%5BtimeRange%5D%5BEQUALS%5D=PT24H"
const spaceEncodedPath = "/main/flows/edit/tutorial/getting-started-elt-pipeline/overview?q=a%20b"
const spaceCanonicalPath = "/main/flows/edit/tutorial/getting-started-elt-pipeline/overview?q=a+b"

describe("bookmarks store", () => {
    let app: App | undefined

    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
    })

    afterEach(() => {
        app?.unmount()
        app = undefined
        localStorage.clear()
    })

    function storeWithRouter() {
        const pinia = createPinia()
        const router = createRouter({
            history: createMemoryHistory(),
            routes: [{path: "/:pathMatch(.*)*", component: {}}],
        })

        app = createApp({})
        app.use(router)
        app.use(pinia)
        setActivePinia(pinia)

        return app.runWithContext(() => useBookmarksStore())
    }

    it("should refresh a derived label when the page name changed language", () => {
        const store = useBookmarksStore()
        store.add({path: "/flows", label: "Flows: Ausführungen"})

        store.refreshLabel({path: "/flows", label: "Flows: Executions"})

        expect(store.pages[0].label).toBe("Flows: Executions")
    })

    // A bookmark the user renamed has to survive a language change, which is the whole reason
    // the flag exists rather than the label just being overwritten on every visit.
    it("should leave a label the user typed alone", () => {
        const store = useBookmarksStore()
        store.add({path: "/flows", label: "Flows: Executions"})
        store.rename({path: "/flows", label: "My flows"})

        store.refreshLabel({path: "/flows", label: "Flows: Ausführungen"})

        expect(store.pages[0].label).toBe("My flows")
        expect(store.pages[0].custom).toBe(true)
    })

    it("should mark a renamed bookmark as custom", () => {
        const store = useBookmarksStore()
        store.add({path: "/flows", label: "Flows"})

        expect(store.pages[0].custom).toBe(false)
        store.rename({path: "/flows", label: "Mine"})
        expect(store.pages[0].custom).toBe(true)
    })

    // Entries persisted before the flag existed carry no `custom`, so there is no way to tell one
    // the user renamed from one this store derived. Re-deriving them would silently rename a
    // bookmark someone named themselves, so an unflagged entry keeps whatever label it holds.
    it("should leave a bookmark stored before the custom flag existed alone", () => {
        localStorage.setItem(STORAGE_KEY, JSON.stringify([{path: "/flows", label: "Mes flows"}]))
        const store = useBookmarksStore()

        store.refreshLabel({path: "/flows", label: "Flows: Executions"})

        expect(store.pages[0].label).toBe("Mes flows")
    })

    it("should not touch other bookmarks", () => {
        const store = useBookmarksStore()
        store.add({path: "/flows", label: "Flows"})
        store.add({path: "/executions", label: "Ausführungen"})

        store.refreshLabel({path: "/executions", label: "Executions"})

        expect(store.pages.map(p => p.label)).toEqual(["Flows", "Executions"])
    })

    // Element identity only — the array is reassigned either way, and `useStorage` is what
    // skips the unchanged write.
    it("should keep the entry's object identity when the label is unchanged", () => {
        const store = useBookmarksStore()
        store.add({path: "/flows", label: "Flows"})
        const before = store.pages[0]

        store.refreshLabel({path: "/flows", label: "Flows"})

        expect(store.pages[0]).toBe(before)
    })

    // Confirming the inline editor without typing must not freeze the label's language.
    it("should not mark a bookmark custom when the rename changes nothing", () => {
        const store = useBookmarksStore()
        store.add({path: "/flows", label: "Flows"})

        store.rename({path: "/flows", label: "Flows"})

        expect(store.pages[0].custom).toBe(false)
        store.refreshLabel({path: "/flows", label: "Ablaeufe"})
        expect(store.pages[0].label).toBe("Ablaeufe")
    })

    it("should match encoded and decoded query filter paths as the same bookmark", () => {
        const store = storeWithRouter()

        store.add({path: decodedFilterPath, label: "Overview"})
        store.add({path: encodedFilterPath, label: "Overview"})

        expect(store.pages).toEqual([{path: decodedFilterPath, label: "Overview", custom: false}])
        expect(store.isBookmarked(encodedFilterPath)).toBe(true)
    })

    it("should update encoded and decoded query filter paths as the same bookmark", () => {
        const store = storeWithRouter()

        store.add({path: decodedFilterPath, label: "Overview"})
        store.refreshLabel({path: encodedFilterPath, label: "Flow overview"})
        store.rename({path: encodedFilterPath, label: "My overview"})

        expect(store.pages[0]).toMatchObject({path: decodedFilterPath, label: "My overview", custom: true})

        store.remove({path: encodedFilterPath})

        expect(store.pages).toEqual([])
    })

    it("should collapse stored duplicate paths that differ only by query encoding", () => {
        localStorage.setItem(STORAGE_KEY, JSON.stringify([
            {path: encodedFilterPath, label: "Overview", custom: false},
            {path: decodedFilterPath, label: "My overview", custom: true},
        ]))

        const store = storeWithRouter()

        expect(store.pages).toEqual([{path: decodedFilterPath, label: "My overview", custom: true}])
    })

    it("should match space-encoded query values with router-canonical query values", () => {
        const store = storeWithRouter()

        store.add({path: spaceEncodedPath, label: "Search"})
        store.add({path: spaceCanonicalPath, label: "Search"})

        expect(store.pages).toEqual([{path: spaceCanonicalPath, label: "Search", custom: false}])
        expect(store.isBookmarked(spaceEncodedPath)).toBe(true)
    })
})
