import {describe, expect, it} from "vitest"
import {routeFamily} from "./routeFamily"

const families = ["executions/update", "flows/update", "assets/update", "tests/edit", "namespaces/update"]

describe("routeFamily", () => {
    it.each(families)("keeps the exact family %s unchanged", (family) => {
        expect(routeFamily(family)).toBe(family)
    })

    it.each([
        ["executions/update/logs", "executions/update"],
        ["flows/update/editor", "flows/update"],
        ["assets/update/overview", "assets/update"],
        ["tests/edit/runs", "tests/edit"],
        ["namespaces/update/files", "namespaces/update"],
    ])("normalizes child route %s to %s", (name, family) => {
        expect(routeFamily(name)).toBe(family)
    })

    it.each(families)("does not normalize a similar prefix %ss", (family) => {
        const name = `${family}s`
        expect(routeFamily(name)).toBe(name)
    })

    it("leaves an unmigrated route unchanged", () => {
        expect(routeFamily("flows/list")).toBe("flows/list")
    })

    it.each([undefined, null])("returns an empty string for %s", (name) => {
        expect(routeFamily(name)).toBe("")
    })
})
