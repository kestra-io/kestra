import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {type EffectScope, effectScope, nextTick, reactive} from "vue"
import {useRouteFilterPolicy} from "./useRouteFilterPolicy"

const mockRoute = reactive<{query: Record<string, string>}>({query: {}})
const mockRouter = {
    replace: vi.fn((to: {query: Record<string, string>}) => {
        Object.assign(mockRoute.query, to.query)
    }),
}

vi.mock("vue-router", () => ({
    useRoute: () => mockRoute,
    useRouter: () => mockRouter,
}))

describe("useRouteFilterPolicy", () => {
    let scope: EffectScope

    beforeEach(() => {
        scope = effectScope()
    })

    afterEach(() => {
        scope.stop()
    })

    test("effectiveValue is defaultValue (not fallbackValue) on mount when route query is empty", () => {
        scope.run(() => {
            mockRoute.query = {}
            mockRouter.replace.mockClear()

            const {effectiveValue} = useRouteFilterPolicy({
                defaultValue: () => ({value: "INFO", direction: "min" as const}),
                applyDefaultIfMissing: () => true,
                fallbackValue: () => ({value: "TRACE", direction: "min" as const}),
                readFromRoute: (q) => q["level"] ? {value: q["level"] as string, direction: "min" as const} : undefined,
                writeToRoute: (q, val) => ({...q, level: val?.value}),
            })

            // On mount before router.replace lands, effectiveValue must already be defaultValue (INFO)
            expect(effectiveValue.value).toEqual({value: "INFO", direction: "min"})
        })
    })

    test("effectiveValue does not jump between fallback and default while route normalization settles", async () => {
        await scope.run(async () => {
            mockRoute.query = {}
            mockRouter.replace.mockClear()

            const recordedEffectiveValues: unknown[] = []

            const {effectiveValue} = useRouteFilterPolicy({
                defaultValue: () => ({value: "INFO", direction: "min" as const}),
                applyDefaultIfMissing: () => true,
                fallbackValue: () => ({value: "TRACE", direction: "min" as const}),
                readFromRoute: (q) => q["level"] ? {value: q["level"] as string, direction: "min" as const} : undefined,
                writeToRoute: (q, val) => ({...q, level: val?.value}),
            })

            recordedEffectiveValues.push(effectiveValue.value)

            // After router.replace updates the query
            await nextTick()
            recordedEffectiveValues.push(effectiveValue.value)

            // It should NEVER have been TRACE
            expect(recordedEffectiveValues).toEqual([
                {value: "INFO", direction: "min"},
                {value: "INFO", direction: "min"},
            ])
        })
    })

    test("respects existing route query when already present", () => {
        scope.run(() => {
            mockRoute.query = {level: "WARN"}
            mockRouter.replace.mockClear()

            const {effectiveValue, routeValue} = useRouteFilterPolicy({
                defaultValue: () => ({value: "INFO", direction: "min" as const}),
                applyDefaultIfMissing: () => true,
                fallbackValue: () => ({value: "TRACE", direction: "min" as const}),
                readFromRoute: (q) => q["level"] ? {value: q["level"] as string, direction: "min" as const} : undefined,
                writeToRoute: (q, val) => ({...q, level: val?.value}),
            })

            expect(effectiveValue.value).toEqual({value: "WARN", direction: "min"})
            expect(routeValue.value).toEqual({value: "WARN", direction: "min"})
            expect(mockRouter.replace).not.toHaveBeenCalled()
        })
    })

    test("uses fallbackValue when applyDefaultIfMissing is false and route is empty", () => {
        scope.run(() => {
            mockRoute.query = {}
            mockRouter.replace.mockClear()

            const {effectiveValue} = useRouteFilterPolicy({
                defaultValue: () => ({value: "INFO", direction: "min" as const}),
                applyDefaultIfMissing: () => false,
                fallbackValue: () => ({value: "TRACE", direction: "min" as const}),
                readFromRoute: (q) => q["level"] ? {value: q["level"] as string, direction: "min" as const} : undefined,
                writeToRoute: (q, val) => ({...q, level: val?.value}),
            })

            expect(effectiveValue.value).toEqual({value: "TRACE", direction: "min"})
            expect(mockRouter.replace).not.toHaveBeenCalled()
        })
    })

    test("uses fallbackValue when disabled", () => {
        scope.run(() => {
            mockRoute.query = {level: "WARN"}
            mockRouter.replace.mockClear()

            const {effectiveValue} = useRouteFilterPolicy({
                enabled: () => false,
                defaultValue: () => ({value: "INFO", direction: "min" as const}),
                applyDefaultIfMissing: () => true,
                fallbackValue: () => ({value: "TRACE", direction: "min" as const}),
                readFromRoute: (q) => q["level"] ? {value: q["level"] as string, direction: "min" as const} : undefined,
                writeToRoute: (q, val) => ({...q, level: val?.value}),
            })

            expect(effectiveValue.value).toEqual({value: "TRACE", direction: "min"})
        })
    })
})
