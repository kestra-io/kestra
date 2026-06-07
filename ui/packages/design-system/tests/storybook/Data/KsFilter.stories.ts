import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {provide, ref, shallowReactive} from "vue"
import {createMemoryHistory, createRouter, routeLocationKey, routerKey, START_LOCATION} from "vue-router"
import KsFilter from "../../../src/components/Data/KsDataTable/KsFilter.vue"

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
