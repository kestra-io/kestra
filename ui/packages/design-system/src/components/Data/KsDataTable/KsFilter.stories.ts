import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {provide, ref, shallowReactive} from "vue"
import {createMemoryHistory, createRouter, routeLocationKey, routerKey, START_LOCATION} from "vue-router"
import {expect, waitFor} from "storybook/test"
import KsFilter from "./KsFilter.vue"
import {SAME_ROW_TOLERANCE_PX} from "./filter/utils/constants"

const meta: Meta<typeof KsFilter> = {
    title: "Components/Data/KsFilter",
    component: KsFilter,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component: "KsFilter provides a complete filter bar with search, filter chips, saved filters, and table options. Multiple top-level groups combine with OR; dragging one group onto another wraps them with an AND/OR operator.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsFilter>

const SIMPLE_CONFIGURATION = {
    searchPlaceholder: "Search...",
    keys: [
        {
            key: "state",
            label: "State",
            valueType: "multi-select",
            comparators: ["IN", "NOT_IN"],
            valueProvider: () => Promise.resolve([
                {label: "Success", value: "SUCCESS"},
                {label: "Failed", value: "FAILED"},
                {label: "Running", value: "RUNNING"},
                {label: "Created", value: "CREATED"},
            ]),
        },
        {
            key: "namespace",
            label: "Namespace",
            valueType: "text",
            comparators: ["*=", "^="],
        },
        {
            key: "flowId",
            label: "Flow ID",
            valueType: "text",
            comparators: ["=", "*="],
        },
    ],
}

const GLOBAL_KEY_CONFIGURATION = {
    ...SIMPLE_CONFIGURATION,
    keys: [
        ...SIMPLE_CONFIGURATION.keys,
        {
            key: "timeRange",
            label: "Started",
            valueType: "select",
            comparators: ["="],
            groupable: false,
            valueProvider: () => Promise.resolve([{label: "Last 24 hours", value: "PT24H"}]),
        },
    ],
}

/**
 * Build a per-story router with its own memory history and provide it to the
 * KsFilter subtree, overriding the app-global router that Storybook installs
 * once in `preview.ts`.
 *
 * Without this isolation every story shares one router, so each story's
 * `router.replace({query})` clobbers the others — in the autodocs view that
 * stacks every story on one page, the last replace wins and every chip bar
 * renders the same (typically deepest) query.
 *
 * Returns a `ready` ref so callers can gate `<KsFilter v-if="ready" />` until
 * the navigation settles and `useFilters` sees the seeded route.
 */
const useIsolatedRouter = (query: Record<string, string> = {}) => {
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [{path: "/", component: {template: "<div/>"}}],
    })

    // Mirror what vue-router's `app.use(router)` does to expose a reactive
    // route object via inject — getters that always read from currentRoute.
    const reactiveRoute = {} as Record<string, unknown>
    for (const key in START_LOCATION) {
        Object.defineProperty(reactiveRoute, key, {
            get: () => (router.currentRoute.value as unknown as Record<string, unknown>)[key],
            enumerable: true,
        })
    }
    provide(routerKey, router)
    provide(routeLocationKey, shallowReactive(reactiveRoute) as never)

    const ready = ref(false)
    router.replace({path: "/", query}).finally(() => {
        ready.value = true
    })
    return ready
}

export const Default: Story = {
    render: () => ({
        components: {KsFilter},
        setup() {
            const ready = useIsolatedRouter()
            return {ready, configuration: SIMPLE_CONFIGURATION}
        },
        template: `
            <div style="padding: 24px">
                <KsFilter v-if="ready" :configuration="configuration" />
            </div>
        `,
    }),
}

export const ReadOnly: Story = {
    render: () => ({
        components: {KsFilter},
        setup() {
            const ready = useIsolatedRouter()
            return {ready, configuration: SIMPLE_CONFIGURATION}
        },
        template: `
            <div style="padding: 24px">
                <KsFilter v-if="ready" :configuration="configuration" :readOnly="true" />
            </div>
        `,
    }),
}

