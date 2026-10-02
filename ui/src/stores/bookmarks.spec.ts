import {afterEach, beforeEach, describe, expect, it} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {useBookmarksStore} from "./bookmarks"

const decodedPath = "/main/flows/edit/tutorial/getting-started-elt-pipeline/overview?filters[timeRange][EQUALS]=PT24H"
const encodedPath = "/main/flows/edit/tutorial/getting-started-elt-pipeline/overview?filters%5BtimeRange%5D%5BEQUALS%5D=PT24H"

describe("bookmarks store", () => {
    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
    })

    afterEach(() => {
        localStorage.clear()
    })

    it("should match encoded and decoded query filter paths as the same bookmark", () => {
        const store = useBookmarksStore()

        store.add({path: decodedPath, label: "Overview"})
        store.add({path: encodedPath, label: "Overview"})

        expect(store.pages).toEqual([{path: decodedPath, label: "Overview", custom: false}])
        expect(store.isBookmarked(encodedPath)).toBe(true)
    })

    it("should update encoded and decoded query filter paths as the same bookmark", () => {
        const store = useBookmarksStore()

        store.add({path: decodedPath, label: "Overview"})
        store.refreshLabel({path: encodedPath, label: "Flow overview"})

        expect(store.pages[0]).toMatchObject({path: decodedPath, label: "Flow overview"})

        store.rename({path: encodedPath, label: "My overview"})

        expect(store.pages[0]).toMatchObject({path: decodedPath, label: "My overview", custom: true})

        store.remove({path: encodedPath})

        expect(store.pages).toEqual([])
    })

    it("should collapse stored duplicate encoded and decoded query filter paths", () => {
        localStorage.setItem("starred.bookmarks", JSON.stringify([
            {path: encodedPath, label: "Overview", custom: false},
            {path: decodedPath, label: "My overview", custom: true},
        ]))

        const store = useBookmarksStore()

        expect(store.pages).toEqual([{path: decodedPath, label: "My overview", custom: true}])
    })
})
