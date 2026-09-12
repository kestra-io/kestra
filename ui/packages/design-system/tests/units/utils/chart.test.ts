import {describe, test, expect} from "vitest"
import {categoryLabel} from "../../../src/utils/chart"

describe("categoryLabel", () => {
    test("keeps the last segment of a fully-qualified type, with its own casing", () => {
        expect(categoryLabel("io.kestra.plugin.ee.assets.VM")).toBe("VM")
        expect(categoryLabel("io.kestra.core.models.assets.External")).toBe("External")
    })

    test("capitalises enum-shaped values", () => {
        expect(categoryLabel("SUCCESS")).toBe("Success")
        expect(categoryLabel("prod")).toBe("Prod")
    })

    test("does not trim a namespace or a decimal value", () => {
        expect(categoryLabel("io.kestra.demo")).toBe("Io.kestra.demo")
        expect(categoryLabel("10.5")).toBe("10.5")
    })
})
