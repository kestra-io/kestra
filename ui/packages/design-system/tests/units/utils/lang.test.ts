import {describe, test, expect, vi, beforeEach, afterEach} from "vitest"
import {
    cloneDeep,
    debounce,
    deepMerge,
    escapeHtml,
    getPath,
    groupBy,
    isDeepEqual,
    isPlainObject,
    mapValues,
    setPath,
    throttle,
} from "../../../src/utils/lang"

describe("escapeHtml", () => {
    test("escapes every character that could break out of an v-html interpolation", () => {
        expect(escapeHtml("<img src=x onerror=\"alert('1')\">"))
            .toBe("&lt;img src=x onerror=&quot;alert(&#39;1&#39;)&quot;&gt;")
    })

    test("escapes ampersands before the entities it produces", () => {
        expect(escapeHtml("a & <b>")).toBe("a &amp; &lt;b&gt;")
    })

    test("returns an empty string for nullish values", () => {
        expect(escapeHtml(undefined)).toBe("")
        expect(escapeHtml(null)).toBe("")
    })
})

describe("cloneDeep", () => {
    test("detaches nested structures from the source", () => {
        const source = {list: [{id: 1}], date: new Date(0)}
        const clone = cloneDeep(source)
        clone.list[0].id = 2

        expect(source.list[0].id).toBe(1)
        expect(clone.date).not.toBe(source.date)
        expect(clone.date.getTime()).toBe(0)
    })

    test("keeps non-clonable values by reference instead of emptying them", () => {
        const file = new File(["content"], "flow.yaml")
        const clone = cloneDeep({file})

        expect(clone.file).toBe(file)
    })
})

describe("deepMerge", () => {
    test("merges nested objects without mutating either argument", () => {
        const target = {a: {one: 1}, keep: true}
        const source = {a: {two: 2}}
        const merged = deepMerge(target, source)

        expect(merged).toEqual({a: {one: 1, two: 2}, keep: true})
        expect(target).toEqual({a: {one: 1}, keep: true})
    })

    test("skips undefined source values and replaces arrays", () => {
        expect(deepMerge({a: 1, b: [1, 2, 3]}, {a: undefined, b: [9]}))
            .toEqual({a: 1, b: [9]})
    })

    test("treats a nullish target as an empty object", () => {
        expect(deepMerge(undefined, {a: 1})).toEqual({a: 1})
    })
})

describe("isDeepEqual", () => {
    test("compares nested values structurally", () => {
        expect(isDeepEqual({a: [{b: 1}]}, {a: [{b: 1}]})).toBe(true)
        expect(isDeepEqual({a: [{b: 1}]}, {a: [{b: 2}]})).toBe(false)
    })

    test("does not report equality when one side has extra keys", () => {
        expect(isDeepEqual({a: 1}, {a: 1, b: undefined})).toBe(false)
    })

    test("treats NaN as equal to itself and dates by value", () => {
        expect(isDeepEqual(NaN, NaN)).toBe(true)
        expect(isDeepEqual(new Date(5), new Date(5))).toBe(true)
    })
})

describe("debounce", () => {
    beforeEach(() => vi.useFakeTimers())
    afterEach(() => vi.useRealTimers())

    test("runs once with the latest arguments after the delay", () => {
        const callback = vi.fn()
        const debounced = debounce(callback, 100)

        debounced("first")
        debounced("second")
        vi.advanceTimersByTime(100)

        expect(callback).toHaveBeenCalledExactlyOnceWith("second")
    })

    test("cancel drops the pending call and flush runs it immediately", () => {
        const callback = vi.fn()
        const debounced = debounce(callback, 100)

        debounced("dropped")
        debounced.cancel()
        vi.advanceTimersByTime(100)
        expect(callback).not.toHaveBeenCalled()

        debounced("kept")
        debounced.flush()
        expect(callback).toHaveBeenCalledExactlyOnceWith("kept")

        vi.advanceTimersByTime(100)
        expect(callback).toHaveBeenCalledOnce()
    })
})