export const WithoutSearch: Story = {
    render: () => ({
        components: {KsFilter},
        setup() {
            const ready = useIsolatedRouter()
            return {ready, configuration: SIMPLE_CONFIGURATION}
        },
        template: `
            <div style="padding: 24px">
                <KsFilter v-if="ready" :configuration="configuration" :showSearchInput="false" />
            </div>
        `,
    }),
}

/**
 * Two top-level OR groups: `state IN [RUNNING]` OR `state IN [FAILED]`. The
 * operator chip between them can be flipped to AND via its popover. Try
 * dragging a chip from one group to the other to recompose the query.
 */
export const WithORGroups: Story = {
    render: () => ({
        components: {KsFilter},
        setup() {
            const ready = useIsolatedRouter({
                "filters[or][0][state][IN]": "RUNNING",
                "filters[or][1][state][IN]": "FAILED",
            })
            return {ready, configuration: SIMPLE_CONFIGURATION}
        },
        template: `
            <div style="padding: 24px">
                <KsFilter v-if="ready" :configuration="configuration" />
            </div>
        `,
    }),
}

/**
 * Demonstrates a nested group — a wrapper containing multiple leaves combined by their
 * own logical operator, sitting inside a top-level group with a (possibly different)
 * operator. Both the top-level and the wrapper operators can be flipped via their
 * respective AND/OR chip, so the same nesting shape covers `(A AND B) OR C`,
 * `(A OR B) AND C`, and so on.
 *
 * Seeded URL in this story: `(state IN [RUNNING] AND flowId = full) OR namespace ^= io.kestra`.
 *
 * This is the maximum nesting depth the chip UI renders — anything deeper (a wrapper
 * inside a wrapper) falls back to the raw editor.
 */
export const WithNestedGroup: Story = {
    render: () => ({
        components: {KsFilter},
        setup() {
            const ready = useIsolatedRouter({
                "filters[or][0][and][0][state][IN]": "RUNNING",
                "filters[or][0][and][1][flowId][EQUALS]": "full",
                "filters[or][1][namespace][STARTS_WITH]": "io.kestra",
            })
            return {ready, configuration: SIMPLE_CONFIGURATION}
        },
        template: `
            <div style="padding: 24px">
                <KsFilter v-if="ready" :configuration="configuration" />
            </div>
        `,
    }),
}

/**
 * Two chips on the same field with different comparators within one group:
 * `namespace *= kestra AND namespace ^= io.`. Allowed because each chip
 * produces a distinct URL key (`filters[namespace][CONTAINS]` and
 * `filters[namespace][STARTS_WITH]`).
 */
export const WithSameFieldTwice: Story = {
    render: () => ({
        components: {KsFilter},
        setup() {
            const ready = useIsolatedRouter({
                "filters[namespace][CONTAINS]": "kestra",
                "filters[namespace][STARTS_WITH]": "io.",
            })
            return {ready, configuration: SIMPLE_CONFIGURATION}
        },
        template: `
            <div style="padding: 24px">
                <KsFilter v-if="ready" :configuration="configuration" />
            </div>
        `,
    }),
}

/**
 * Same renderable URL as `WithORGroups`, but the filter starts in the raw
 * newline-delimited editor view via `defaultViewMode="raw"`. The user can
 * toggle back to the chip view because the query is still representable.
 */
export const WithRawEditor: Story = {
    render: () => ({
        components: {KsFilter},
        setup() {
            const ready = useIsolatedRouter({
                "filters[or][0][state][IN]": "RUNNING",
                "filters[or][1][state][IN]": "FAILED",
            })
            return {ready, configuration: SIMPLE_CONFIGURATION}
        },
        template: `
            <div style="padding: 24px">
                <KsFilter v-if="ready" :configuration="configuration" defaultViewMode="raw" />
            </div>
        `,
    }),
}

