import {describe, it, expect} from "vitest"
import {removeNullAndUndefined} from "./cleanUp"

describe("cleanUp", () => {
    describe("removeNullAndUndefined", () => {
        it("returns undefined for null and undefined inputs", () => {
            expect(removeNullAndUndefined(null)).toBeUndefined()
            expect(removeNullAndUndefined(undefined)).toBeUndefined()
        })

        it("returns primitives unchanged", () => {
            expect(removeNullAndUndefined("hello")).toBe("hello")
            expect(removeNullAndUndefined(42)).toBe(42)
            expect(removeNullAndUndefined(true)).toBe(true)
            expect(removeNullAndUndefined(false)).toBe(false)
            expect(removeNullAndUndefined(0)).toBe(0)
        })

        it("removes null and undefined properties from objects", () => {
            const input = {
                a: "value",
                b: null,
                c: undefined,
                d: 123,
            }
            expect(removeNullAndUndefined(input)).toEqual({
                a: "value",
                d: 123,
            })
        })

        it("returns undefined for an object where all properties are null or undefined", () => {
            const input = {
                a: null,
                b: undefined,
            }
            expect(removeNullAndUndefined(input)).toBeUndefined()
        })

        it("filters out null and undefined items from arrays", () => {
            const input = ["first", null, "second", undefined, 3]
            expect(removeNullAndUndefined(input)).toEqual(["first", "second", 3])
        })

        it("returns undefined for an array containing only null or undefined items", () => {
            expect(removeNullAndUndefined([null, undefined])).toBeUndefined()
            expect(removeNullAndUndefined([])).toBeUndefined()
        })

        it("handles deeply nested objects and arrays", () => {
            const input = {
                name: "test",
                nested: {
                    keep: "yes",
                    drop: null,
                    emptyObj: {
                        allNull: null,
                    },
                },
                list: [
                    {val: "item1", remove: null},
                    {allNull: null},
                    null,
                ],
            }

            expect(removeNullAndUndefined(input)).toEqual({
                name: "test",
                nested: {
                    keep: "yes",
                },
                list: [
                    {val: "item1"},
                ],
            })
        })

        it("keeps a top-level field as it is when told to, empty values included", () => {
            const task = {id: "rows", description: null, defaults: [{name: "web", cpus: null}, {name: null, cpus: null}]}
            expect(removeNullAndUndefined(task, (key) => key === "defaults"))
                .toEqual({id: "rows", defaults: [{name: "web", cpus: null}, {name: null, cpus: null}]})
        })
    })
})
