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
        it("shouldReturnFlatMenuUnchanged", () => {
            const flatMenu: MenuItem[] = [
                {id: "flows", title: "Flows"},
                {id: "executions", title: "Executions"},
            ]
            expect(flattenMenuItems(flatMenu)).toEqual([
                {id: "flows", title: "Flows"},
                {id: "executions", title: "Executions"},
            ])
        })

        it("shouldFlattenChildrenWithParentsInDisplayOrder", () => {
            expect(flattenMenuItems(menu)).toEqual([
                menu[0],
                menu[0].child![0],
                menu[0].child![1],
                menu[0].child![2],
                menu[1],
                menu[1].child![0],
                menu[1].child![1],
            ])
        })

        it("shouldFullyFlattenArbitrarilyDeepNesting", () => {
            const deepMenu: MenuItem[] = [
                {
                    id: "level-1",
                    title: "Level 1",
                    child: [
                        {
                            id: "level-2",
                            title: "Level 2",
                            child: [
                                {
                                    id: "level-3",
                                    title: "Level 3",
                                    child: [
                                        {id: "deep-item", title: "Deep Item"},
                                    ],
                                },
                            ],
                        },
                    ],
                },
            ]
            expect(flattenMenuItems(deepMenu).map((item) => item.id)).toEqual([
                "level-1",
                "level-2",
                "level-3",
                "deep-item",
            ])
        })

        it("shouldKeepItemWithEmptyChildrenArray", () => {
            const menuWithEmptyChild: MenuItem[] = [
                {
                    id: "workspace",
                    title: "Workspace",
                    child: [
                        {id: "empty-item", title: "Empty", child: []},
                    ],
                },
            ]
            expect(flattenMenuItems(menuWithEmptyChild).map((item) => item.id)).toEqual([
                "workspace",
                "empty-item",
            ])
        })

        it("shouldReturnEmptyListForEmptyMenu", () => {
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