/**
 * A URL with three nested `[and|or][N]` segments — one level deeper than the
 * chip UI can render. The filter automatically switches to the raw editor on
 * mount and the toggle is locked in raw mode (the chip view can't represent
 * a wrapper-inside-a-wrapper, so flipping back would silently drop filters).
 */
export const WithUnrenderableQuery: Story = {
    render: () => ({
        components: {KsFilter},
        setup() {
            const ready = useIsolatedRouter({
                "filters[and][0][or][0][and][0][namespace][CONTAINS]": "system",
                "filters[and][0][or][0][and][1][state][IN]": "FAILED",
                "filters[and][0][or][1][flowId][EQUALS]": "deep",
            })
            return {ready, configuration: SIMPLE_CONFIGURATION}
        },
        template: `
            <div style="padding: 24px">
                <KsFilter v-if="ready" :configuration="configuration" />
            </div>
        `,
    }),
}

/**
 * Two conditions in one group (`ruleCount > 1`) — the chip bar collapses into
 * a "2 rules" pill. Clicking it opens the AdvancedFilterBuilder panel where
 * each condition is edited in its own ConditionRow.
 */
export const WithAdvancedBuilder: Story = {
    render: () => ({
        components: {KsFilter},
        setup() {
            const ready = useIsolatedRouter({
                "filters[state][IN]": "RUNNING",
                "filters[namespace][CONTAINS]": "io.kestra",
            })
            return {ready, configuration: SIMPLE_CONFIGURATION}
        },
        template: `
            <div style="padding: 24px">
                <KsFilter v-if="ready" :configuration="configuration" />
            </div>
        `,
    }),
}

const COLORED_CONFIGURATION = {
    searchPlaceholder: "Search...",
    keys: [
        {
            key: "state",
            label: "State",
            valueType: "multi-select",
            comparators: ["IN", "NOT_IN"],
            colored: true,
            valueProvider: () => Promise.resolve([
                {label: "Success", value: "SUCCESS"},
                {label: "Failed", value: "FAILED"},
                {label: "Running", value: "RUNNING"},
                {label: "Created", value: "CREATED"},
                {label: "Warning", value: "WARNING"},
                {label: "Paused", value: "PAUSED"},
            ]),
        },
        {
            key: "namespace",
            label: "Namespace",
            valueType: "text",
            comparators: ["*=", "^="],
        },
    ],
}

/**
 * Multi-select filter with `colored: true` — ConditionRow renders status tags
 * with per-state colors instead of plain labels. Open the AdvancedFilterBuilder
 * (by clicking the "2 rules" pill) to see the colored tag display.
 */
export const WithColoredStatusFilter: Story = {
    render: () => ({
        components: {KsFilter},
        setup() {
            const ready = useIsolatedRouter({
                "filters[state][IN]": "RUNNING,FAILED",
                "filters[namespace][CONTAINS]": "io.kestra",
            })
            return {ready, configuration: COLORED_CONFIGURATION}
        },
        template: `
            <div style="padding: 24px">
                <KsFilter v-if="ready" :configuration="configuration" />
            </div>
        `,
    }),
}

/**
 * At viewports ≤768px the bar collapses to a search field plus a filter button.
 * The button opens a bottom-anchored sheet (Figma `[mob]Filter`) with the
 * `All Filters` / `My Filters saved` segmented control, a per-field accordion
 * (each row expands to its value editor), and a `Clear all` / `Save` /
 * `Apply filters` footer. Seeded with one applied filter so the count badge shows.
 */
