import {afterAll, beforeEach, describe, expect, it} from "vitest"
import {isRef} from "vue"

import {useStoredPanels, PreSerializedPanel} from "./useStoredPanels"
import {EditorElement, Tab} from "../utils/multiPanelTypes"

function makeTab(uid: string): Tab {
    return {uid, button: {icon: null, label: uid}, component: null}
}

// An editor element that only deserializes the uids it knows about, mirroring a real one
// returning undefined for a tab whose backing element was renamed or removed.
function elementKnowing(...uids: string[]): Pick<EditorElement, "deserialize"> {
    const known = new Set(uids)
    return {deserialize: (uid) => (known.has(uid) ? makeTab(uid) : undefined)}
}

const KEY = "kestra.panels.test"

function storedLayout(): PreSerializedPanel[] {
    return JSON.parse(localStorage.getItem(KEY) ?? "null")
}

describe("useStoredPanels", () => {
    beforeEach(() => {
        localStorage.clear()
    })

    afterAll(() => {
        localStorage.clear()
    })

    it("serializes panels to their tab uids, active tab uid and size", () => {
        useStoredPanels(KEY, [elementKnowing("a", "b")], ["a", "b"])

        expect(storedLayout()).toEqual([
            {tabs: ["a"], activeTab: "a", size: 50},
            {tabs: ["b"], activeTab: "b", size: 50},
        ])
    })

    it("restores a stored layout into real tabs via the deserialize callbacks", () => {
        localStorage.setItem(KEY, JSON.stringify([
            {tabs: ["a", "b"], activeTab: "b", size: 60},
            {tabs: ["c"], activeTab: "c", size: 40},
        ]))

        const {panels} = useStoredPanels(KEY, [elementKnowing("a", "b", "c")])

        expect(panels.value).toEqual([
            {tabs: [makeTab("a"), makeTab("b")], activeTab: makeTab("b"), size: 60},
            {tabs: [makeTab("c")], activeTab: makeTab("c"), size: 40},
        ])
    })

    it("drops a tab whose uid no longer deserializes while keeping the rest of its panel", () => {
        localStorage.setItem(KEY, JSON.stringify([
            {tabs: ["a", "gone", "b"], activeTab: "a", size: 100},
        ]))

        const {panels} = useStoredPanels(KEY, [elementKnowing("a", "b")])

        expect(panels.value).toHaveLength(1)
        expect(panels.value[0].tabs).toEqual([makeTab("a"), makeTab("b")])
    })

    it("removes a panel stored with no tabs entirely", () => {
        localStorage.setItem(KEY, JSON.stringify([
            {tabs: [], activeTab: undefined, size: 50},
            {tabs: ["a"], activeTab: "a", size: 50},
        ]))

        const {panels} = useStoredPanels(KEY, [elementKnowing("a")])

        expect(panels.value).toEqual([
            {tabs: [makeTab("a")], activeTab: makeTab("a"), size: 50},
        ])
    })

    it("falls back to the first remaining tab when the active tab uid no longer exists", () => {
        localStorage.setItem(KEY, JSON.stringify([
            {tabs: ["a", "b"], activeTab: "gone", size: 100},
        ]))

        const {panels} = useStoredPanels(KEY, [elementKnowing("a", "b")])

        expect(panels.value[0].activeTab).toEqual(makeTab("a"))
    })

    it("returns a plain ref seeded from the defaults when given no storage key", () => {
        const {panels} = useStoredPanels(undefined, [elementKnowing("a")], ["a"])

        expect(isRef(panels)).toBe(true)
        expect(localStorage.length).toBe(0)
        expect(panels.value).toEqual([
            {tabs: [makeTab("a")], activeTab: makeTab("a"), size: 100},
        ])
    })

    it("saveState writes the current layout under an alternate key", () => {
        const {saveState} = useStoredPanels(KEY, [elementKnowing("a")], ["a"])

        const altKey = "kestra.panels.backup"
        saveState(altKey)

        expect(JSON.parse(localStorage.getItem(altKey)!)).toEqual([
            {tabs: ["a"], activeTab: "a", size: 100},
        ])
    })
})
