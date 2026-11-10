import {describe, test, expect} from "vitest"
import {rowKey} from "./rowKey"

describe("rowKey", () => {
    test("returns the same key for the same object on every call", () => {
        const row = {name: "a"}

        expect(rowKey(row)).toBe(rowKey(row))
    })

    test("returns different keys for distinct objects", () => {
        expect(rowKey({name: "a"})).not.toBe(rowKey({name: "b"}))
    })

    test("returns different keys for deeply equal but distinct instances", () => {
        expect(rowKey({name: "a"})).not.toBe(rowKey({name: "a"}))
    })

    test("formats keys as row-<n>", () => {
        expect(rowKey({})).toMatch(/^row-\d+$/)
    })
})