export const Mobile: Story = {
    parameters: {
        viewport: {
            options: {
                mobile: {name: "Mobile", styles: {width: "375px", height: "812px"}},
            },
        },
    },
    globals: {
        viewport: {value: "mobile", isRotated: false},
    },
    render: () => ({
        components: {KsFilter},
        setup() {
            const ready = useIsolatedRouter({
                "filters[namespace][CONTAINS]": "io.kestra",
            })
            return {ready, configuration: SIMPLE_CONFIGURATION}
        },
        template: `
            <div style="padding: 16px">
                <KsFilter v-if="ready" :configuration="configuration" />
            </div>
        `,
    }),
}

/**
 * Filter with chart and data-refresh table options — the FilterSettings panel
 * (opened from the right-side gear button) exposes a chart visibility toggle
 * and a periodic-refresh switch.
 */
export const WithTableOptions: Story = {
    render: () => ({
        components: {KsFilter},
        setup() {
            const ready = useIsolatedRouter()
            const tableOptions = {
                chart: {shown: true, value: true, callback: () => {}},
                refresh: {shown: true, callback: () => {}},
            }
            return {ready, configuration: SIMPLE_CONFIGURATION, tableOptions}
        },
        template: `
            <div style="padding: 24px">
                <KsFilter v-if="ready" :configuration="configuration" :tableOptions="tableOptions" />
            </div>
        `,
    }),
}

const TRANSPARENT = "rgba(0, 0, 0, 0)"

/** The element that carries the rule between the conditional and the global chips. */
const groupSeparator = (canvasElement: HTMLElement) => {
    const element = canvasElement.querySelector<HTMLElement>(".filter .ends-conditional-group")
    if (!element) throw new Error("nothing carries the group separator yet")
    return element
}

const chipCentres = (canvasElement: HTMLElement) =>
    [...canvasElement.querySelectorAll<HTMLElement>(".filter .filter-chip-wrap")].map((wrap) => {
        const box = wrap.getBoundingClientRect()
        return (box.top + box.bottom) / 2
    })

/**
 * A bar narrow enough that the chips wrap onto several rows, with a `groupable: false`
 * key applied so a global chip is among them. Every wrapped row starts at the same left
 * edge as the `{ }` toggle.
 */
export const WithWrappedRows: Story = {
    render: () => ({
        components: {KsFilter},
        setup() {
            const ready = useIsolatedRouter({
                "filters[namespace][STARTS_WITH]": "company",
                "filters[timeRange][EQUALS]": "PT24H",
            })
            return {ready, configuration: GLOBAL_KEY_CONFIGURATION}
        },
        template: `
            <div style="padding: 24px; width: 620px">
                <KsFilter v-if="ready" :configuration="configuration" />
            </div>
        `,
    }),
    async play({canvasElement}) {
        const main = await waitFor(() => {
            const element = canvasElement.querySelector<HTMLElement>(".filter .top > .main-filters")
            if (!element) throw new Error("no .filter .top > .main-filters: the bar is no longer one wrapping group")
            const chips = element.querySelectorAll(".filter-chip-wrap")
            if (chips.length < 2) throw new Error(`expected 2 chips, rendered ${chips.length}`)
            return element
        })

        // MainFilter's root is display: contents, so the items actually laid out are its children.
        const boxes = [...main.children]
            .flatMap((child) => getComputedStyle(child).display === "contents" ? [...child.children] : [child])
            .map((element) => element.getBoundingClientRect())
            .filter((box) => box.width > 0 && box.height > 0)

        // Rows group by vertical centre rather than by top: the 18px reset link and the 32px
        // chips beside it share a row without sharing a top.
        const leftPerRow = new Map<number, number>()
        for (const box of boxes) {
            const centre = (box.top + box.bottom) / 2
            const row = [...leftPerRow.keys()].find((key) => Math.abs(key - centre) <= 4) ?? centre
            leftPerRow.set(row, Math.min(leftPerRow.get(row) ?? Infinity, Math.round(box.left)))
        }

        expect(leftPerRow.size).toBeGreaterThan(1)
        expect([...new Set(leftPerRow.values())]).toHaveLength(1)

        // The global chip is on its own row here, so the group rule must not be drawn.
        const [conditional, global] = chipCentres(canvasElement)
        expect(Math.abs(conditional - global)).toBeGreaterThan(SAME_ROW_TOLERANCE_PX)
        await waitFor(() =>
            expect(getComputedStyle(groupSeparator(canvasElement)).borderRightColor).toBe(TRANSPARENT),
        )
    },
}

