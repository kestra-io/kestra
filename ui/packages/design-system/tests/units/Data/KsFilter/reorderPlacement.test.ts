import {describe, test, expect} from "vitest"
import {computePlacement} from "../../../../src/components/Data/KsDataTable/filter/utils/reorderPlacement"

const GROUPS: Record<string, string> = {
    a1: "g1", a2: "g1", a3: "g1",
    b1: "g2", b2: "g2",
}
const groupIdOf = (id: string) => GROUPS[id]

describe("computePlacement", () => {
    test("drops the dragged filter at the end of the previous group at a boundary (regression)", () => {
        expect(computePlacement(["a1", "a2", "b2", "b1"], "b2", groupIdOf))
            .toEqual({targetLeafId: "g1", targetIndex: 2})
    })

    test("uses the following row's group when dropped at the very top", () => {
        expect(computePlacement(["b1", "a1", "a2", "b2"], "b1", groupIdOf))
            .toEqual({targetLeafId: "g1", targetIndex: 0})
    })

    test("repositions within the same group", () => {
        expect(computePlacement(["a2", "a1", "a3"], "a1", groupIdOf))
            .toEqual({targetLeafId: "g1", targetIndex: 1})
    })

    test("targets the position inside another group when dropped between its rows", () => {
        expect(computePlacement(["a2", "b1", "a1", "b2"], "a1", groupIdOf))
            .toEqual({targetLeafId: "g2", targetIndex: 1})
    })

    test("returns null when the dragged id is the only row", () => {
        expect(computePlacement(["a1"], "a1", groupIdOf)).toBeNull()
    })

    test("returns null when the dragged id is absent", () => {
        expect(computePlacement(["a1", "a2"], "missing", groupIdOf)).toBeNull()
    })
})
