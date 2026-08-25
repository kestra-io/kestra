import {describe, expect, it, beforeEach, afterEach} from "vitest"
import {createPinia, setActivePinia} from "pinia"

import {useBookmarksStore} from "../../../src/stores/bookmarks"

const STORAGE_KEY = "starred.bookmarks"

describe("bookmarks store", () => {
    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
    })
    afterEach(() => localStorage.clear())

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

        expect(store.pages[0].custom).toBeUndefined()
        store.rename({path: "/flows", label: "Mine"})
        expect(store.pages[0].custom).toBe(true)
    })

    // Entries persisted before the flag existed carry no `custom`, and must be refreshable —
    // they are exactly the stale-language bookmarks this fixes.
    it("should refresh a bookmark stored before the custom flag existed", () => {
        localStorage.setItem(STORAGE_KEY, JSON.stringify([{path: "/flows", label: "Ausführungen"}]))
        const store = useBookmarksStore()

        store.refreshLabel({path: "/flows", label: "Executions"})

        expect(store.pages[0].label).toBe("Executions")
    })

    it("should not touch other bookmarks", () => {
        const store = useBookmarksStore()
        store.add({path: "/flows", label: "Flows"})
        store.add({path: "/executions", label: "Ausführungen"})

        store.refreshLabel({path: "/executions", label: "Executions"})

        expect(store.pages.map(p => p.label)).toEqual(["Flows", "Executions"])
    })

    it("should be a no-op when the label is unchanged", () => {
        const store = useBookmarksStore()
        store.add({path: "/flows", label: "Flows"})
        const before = store.pages[0]

        store.refreshLabel({path: "/flows", label: "Flows"})

        expect(store.pages[0]).toBe(before)
    })
})