describe("throttle", () => {
    beforeEach(() => vi.useFakeTimers())
    afterEach(() => vi.useRealTimers())

    test("runs on the leading edge then once more at the end of the window", () => {
        const callback = vi.fn()
        const throttled = throttle(callback, 100)

        throttled("first")
        throttled("second")
        throttled("third")
        expect(callback).toHaveBeenCalledExactlyOnceWith("first")

        vi.advanceTimersByTime(100)
        expect(callback).toHaveBeenNthCalledWith(2, "third")
    })

    test("leading: false waits for the end of the first window", () => {
        const callback = vi.fn()
        const throttled = throttle(callback, 100, {leading: false})

        throttled("first")
        expect(callback).not.toHaveBeenCalled()

        vi.advanceTimersByTime(100)
        expect(callback).toHaveBeenCalledExactlyOnceWith("first")
    })

    test("leading: false still defers after the window has lapsed", () => {
        const callback = vi.fn()
        const throttled = throttle(callback, 100, {leading: false})

        throttled("first")
        vi.advanceTimersByTime(100)
        expect(callback).toHaveBeenCalledExactlyOnceWith("first")

        vi.advanceTimersByTime(1000)
        throttled("second")
        expect(callback).toHaveBeenCalledOnce()

        vi.advanceTimersByTime(100)
        expect(callback).toHaveBeenNthCalledWith(2, "second")
    })

    test("cancel gives the leading edge back", () => {
        const callback = vi.fn()
        const throttled = throttle(callback, 100)

        throttled("leading")
        throttled.cancel()

        throttled("after cancel")
        expect(callback).toHaveBeenNthCalledWith(2, "after cancel")
    })

    test("flush runs the trailing call immediately and cancel drops it", () => {
        const callback = vi.fn()
        const throttled = throttle(callback, 100)

        throttled("leading")
        throttled("trailing")
        throttled.flush()
        expect(callback).toHaveBeenNthCalledWith(2, "trailing")

        throttled("dropped")
        throttled.cancel()
        vi.advanceTimersByTime(200)
        expect(callback).toHaveBeenCalledTimes(2)
    })
})

describe("getPath and setPath", () => {
    test("read dotted and bracketed paths", () => {
        const source = {tasks: [{id: "a", nested: {value: 1}}]}

        expect(getPath(source, "tasks[0].nested.value")).toBe(1)
        expect(getPath(source, "tasks[1].nested.value", "fallback")).toBe("fallback")
        expect(getPath(source, ["tasks", 0, "id"])).toBe("a")
    })

    test("setPath refuses segments that would reach the prototype chain", () => {
        const target: Record<string, unknown> = {}

        setPath(target, "__proto__.polluted", "yes")
        setPath(target, ["constructor", "prototype", "polluted"], "yes")

        expect(target).toEqual({})
        expect(({} as Record<string, unknown>).polluted).toBeUndefined()
    })

    test("create missing containers, using an array when the next segment is an index", () => {
        const target: Record<string, unknown> = {}

        setPath(target, "tasks[0].id", "a")
        setPath(target, "meta.label", "b")

        expect(target).toEqual({tasks: [{id: "a"}], meta: {label: "b"}})
    })
})

describe("isPlainObject", () => {
    test("accepts object literals and null-prototype objects", () => {
        expect(isPlainObject({})).toBe(true)
        expect(isPlainObject(Object.create(null))).toBe(true)
    })

    test("rejects the values deepMerge and cloneDeep must not walk into", () => {
        expect(isPlainObject(new Date())).toBe(false)
        expect(isPlainObject(new Map())).toBe(false)
        expect(isPlainObject(new (class Thing {})())).toBe(false)
        expect(isPlainObject([])).toBe(false)
        expect(isPlainObject(null)).toBe(false)
    })
})

describe("groupBy", () => {
    test("keys by the stringified iteratee result, keeping input order in each group", () => {
        const rows = [{id: 1, kind: 2}, {id: 2, kind: 1}, {id: 3, kind: 2}]

        expect(groupBy(rows, (row) => row.kind)).toEqual({
            1: [{id: 2, kind: 1}],
            2: [{id: 1, kind: 2}, {id: 3, kind: 2}],
        })
    })

    test("returns an empty object for an empty input", () => {
        expect(groupBy([], (item) => String(item))).toEqual({})
    })
})

describe("mapValues", () => {
    test("passes the key as the second argument and leaves the keys untouched", () => {
        expect(mapValues({a: 1, b: 2}, (value, key) => `${key}${value}`)).toEqual({a: "a1", b: "b2"})
    })
})