/**
 * The same filters on a bar wide enough to keep the conditional and the global chip on one row,
 * which is the only case where the rule between the two groups is drawn.
 */
export const WithGlobalSeparator: Story = {
    render: () => ({
        components: {KsFilter},
        setup() {
            const ready = useIsolatedRouter({
                "filters[namespace][STARTS_WITH]": "company",
                "filters[timeRange][EQUALS]": "PT24H",
            })
            const buttons = {savedFilters: {shown: false}, tableOptions: {shown: false}}
            const tableOptions = {columns: {shown: false}, refresh: {shown: false}}
            return {ready, configuration: GLOBAL_KEY_CONFIGURATION, buttons, tableOptions}
        },
        template: `
            <div style="padding: 24px; width: 1000px">
                <KsFilter
                    v-if="ready"
                    :configuration="configuration"
                    :buttons="buttons"
                    :tableOptions="tableOptions"
                />
            </div>
        `,
    }),
    async play({canvasElement}) {
        await waitFor(() => {
            const centres = chipCentres(canvasElement)
            if (centres.length < 2) throw new Error(`expected 2 chips, rendered ${centres.length}`)
            expect(Math.abs(centres[0] - centres[1])).toBeLessThanOrEqual(SAME_ROW_TOLERANCE_PX)
        })

        await waitFor(() =>
            expect(getComputedStyle(groupSeparator(canvasElement)).borderRightColor).not.toBe(TRANSPARENT),
        )
    },
}

/**
 * Two applied filters fold the conditional chips into the rules pill, which is the common case on
 * every list page since `timeRange` is a global key everywhere. The pill carries the group rule
 * just as a chip would.
 */
export const WithFoldedRulesSeparator: Story = {
    render: () => ({
        components: {KsFilter},
        setup() {
            const ready = useIsolatedRouter({
                "filters[namespace][STARTS_WITH]": "company",
                "filters[state][IN]": "FAILED",
                "filters[timeRange][EQUALS]": "PT24H",
            })
            const buttons = {savedFilters: {shown: false}, tableOptions: {shown: false}}
            const tableOptions = {columns: {shown: false}, refresh: {shown: false}}
            return {ready, configuration: GLOBAL_KEY_CONFIGURATION, buttons, tableOptions}
        },
        template: `
            <div style="padding: 24px; width: 1000px">
                <KsFilter
                    v-if="ready"
                    :configuration="configuration"
                    :buttons="buttons"
                    :tableOptions="tableOptions"
                />
            </div>
        `,
    }),
    async play({canvasElement}) {
        await waitFor(() => {
            if (!canvasElement.querySelector(".filter .rules-pill")) throw new Error("the pill has not folded yet")
            const centres = chipCentres(canvasElement)
            if (centres.length < 2) throw new Error(`expected the pill and one chip, found ${centres.length}`)
            expect(Math.abs(centres[0] - centres[1])).toBeLessThanOrEqual(SAME_ROW_TOLERANCE_PX)
        })

        await waitFor(() =>
            expect(getComputedStyle(groupSeparator(canvasElement)).borderRightColor).not.toBe(TRANSPARENT),
        )
    },
}

/**
 * What `BlueprintsFilterBar` renders: `searchInputFullWidth` with a configuration that has
 * no keys, so the search is the only control and stretches to fill the bar.
 */
