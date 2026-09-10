import {describe, expect, it, vi} from "vitest"
import {flatten} from "./utils"

vi.mock("@kestra-io/design-system", () => ({
    fileUtils: {isFileUri: () => false},
    copyToClipboard: vi.fn(),
}))
vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {}}),
}))

describe("flatten", () => {
    it("should key every leaf by its path, keeping nulls and empty containers", () => {
        expect(flatten({a: {b: 1}, c: null, d: {}, e: [1, 2]})).toEqual({
            "a.b": 1,
            "c": null,
            "d": {},
            "e.0": 1,
            "e.1": 2,
        })
    })

    it("should flatten more leaves than a spread can carry as arguments", () => {
        const wide = Object.fromEntries(Array.from({length: 150_000}, (_, index) => [`item_${index}`, index]))

        expect(Object.keys(flatten(wide))).toHaveLength(150_000)
    })
})
