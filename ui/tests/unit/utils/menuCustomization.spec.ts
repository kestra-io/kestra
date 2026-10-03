import {describe, it, expect} from "vitest"
import {
    menuSectionId,
    flattenMenuItems,
    resolveSectionItemIds,
    pickItemsByIds,
    isMenuItemVisible,
} from "../../../src/utils/menuCustomization"
import type {MenuItem} from "override/components/useLeftMenu"

const menu: MenuItem[] = [
    {
        title: "Workspace",
        child: [
            {id: "flows", title: "Flows"},
            {id: "executions", title: "Executions"},
            {id: "logs", title: "Logs"},
        ],
    },
    {
        title: "Resources",
        child: [
            {id: "namespaces", title: "Namespaces"},
            {id: "hidden-one", title: "Hidden", hidden: true},
        ],
    },
]

describe("menuCustomization", () => {
    describe("menuSectionId", () => {
        it("shouldUseExplicitIdWhenPresent", () => {
            expect(menuSectionId({id: "system", title: "Tenant Admin"})).toBe("system")
        })

        it("shouldDeriveSlugFromTitleWhenNoId", () => {
            expect(menuSectionId({title: "Tenant Admin"})).toBe("tenant-admin")
        })
    })

    describe("flattenMenuItems", () => {
        it("shouldReturnAFlatMenuUnchanged", () => {
            // A single section's children come back as-is: these are the leaf items the
            // customisation dialog reorders, so a flat section must round-trip untouched.
            const flat: MenuItem[] = [
                {title: "Workspace", child: [
                    {id: "flows", title: "Flows"},
                    {id: "executions", title: "Executions"},
                ]},
            ]
            expect(flattenMenuItems(flat).map((i) => i.id)).toEqual(["flows", "executions"])
        })

        it("shouldFlattenChildrenInDisplayOrderAcrossSections", () => {
            // The dialog works off one list, so items from later sections must follow items
            // from earlier ones; the parent section headers themselves are not emitted.
            expect(flattenMenuItems(menu).map((i) => i.id)).toEqual([
                "flows",
                "executions",
                "logs",
                "namespaces",
                "hidden-one",
            ])
        })

        it("shouldKeepDeeperNestingOnTheLiftedChild", () => {
            // flattenMenuItems lifts each section's direct children one level; a grandchild is
            // not hoisted but travels with its parent, so the parent keeps its own child array.
            const nested: MenuItem[] = [
                {title: "Workspace", child: [
                    {id: "flows", title: "Flows", child: [
                        {id: "flow-detail", title: "Detail"},
                    ]},
                ]},
            ]
            const result = flattenMenuItems(nested)
            expect(result.map((i) => i.id)).toEqual(["flows"])
            expect(result[0].child?.map((i) => i.id)).toEqual(["flow-detail"])
        })

        it("shouldDropASectionWithAnEmptyChildArray", () => {
            // An empty children array contributes nothing to the flat list rather than throwing.
            const emptied: MenuItem[] = [{title: "Workspace", child: []}]
            expect(flattenMenuItems(emptied)).toEqual([])
        })

        it("shouldDropASectionThatHasNoChildProperty", () => {
            // child is optional on MenuItem; a header-only section must be skipped, not crash.
            const headerOnly: MenuItem[] = [{title: "Separator"}]
            expect(flattenMenuItems(headerOnly)).toEqual([])
        })

        it("shouldReturnAnEmptyListForAnEmptyMenu", () => {
            expect(flattenMenuItems([])).toEqual([])
        })
    })

    describe("resolveSectionItemIds", () => {
        it("shouldReturnDefaultOrderWhenSectionUntouched", () => {
            expect(resolveSectionItemIds(menu, {}, "workspace")).toEqual([
                "flows",
                "executions",
                "logs",
            ])
        })

        it("shouldExcludeHiddenAndIdlessItemsFromDefaults", () => {
            expect(resolveSectionItemIds(menu, {}, "resources")).toEqual(["namespaces"])
        })

        it("shouldUseSavedOrderWhenPresent", () => {
            const order = {workspace: ["logs", "flows", "executions"]}
            expect(resolveSectionItemIds(menu, order, "workspace")).toEqual([
                "logs",
                "flows",
                "executions",
            ])
        })

        it("shouldFilterStaleIdsFromSavedOrder", () => {
            const order = {workspace: ["flows", "removed-item", "logs"]}
            expect(resolveSectionItemIds(menu, order, "workspace")).toEqual(["flows", "logs"])
        })

        it("shouldExcludeItemsMovedToAnotherSectionFromUntouchedDefaults", () => {
            const order = {resources: ["namespaces", "flows"]}
            expect(resolveSectionItemIds(menu, order, "workspace")).toEqual([
                "executions",
                "logs",
            ])
        })

        it("shouldReturnEmptyArrayForExplicitlyEmptiedSection", () => {
            const order = {workspace: []}
            expect(resolveSectionItemIds(menu, order, "workspace")).toEqual([])
        })

        it("shouldDistinguishEmptiedSectionFromUntouchedSection", () => {
            const order = {workspace: []}
            expect(resolveSectionItemIds(menu, order, "resources")).toEqual(["namespaces"])
        })
    })

    describe("pickItemsByIds", () => {
        it("shouldResolveIdsToItemsInGivenOrder", () => {
            const result = pickItemsByIds(menu, ["logs", "namespaces"])
            expect(result.map((i) => i.id)).toEqual(["logs", "namespaces"])
        })

        it("shouldDropUnknownIdsAndHiddenItems", () => {
            const result = pickItemsByIds(menu, ["flows", "unknown", "hidden-one"])
            expect(result.map((i) => i.id)).toEqual(["flows"])
        })
    })

    describe("isMenuItemVisible", () => {
        it("shouldDefaultToVisibleWhenUnset", () => {
            expect(isMenuItemVisible({}, {id: "flows", title: "Flows"})).toBe(true)
        })

        it("shouldHideWhenExplicitlyFalse", () => {
            expect(isMenuItemVisible({flows: false}, {id: "flows", title: "Flows"})).toBe(false)
        })

        it("shouldTreatIdlessItemAsVisible", () => {
            expect(isMenuItemVisible({}, {title: "Separator"})).toBe(true)
        })
    })
})
