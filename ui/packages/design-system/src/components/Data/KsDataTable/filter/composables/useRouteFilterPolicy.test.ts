import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {type EffectScope, effectScope, nextTick, reactive} from "vue"
import {useRouteFilterPolicy} from "./useRouteFilterPolicy"

const mockRoute = reactive<{query: Record<string, string>}>({query: {}})
const mockRouter = {
    replace: vi.fn((to: {query: Record<string, string>}) => {
        for (const key of Object.keys(mockRoute.query)) {
            delete mockRoute.query[key]
        }
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

    test("effectiveValue returns defaultValue while router navigation is in-flight and routeValue is undefined", () => {
        scope.run(() => {
            mockRoute.query = {}
            mockRouter.replace.mockImplementationOnce(() => {
                // Do not update mockRoute.query immediately to simulate async router
            })

            const {effectiveValue, isRouteSettled} = useRouteFilterPolicy({
                defaultValue: () => ({value: "INFO", direction: "min" as const}),
                applyDefaultIfMissing: () => true,
                fallbackValue: () => ({value: "TRACE", direction: "min" as const}),
                readFromRoute: (q) => q["level"] ? {value: q["level"] as string, direction: "min" as const} : undefined,
                writeToRoute: (q, val) => ({...q, level: val?.value}),
            })

            // Navigation is still in flight:
            expect(isRouteSettled.value).toBe(false)
            // Even though routeValue is undefined, effectiveValue returns defaultValue:
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

    test("reverts to fallbackValue when user clears filter after normalization", async () => {
        await scope.run(async () => {
            mockRoute.query = {}
            mockRouter.replace.mockClear()

            const {effectiveValue, setRouteValue} = useRouteFilterPolicy({
                defaultValue: () => ({value: "INFO", direction: "min" as const}),
                applyDefaultIfMissing: () => true,
                fallbackValue: () => ({value: "TRACE", direction: "min" as const}),
                readFromRoute: (q) => q["level"] ? {value: q["level"] as string, direction: "min" as const} : undefined,
                writeToRoute: (q, val) => {
                    const next = {...q}
                    if (val?.value) next.level = val.value
                    else delete next.level
                    return next
                },
            })

            // Initial tick: in-flight normalization gives defaultValue
            expect(effectiveValue.value).toEqual({value: "INFO", direction: "min"})

            // Wait for normalization replace to land
            await nextTick()
            expect(effectiveValue.value).toEqual({value: "INFO", direction: "min"})

            // Now simulate user clearing the filter chip
            setRouteValue(undefined)
            await nextTick()

            // After clearing, effectiveValue should revert to fallbackValue (TRACE)
            expect(effectiveValue.value).toEqual({value: "TRACE", direction: "min"})
        })
    })
})
