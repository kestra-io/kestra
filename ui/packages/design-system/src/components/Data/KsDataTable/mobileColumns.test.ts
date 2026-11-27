import {describe, expect, test} from "vitest"
import {mobileColumnClass, mobileColumnOrder, type KsDataTableMobileColumns} from "./mobileColumns"

const COLUMNS: KsDataTableMobileColumns = {
    leading: "state",
    title: "id",
    titleAside: "startDate",
    meta: ["namespace", "flowId", "duration"],
    metaAside: "labels",
    actions: "actions",
}

describe("mobileColumnClass", () => {
    // Every unset optional slot is undefined as well, so without its own check an undefined key
    // would match the first of them instead of hiding.
    test("hides an undefined key", () => {
        expect(mobileColumnClass({title: "id"}, undefined)).toBe("ks-mobile-hidden")
    })

    test.each([
        ["leading", "ks-mobile-leading"],
        ["title", "ks-mobile-title"],
        ["titleAside", "ks-mobile-title-aside"],
        ["actions", "ks-mobile-actions"],
    ] as const)("maps the %s key to %s", (slot, expected) => {
        expect(mobileColumnClass(COLUMNS, COLUMNS[slot])).toBe(expected)
    })

    test("marks only the first meta key with ks-mobile-meta-first", () => {
        expect(mobileColumnClass(COLUMNS, "namespace")).toBe("ks-mobile-meta ks-mobile-meta-first")
        expect(mobileColumnClass(COLUMNS, "flowId")).toBe("ks-mobile-meta")
        expect(mobileColumnClass(COLUMNS, "duration")).toBe("ks-mobile-meta")
    })

    test("maps the metaAside key to ks-mobile-meta-aside", () => {
        expect(mobileColumnClass(COLUMNS, "labels")).toBe("ks-mobile-meta-aside")
    })

    test("hides a key that is in no slot", () => {
        expect(mobileColumnClass(COLUMNS, "unknown")).toBe("ks-mobile-hidden")
    })

    test("does not throw without a meta array, and still reaches metaAside", () => {
        const columns = {title: "id", metaAside: "labels"}

        expect(mobileColumnClass(columns, "labels")).toBe("ks-mobile-meta-aside")
        expect(mobileColumnClass(columns, "unknown")).toBe("ks-mobile-hidden")
    })

    // One row per link of the chain: the key sits in a slot and in the one checked right after it.
    test.each([
        {earlier: "leading", later: "title", columns: {leading: "shared", title: "shared"}, expected: "ks-mobile-leading"},
        {earlier: "title", later: "titleAside", columns: {title: "shared", titleAside: "shared"}, expected: "ks-mobile-title"},
        {earlier: "titleAside", later: "actions", columns: {title: "id", titleAside: "shared", actions: "shared"}, expected: "ks-mobile-title-aside"},
        {earlier: "actions", later: "meta", columns: {title: "id", actions: "shared", meta: ["shared"]}, expected: "ks-mobile-actions"},
        {earlier: "meta", later: "metaAside", columns: {title: "id", meta: ["namespace", "shared"], metaAside: "shared"}, expected: "ks-mobile-meta"},
    ])("resolves a key in both $earlier and $later to $earlier", ({columns, expected}) => {
        expect(mobileColumnClass(columns, "shared")).toBe(expected)
    })
})

describe("mobileColumnOrder", () => {
    test("returns the meta index of a meta key", () => {
        expect(mobileColumnOrder(COLUMNS, "namespace")).toBe(0)
        expect(mobileColumnOrder(COLUMNS, "flowId")).toBe(1)
        expect(mobileColumnOrder(COLUMNS, "duration")).toBe(2)
    })

    test.each([undefined, "state", "id", "startDate", "labels", "actions", "unknown"])("returns undefined for %s, which is not a meta key", (key) => {
        expect(mobileColumnOrder(COLUMNS, key)).toBeUndefined()
    })

    test("returns undefined without a meta array", () => {
        expect(mobileColumnOrder({title: "id"}, "id")).toBeUndefined()
    })

    test("keeps the meta index of a key that mobileColumnClass resolves to an earlier slot", () => {
        const columns = {title: "namespace", meta: ["id", "namespace"]}

        expect(mobileColumnClass(columns, "namespace")).toBe("ks-mobile-title")
        expect(mobileColumnOrder(columns, "namespace")).toBe(1)
    })
})
