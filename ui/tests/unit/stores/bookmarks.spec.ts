import {afterAll, beforeEach, describe, expect, it} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {useBookmarksStore} from "../../../src/stores/bookmarks"

const LOCAL_STORAGE_KEY = "starred.bookmarks"

describe("bookmarks store", () => {
    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
    })

    afterAll(() => {
        localStorage.clear()
    })

    it("shouldAddAndPersistBookmark", () => {
        const store = useBookmarksStore()

        store.add({path: "/flows/a", label: "A"})

        expect(store.pages).toEqual([{path: "/flows/a", label: "A"}])
        expect(JSON.parse(localStorage.getItem(LOCAL_STORAGE_KEY)!)).toEqual([
            {path: "/flows/a", label: "A"},
        ])
    })

    it("shouldNotDuplicateBookmarkForSamePath", () => {
        const store = useBookmarksStore()

        store.add({path: "/flows/a", label: "A"})
        store.add({path: "/flows/a", label: "A again"})

        expect(store.pages).toEqual([{path: "/flows/a", label: "A"}])
    })

    it("shouldPreserveBookmarksWrittenByOtherTabsWhenAdding", () => {
        const store = useBookmarksStore()
        expect(store.pages).toEqual([])

        // Another tab wrote to localStorage while this tab still has stale memory.
        localStorage.setItem(
            LOCAL_STORAGE_KEY,
            JSON.stringify([{path: "/flows/a", label: "A"}]),
        )

        store.add({path: "/flows/b", label: "B"})

        expect(store.pages).toEqual([
            {path: "/flows/a", label: "A"},
            {path: "/flows/b", label: "B"},
        ])
        expect(JSON.parse(localStorage.getItem(LOCAL_STORAGE_KEY)!)).toEqual([
            {path: "/flows/a", label: "A"},
            {path: "/flows/b", label: "B"},
        ])
    })

    it("shouldPreserveBookmarksWrittenByOtherTabsWhenRemoving", () => {
        const store = useBookmarksStore()
        store.add({path: "/flows/a", label: "A"})

        localStorage.setItem(
            LOCAL_STORAGE_KEY,
            JSON.stringify([
                {path: "/flows/a", label: "A"},
                {path: "/flows/b", label: "B"},
            ]),
        )

        store.remove({path: "/flows/a"})

        expect(store.pages).toEqual([{path: "/flows/b", label: "B"}])
    })

    it("shouldRenameAgainstLatestPersistedBookmarks", () => {
        const store = useBookmarksStore()
        store.add({path: "/flows/a", label: "A"})

        localStorage.setItem(
            LOCAL_STORAGE_KEY,
            JSON.stringify([
                {path: "/flows/a", label: "A"},
                {path: "/flows/b", label: "B"},
            ]),
        )

        store.rename({path: "/flows/a", label: "Renamed A"})

        expect(store.pages).toEqual([
            {path: "/flows/a", label: "Renamed A"},
            {path: "/flows/b", label: "B"},
        ])
    })

    it("shouldSyncFromStorageEvent", () => {
        const store = useBookmarksStore()

        localStorage.setItem(
            LOCAL_STORAGE_KEY,
            JSON.stringify([{path: "/flows/a", label: "A"}]),
        )
        window.dispatchEvent(
            new StorageEvent("storage", {
                key: LOCAL_STORAGE_KEY,
                newValue: JSON.stringify([{path: "/flows/a", label: "A"}]),
            }),
        )

        expect(store.pages).toEqual([{path: "/flows/a", label: "A"}])
    })

    it("shouldRecoverFromCorruptedLocalStorage", () => {
        localStorage.setItem(LOCAL_STORAGE_KEY, "not-json")
        setActivePinia(createPinia())

        const store = useBookmarksStore()

        expect(store.pages).toEqual([])
        store.add({path: "/flows/a", label: "A"})
        expect(store.pages).toEqual([{path: "/flows/a", label: "A"}])
    })
})