export const WithFullWidthSearch: Story = {
    render: () => ({
        components: {KsFilter},
        setup() {
            const ready = useIsolatedRouter()
            const configuration = {searchPlaceholder: "Search blueprints...", keys: []}
            const buttons = {savedFilters: {shown: false}, tableOptions: {shown: false}}
            const tableOptions = {chart: {shown: false}, columns: {shown: false}, refresh: {shown: false}}
            return {ready, configuration, buttons, tableOptions}
        },
        template: `
            <div style="padding: 24px; width: 620px">
                <KsFilter
                    v-if="ready"
                    :configuration="configuration"
                    :buttons="buttons"
                    :tableOptions="tableOptions"
                    searchInputFullWidth
                />
            </div>
        `,
    }),
}

const STRING_KEY = {
    key: "flowId",
    label: "Flow ID",
    valueType: "text",
    comparators: ["=", "!=", "*=", "!*=", "^=", "$=", "~="],
    showComparatorSelection: true,
}

const NAMESPACE_KEY = {
    key: "namespace",
    label: "Namespace",
    valueType: "text",
    comparators: ["*=", "^.="],
    showComparatorSelection: true,
}

const NUMBER_KEY = {
    key: "attempts",
    label: "Attempts",
    valueType: "number",
    comparators: [">=", ">", "<=", "<", "BETWEEN"],
    showComparatorSelection: true,
}

const DATE_KEY = {
    key: "created",
    label: "Created",
    valueType: "date",
    comparators: [">=", ">", "<=", "<"],
    showComparatorSelection: true,
}

const DATE_RANGE_KEY = {
    key: "lastTriggered",
    label: "Last triggered",
    valueType: "time-range",
    customDateMode: "range",
    comparators: [">=", "<="],
    showComparatorSelection: true,
    valueProvider: () => Promise.resolve([{label: "Last 24 hours", value: "PT24H"}]),
}

const SELECT_KEY = {
    key: "triggerState",
    label: "Trigger state",
    valueType: "select",
    comparators: ["=", "!="],
    showComparatorSelection: true,
    valueProvider: () => Promise.resolve([
        {label: "Enabled", value: "ENABLED", count: 128},
        {label: "Disabled", value: "DISABLED", count: 7},
    ]),
}

const RADIO_KEY = {
    key: "childFilter",
    label: "Hierarchy",
    valueType: "radio",
    comparators: ["="],
    valueProvider: () => Promise.resolve([
        {label: "Parent", value: "MAIN", description: "Top-level executions only"},
        {label: "Child", value: "CHILD", description: "Nested executions only"},
    ]),
}

const MULTI_VALUE_KEY = {
    key: "level",
    label: "Level",
    valueType: "multi-select",
    comparators: ["IN", "NOT_IN", ">=", "<="],
    comparatorLabels: {">=": "At or Above", "<=": "At or Below"},
    showComparatorSelection: true,
    valueProvider: () => Promise.resolve([
        {label: "Trace", value: "TRACE", count: 18432},
        {label: "Debug", value: "DEBUG", count: 2210},
        {label: "Info", value: "INFO", count: 640},
        {label: "Warn", value: "WARN", count: 12},
        {label: "Error", value: "ERROR", count: 0},
    ]),
}

const KEY_VALUE_KEY = {
    key: "labels",
    label: "Labels",
    valueType: "key-value",
    comparators: ["=", "!=", "IN", "NOT_IN", "*=", "!*=", "IS_NOT_NULL", "IS_NULL"],
    showComparatorSelection: true,
}

/**
 * A bar holding only `key`, seeded with one chip whose editor the play opens, so the value editor
 * shows without a click. The play also checks the chip decoded its comparator and value; a `null`
 * comparator label is for a key with a single comparator, whose chip shows none.
 */
