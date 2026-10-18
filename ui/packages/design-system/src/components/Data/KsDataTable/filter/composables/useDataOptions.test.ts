import {beforeEach, describe, expect, test, vi} from "vitest"
import {nextTick, reactive} from "vue"
import type {TableOptions} from "../utils/filterTypes"
import {useDataOptions} from "./useDataOptions"

const STORAGE_KEY = "filterDataOptions"

beforeEach(() => localStorage.clear())

describe("showOptions", () => {
    test("defaults to false when nothing is stored", () => {
        expect(useDataOptions({}).showOptions.value).toBe(false)
    })

    test("reads the stored value case-insensitively", () => {
        localStorage.setItem(STORAGE_KEY, "TRUE")
        expect(useDataOptions({}).showOptions.value).toBe(true)

        localStorage.setItem(STORAGE_KEY, "False")
        expect(useDataOptions({}).showOptions.value).toBe(false)
    })

    test("toggleOptions flips the value and writes it back", () => {
        const {showOptions, toggleOptions} = useDataOptions({})

        toggleOptions()
        expect(showOptions.value).toBe(true)
        expect(localStorage.getItem(STORAGE_KEY)).toBe("true")

        toggleOptions()
        expect(showOptions.value).toBe(false)
        expect(localStorage.getItem(STORAGE_KEY)).toBe("false")
    })
})

describe("chartVisible", () => {
    test("starts from options.chart.value", () => {
        expect(useDataOptions({chart: {value: false}}).chartVisible.value).toBe(false)
    })

    test("defaults to true when no chart value is given", () => {
        expect(useDataOptions({}).chartVisible.value).toBe(true)
        expect(useDataOptions({chart: {}}).chartVisible.value).toBe(true)
    })

    test("follows an external change of options.chart.value", async () => {
        const options = reactive<TableOptions>({chart: {value: true}})
        const {chartVisible} = useDataOptions(options)

        options.chart!.value = false
        await nextTick()

        expect(chartVisible.value).toBe(false)
    })

    test("ignores an external change to undefined instead of blanking the flag", async () => {
        const options = reactive<TableOptions>({chart: {value: false}})
        const {chartVisible} = useDataOptions(options)

        options.chart!.value = undefined
        await nextTick()

        expect(chartVisible.value).toBe(false)
    })

    test("updateChart sets the flag and calls the chart callback", () => {
        const callback = vi.fn()
        const {chartVisible, updateChart} = useDataOptions({chart: {value: true, callback}})

        updateChart(false)

        expect(chartVisible.value).toBe(false)
        expect(callback).toHaveBeenCalledExactlyOnceWith(false)
    })
})

describe("refreshData", () => {
    test("calls the refresh callback", () => {
        const callback = vi.fn()

        useDataOptions({refresh: {callback}}).refreshData()

        expect(callback).toHaveBeenCalledOnce()
    })

    test("does nothing when no refresh callback was supplied", () => {
        expect(() => useDataOptions({}).refreshData()).not.toThrow()
        expect(() => useDataOptions({refresh: {}}).refreshData()).not.toThrow()
    })
})
