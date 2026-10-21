import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {effectScope, type EffectScope} from "vue"
import type {LocationQuery, LocationQueryRaw} from "vue-router"

import {COMPARATOR_LABELS, Comparators, type AppliedFilter} from "../utils/filterTypes"
import {
    hasUnsupportedRouteLevelComparator,
    normalizeRouteLevelFilter,
    readAppliedLevelFilter,
    readRouteLevelFilter,
    type LevelFilterValue,
} from "../utils/logLevelQuery"

import {useRouteFilterPolicy} from "./useRouteFilterPolicy"

type QueryLike = LocationQuery | LocationQueryRaw;
type Navigation = {query?: LocationQueryRaw};
type Policy = ReturnType<typeof useRouteFilterPolicy<LevelFilterValue>>;

const routerStub = vi.hoisted(() => {
    const state = {
        route: null as {query: QueryLike} | null,
        // Simulates a router.replace landing synchronously. Flipped off for the settle-timeout
        // test, where the navigation must never land for the grace period to stay armed.
        autoApply: true,
        replace: vi.fn<(to: Navigation) => void>(),
    }
    state.replace.mockImplementation((to) => {
        if (state.route && state.autoApply && to.query !== undefined) {
            state.route.query = to.query
        }
    })
    return state
})

vi.mock("vue-router", async () => {
    const {reactive} = await import("vue")
    const route = reactive({query: {} as QueryLike})
    routerStub.route = route
    return {
        useRoute: () => route,
        useRouter: () => ({replace: routerStub.replace}),
    }
})

let scopes: EffectScope[] = []

const setQuery = (query: QueryLike) => {
    if (routerStub.route === null) {
        throw new Error("the vue-router stub was not initialised")
    }
    routerStub.route.query = query
}

const startPolicy = (setup: () => Policy): Policy => {
    const scope = effectScope()
    scopes.push(scope)
    const policy = scope.run(setup)
    if (policy === undefined) {
        throw new Error("the composable did not run inside an active effect scope")
    }
    return policy
}

const minLevel = (value: string): LevelFilterValue => ({value, direction: "min"})

const levelFilter = (value: string): AppliedFilter => ({
    id: "level-filter-1",
    key: "level",
    keyLabel: "Level",
    comparator: Comparators.GREATER_THAN_OR_EQUAL_TO,
    comparatorLabel: COMPARATOR_LABELS[Comparators.GREATER_THAN_OR_EQUAL_TO],
    value,
    valueLabel: value,
})

const resolverOptions = {
    readFromRoute: readRouteLevelFilter,
    writeToRoute: normalizeRouteLevelFilter,
}

const unsupportedOptions = {
    ...resolverOptions,
    hasUnsupportedRouteValue: hasUnsupportedRouteLevelComparator,
}

const replacedQuery = (callIndex: number) => routerStub.replace.mock.calls[callIndex]?.[0]?.query