const comparatorStory = (
    key: object,
    query: Record<string, string>,
    comparatorLabel: string | RegExp | null,
    valueLabel: string,
): Story => ({
    render: () => ({
        components: {KsFilter},
        setup() {
            const ready = useIsolatedRouter(query)
            return {ready, configuration: {searchPlaceholder: "Search...", keys: [key]}}
        },
        template: `
            <div style="padding: 24px; min-height: 480px">
                <KsFilter v-if="ready" :configuration="configuration" />
            </div>
        `,
    }),
    async play({canvasElement}) {
        await waitFor(() =>
            expect(canvasElement.querySelector(".chip .value")?.textContent?.trim()).toBe(valueLabel),
        )
        const comparator = canvasElement.querySelector(".chip .comparator")?.textContent?.trim() ?? null
        if (comparatorLabel instanceof RegExp) await expect(comparator).toMatch(comparatorLabel)
        else await expect(comparator).toBe(comparatorLabel)

        canvasElement.querySelector<HTMLElement>(".chip")!.click()
        await waitFor(() => expect(canvasElement.ownerDocument.querySelector(".edit-popper")).not.toBeNull())
    },
})

export const ComparatorString = comparatorStory(STRING_KEY, {"filters[flowId][CONTAINS]": "hello"}, "Contains", "hello")
export const ComparatorRegex = comparatorStory(STRING_KEY, {"filters[flowId][REGEX]": "^hello-.*$"}, "Matches Pattern", "^hello-.*$")
export const ComparatorPrefix = comparatorStory(NAMESPACE_KEY, {"filters[namespace][PREFIX]": "company.team"}, "Prefix", "company.team")
export const ComparatorNumber = comparatorStory(NUMBER_KEY, {"filters[attempts][GREATER_THAN_OR_EQUAL_TO]": "2"}, "Greater Than or Equal", "2")
export const ComparatorDecimal = comparatorStory(NUMBER_KEY, {
    "filters[attempts][GREATER_THAN_OR_EQUAL_TO]": "0.5",
    "filters[attempts][LESS_THAN_OR_EQUAL_TO]": "2.75",
}, "Between", "0.5 - 2.75")
export const ComparatorDate = comparatorStory(
    DATE_KEY,
    {"filters[created][GREATER_THAN_OR_EQUAL_TO]": "2026-01-15T10:00:00.000Z"},
    "Greater Than or Equal",
    new Date("2026-01-15T10:00:00.000Z").toLocaleDateString(),
)
export const ComparatorDateRange = comparatorStory(
    DATE_RANGE_KEY,
    {
        "filters[lastTriggered][GREATER_THAN_OR_EQUAL_TO]": "2026-01-15T10:00:00.000Z",
        "filters[lastTriggered][LESS_THAN_OR_EQUAL_TO]": "2026-01-20T18:00:00.000Z",
    },
    /between/i,
    `${new Date("2026-01-15T10:00:00.000Z").toLocaleString()} - ${new Date("2026-01-20T18:00:00.000Z").toLocaleString()}`,
)
export const ComparatorSelect = comparatorStory(SELECT_KEY, {"filters[triggerState][NOT_EQUALS]": "DISABLED"}, "Not Equals", "DISABLED")
export const ComparatorRadio = comparatorStory(RADIO_KEY, {"filters[childFilter][EQUALS]": "CHILD"}, null, "CHILD")
export const ComparatorMultiValue = comparatorStory(MULTI_VALUE_KEY, {"filters[level][IN]": "WARN,ERROR"}, "In", "WARN+1")
export const ComparatorThreshold = comparatorStory(MULTI_VALUE_KEY, {"filters[level][GREATER_THAN_OR_EQUAL_TO]": "WARN"}, "At or Above", "WARN")
export const ComparatorKeyValue = comparatorStory(KEY_VALUE_KEY, {"filters[labels][EQUALS][team]": "core"}, "Equals", "team:core")
export const ComparatorNull = comparatorStory(KEY_VALUE_KEY, {"filters[labels][IS_NULL]": "team"}, "Is Not Set", "team")