describe("useRouteFilterPolicy", () => {
    describe("value resolution", () => {
        test("shouldReadAValuePresentInTheRouteQueryBackThroughReadFromRoute", () => {
            setQuery({"filters[level][GREATER_THAN_OR_EQUAL_TO]": "ERROR"})
            const policy = startPolicy(() =>
                useRouteFilterPolicy<LevelFilterValue>({...resolverOptions}))

            expect(policy.routeValue.value).toEqual(minLevel("ERROR"))
            expect(policy.effectiveValue.value).toEqual(minLevel("ERROR"))
            expect(routerStub.replace).not.toHaveBeenCalled()
        })

        // effectiveValue prefers the route value over the explicit one. The issue's second bullet
        // states the reverse, but the code (and its consumer in Logs.vue) treats the URL as the
        // most specific source, so the precedence is pinned as implemented: kestra-io/kestra#21308
        test("shouldPreferTheRouteValueOverTheExplicitValueWhenBothAreSet", () => {
            setQuery({"filters[level][GREATER_THAN_OR_EQUAL_TO]": "ERROR"})
            const policy = startPolicy(() =>
                useRouteFilterPolicy<LevelFilterValue>({
                    ...resolverOptions,
                    explicitValue: () => minLevel("INFO"),
                    defaultValue: () => minLevel("DEBUG"),
                    applyDefaultIfMissing: () => true,
                }))

            expect(policy.effectiveValue.value).toEqual(minLevel("ERROR"))
            expect(routerStub.replace).not.toHaveBeenCalled()
        })

        test("shouldWriteTheExplicitValueToTheQueryInsteadOfTheDefault", () => {
            const policy = startPolicy(() =>
                useRouteFilterPolicy<LevelFilterValue>({
                    ...resolverOptions,
                    explicitValue: () => minLevel("INFO"),
                    defaultValue: () => minLevel("DEBUG"),
                    applyDefaultIfMissing: () => true,
                }))

            expect(replacedQuery(0)).toEqual({"filters[level][GREATER_THAN_OR_EQUAL_TO]": "INFO"})
            expect(policy.effectiveValue.value).toEqual(minLevel("INFO"))
            expect(routerStub.replace).toHaveBeenCalledTimes(1)
        })

        test("shouldWriteTheDefaultValueToTheQueryWhenTheValueIsMissingAndApplyDefaultIfMissingIsTrue", () => {
            const policy = startPolicy(() =>
                useRouteFilterPolicy<LevelFilterValue>({
                    ...resolverOptions,
                    defaultValue: () => minLevel("DEBUG"),
                    applyDefaultIfMissing: () => true,
                }))

            expect(replacedQuery(0)).toEqual({"filters[level][GREATER_THAN_OR_EQUAL_TO]": "DEBUG"})
            expect(policy.effectiveValue.value).toEqual(minLevel("DEBUG"))
            expect(routerStub.replace).toHaveBeenCalledTimes(1)
        })

        test("shouldNotWriteTheDefaultValueToTheQueryWhenApplyDefaultIfMissingIsFalse", () => {
            const policy = startPolicy(() =>
                useRouteFilterPolicy<LevelFilterValue>({
                    ...resolverOptions,
                    defaultValue: () => minLevel("DEBUG"),
                }))

            expect(routerStub.replace).not.toHaveBeenCalled()
            expect(policy.effectiveValue.value).toBeUndefined()
        })

        test("shouldUseTheFallbackValueWhenThereIsNoRouteExplicitOrDefaultValue", () => {
            const policy = startPolicy(() =>
                useRouteFilterPolicy<LevelFilterValue>({
                    ...resolverOptions,
                    fallbackValue: () => minLevel("TRACE"),
                }))

            expect(routerStub.replace).not.toHaveBeenCalled()
            expect(policy.effectiveValue.value).toEqual(minLevel("TRACE"))
        })

        test("shouldWriteNothingWhenEnabledReturnsFalse", () => {
            const policy = startPolicy(() =>
                useRouteFilterPolicy<LevelFilterValue>({
                    ...resolverOptions,
                    enabled: () => false,
                    defaultValue: () => minLevel("DEBUG"),
                    applyDefaultIfMissing: () => true,
                    fallbackValue: () => minLevel("TRACE"),
                }))

            policy.setRouteValue(minLevel("INFO"))

            expect(routerStub.replace).not.toHaveBeenCalled()
            expect(policy.effectiveValue.value).toEqual(minLevel("TRACE"))
        })
    })

    describe("setRouteValue synchronization", () => {
        // isSameValue must compare by content: a freshly-built object equal to the route's own
        // value is no change, and only a re-encoded or new value may fire a router.replace.
        test("shouldNotReplaceTheRouteAgainForStructurallyEqualObjectValuesButForAChangedOne", () => {
            const policy = startPolicy(() =>
                useRouteFilterPolicy<LevelFilterValue>({...resolverOptions}))

            policy.setRouteValue(minLevel("INFO"))
            expect(replacedQuery(0)).toEqual({"filters[level][GREATER_THAN_OR_EQUAL_TO]": "INFO"})
            expect(routerStub.replace).toHaveBeenCalledTimes(1)

            policy.setRouteValue(minLevel("INFO"))
            expect(routerStub.replace).toHaveBeenCalledTimes(1)

            policy.setRouteValue(minLevel("DEBUG"))
            expect(replacedQuery(1)).toEqual({"filters[level][GREATER_THAN_OR_EQUAL_TO]": "DEBUG"})
            expect(routerStub.replace).toHaveBeenCalledTimes(2)
        })

        test("shouldShortCircuitTheEqualValueSyncWhenTheRouteValueIsUnsupported", () => {
            setQuery({level: "ERROR"})
            const policy = startPolicy(() =>
                useRouteFilterPolicy<LevelFilterValue>({...unsupportedOptions}))

            policy.setRouteValue(minLevel("ERROR"))
            expect(replacedQuery(0)).toEqual({"filters[level][GREATER_THAN_OR_EQUAL_TO]": "ERROR"})
            expect(routerStub.replace).toHaveBeenCalledTimes(1)

            // once the route carries the supported encoding, an equal value settles without a write
            policy.setRouteValue(minLevel("ERROR"))
            expect(routerStub.replace).toHaveBeenCalledTimes(1)
        })

        test("shouldNormalizeTheLegacyQueryAnywayWhenTheRouteCarriesAnUnsupportedValue", () => {
            setQuery({level: "ERROR"})
            const policy = startPolicy(() =>
                useRouteFilterPolicy<LevelFilterValue>({
                    ...unsupportedOptions,
                    defaultValue: () => minLevel("DEBUG"),
                    applyDefaultIfMissing: () => true,
                }))

            expect(replacedQuery(0)).toEqual({"filters[level][GREATER_THAN_OR_EQUAL_TO]": "ERROR"})
            expect(policy.effectiveValue.value).toEqual(minLevel("ERROR"))
            expect(routerStub.replace).toHaveBeenCalledTimes(1)
        })
    })

    describe("syncFromAppliedFilters", () => {
        test("shouldNotSyncFromAppliedFiltersWhenShouldSyncFromAppliedFiltersReturnsFalse", () => {
            const policy = startPolicy(() =>
                useRouteFilterPolicy<LevelFilterValue>({
                    ...resolverOptions,
                    readFromAppliedFilters: readAppliedLevelFilter,
                    shouldSyncFromAppliedFilters: () => false,
                }))

            policy.syncFromAppliedFilters([levelFilter("ERROR")])

            expect(routerStub.replace).not.toHaveBeenCalled()
            expect(policy.routeValue.value).toBeUndefined()
        })

        test("shouldReadTheAppliedValueBackIntoTheRouteWhenTheGateAllowsIt", () => {
            const policy = startPolicy(() =>
                useRouteFilterPolicy<LevelFilterValue>({
                    ...resolverOptions,
                    readFromAppliedFilters: readAppliedLevelFilter,
                    shouldSyncFromAppliedFilters: () => true,
                }))

            policy.syncFromAppliedFilters([levelFilter("ERROR")])

            expect(replacedQuery(0)).toEqual({"filters[level][GREATER_THAN_OR_EQUAL_TO]": "ERROR"})
            expect(policy.routeValue.value).toEqual(minLevel("ERROR"))
        })
    })

    describe("scope disposal", () => {
        test("shouldTearDownTheSettleTimeoutWhenTheScopeIsDisposed", () => {
            // navigation never lands, so the settle grace period stays armed after the default write
            routerStub.autoApply = false
            const scope = effectScope()
            scopes.push(scope)
            const policy = scope.run(() =>
                useRouteFilterPolicy<LevelFilterValue>({
                    ...resolverOptions,
                    defaultValue: () => minLevel("DEBUG"),
                    applyDefaultIfMissing: () => true,
                }))
            if (policy === undefined) {
                throw new Error("the composable did not run inside an active effect scope")
            }

            expect(replacedQuery(0)).toEqual({"filters[level][GREATER_THAN_OR_EQUAL_TO]": "DEBUG"})
            expect(policy.isRouteSettled.value).toBe(false)
            expect(vi.getTimerCount()).toBe(1)

            scope.stop()

            expect(vi.getTimerCount()).toBe(0)
            vi.advanceTimersByTime(3000)
            expect(policy.isRouteSettled.value).toBe(false)
        })
    })

    beforeEach(() => {
        vi.useFakeTimers()
        scopes = []
        routerStub.autoApply = true
        routerStub.replace.mockClear()
        setQuery({})
    })

    afterEach(() => {
        scopes.forEach((scope) => scope.stop())
        scopes = []
        vi.useRealTimers()
    })
})
