import {vi} from "vitest";

// executionsStore.findExecutions() calls ExecutionsAPI.searchExecutions() directly, which goes
// through the SDK's own internal client rather than the axios instance setMockClient() swaps -
// so it has to be intercepted at the submodule level. searchExecutions() receives a real
// `filters: QueryFilter[]` array rather than the flat "filters[field][OP]" string keys axios
// used to see, so queryFiltersToFlatParams() reconstructs those flat keys to reuse the existing
// FILTER_MAP/filterExecutions logic unchanged. Everything the mock factory below needs to close
// over must live inside vi.hoisted(), since vi.mock() factories run before any other module code.
const {mockState, filterExecutions} = vi.hoisted(() => {
    const state = {data: [] as any[]};

    const SEARCHABLE_FIELDS = ["id", "namespace", "flowId"] as const;
    const LABEL_FILTER_PATTERN = /filters\[labels]\[(\w+)]\[(.+)]/;

    const toArray = (value: any) => Array.isArray(value)
        ? value
        : value.split(",");

    const FILTER_MAP: {[key: string]: (e: any, value: any) => boolean} = {
        "filters[namespace][IN]": (e, value) => toArray(value).includes(e.namespace),
        "filters[namespace][NOT_IN]": (e, value) => !toArray(value).includes(e.namespace),
        "filters[namespace][CONTAINS]": (e, value) => e.namespace?.toLowerCase().includes(value.toLowerCase()),
        "filters[flowId][EQUALS]": (e, value) => e.flowId?.toLowerCase() === value.toLowerCase(),
        "filters[flowId][NOT_EQUALS]": (e, value) => e.flowId?.toLowerCase() !== value.toLowerCase(),
        "filters[flowId][CONTAINS]": (e, value) => e.flowId?.toLowerCase().includes(value.toLowerCase()),
        "filters[state][IN]": (e, value) => toArray(value).includes(e.state?.current),
        "filters[state][NOT_IN]": (e, value) => !toArray(value).includes(e.state?.current),
        "filters[kind][EQUALS]": (e, value) => e.kind === value,
        "filters[scope][EQUALS]": (e, value) => e.scope === value,
        "filters[scope][NOT_EQUALS]": (e, value) => e.scope !== value,
        "filters[childFilter][EQUALS]": (e, value) => e.childFilter === value,
        "filters[triggerExecutionId][EQUALS]": (e, value) => e.triggerExecutionId === value,
        "filters[triggerExecutionId][NOT_EQUALS]": (e, value) => e.triggerExecutionId !== value,
        "filters[timeRange][EQUALS]": () => true,
    };

    const hasLabel = (e: any, key: string, value: string) =>
        e.labels?.some((l: any) => l.key === key && l.value === value);

    const filterFn = (executions: any[], params: any): any[] =>
        Object.entries(params).reduce((filtered, [key, value]) => {
            if (!value) return filtered;

            if (key === "filters[q][EQUALS]") {
                return filtered.filter((e: any) =>
                    SEARCHABLE_FIELDS.some(field =>
                        e[field]?.toLowerCase().includes((value as string).toLowerCase())
                    )
                );
            }

            if (FILTER_MAP[key]) {
                return filtered.filter(e => FILTER_MAP[key](e, value));
            }

            if (key.startsWith("filters[labels]")) {
                const match = key.match(LABEL_FILTER_PATTERN);
                if (!match) return filtered;

                return filtered.filter(e =>
                    match[1] === "EQUALS"
                        ? hasLabel(e, match[2], value as string)
                        : !hasLabel(e, match[2], value as string)
                );
            }

            return filtered;
        }, [...executions]);

    return {mockState: state, filterExecutions: filterFn};
})

const ENUM_FIELD_TO_KEY: Record<string, string> = {QUERY: "q"};
function enumFieldToKey(field: string): string {
    return ENUM_FIELD_TO_KEY[field] ?? field.toLowerCase().replace(/_([a-z])/g, (_, c) => c.toUpperCase());
}
function queryFiltersToFlatParams(filters: {field: string, operation: string, value: unknown}[]): Record<string, any> {
    const flat: Record<string, any> = {};
    for (const f of filters ?? []) {
        const key = enumFieldToKey(f.field);
        if (key === "labels" && f.value && typeof f.value === "object") {
            for (const [subKey, subValue] of Object.entries(f.value as Record<string, unknown>)) {
                flat[`filters[labels][${f.operation}][${subKey}]`] = subValue;
            }
        } else {
            flat[`filters[${key}][${f.operation}]`] = f.value;
        }
    }
    return flat;
}

vi.mock("@kestra-io/kestra-sdk/executions", () => ({
    searchExecutions: async (params: {page?: number, size?: number, filters?: any[]}) => {
        const {page = 1, size = 25} = params;
        const flatParams = queryFiltersToFlatParams(params.filters ?? []);
        const filtered = filterExecutions(mockState.data, flatParams);
        const start = (page - 1) * size;
        return {results: filtered.slice(start, start + size), total: filtered.length};
    },
}))

import {vueRouter} from "storybook-vue3-router";
import type {Meta, StoryObj} from "@storybook/vue3";
import {useAuthStore} from "override/stores/auth";
import {useMiscStore} from "override/stores/misc";
import {useNamespacesStore} from "override/stores/namespaces";
import fixture from "../executions/Executions.fixture.json";
import Executions from "../../../../src/components/executions/Executions.vue";

const getNamespaces = (data: any[]): string[] => (
    Array.from(new Set(data
        .map(item => item.namespace).filter(Boolean)))
        .sort()
);

const MOCK_USER = {
    isAllowed: () => true,
    hasAnyActionOnAnyNamespace: () => true,
} as any;

const MOCK_CONFIGS = {
    hiddenLabelsPrefixes: ["system_"],
    edition: "OSS"
} as any;

const ROUTER_ROUTES = [
    {
        path: "/",
        name: "home",
        component: {template: "<div>home</div>"}
    },
    {
        path: "/flows/update/:namespace/:id?/:flowId?",
        name: "flows/update",
        component: {template: "<div>updateflows</div>"}
    }, {
        path: "/executions/update/:namespace/:id?/:flowId?",
        name: "executions/update",
        component: {template: "<div>executions</div>"}
    },
    {
        path: "/executions/:id?/:flowId?",
        name: "executions/list",
        component: {template: "<div>executions</div>"}
    }
];

function getDecorators(data: any[]) {
    const FIXTURE_NAMESPACES = getNamespaces(data);

    return [
        () => ({
            setup() {
                useAuthStore().user = MOCK_USER;
                useMiscStore().configs = MOCK_CONFIGS;
                useNamespacesStore().loadAutocomplete = () => Promise.resolve(FIXTURE_NAMESPACES);

                mockState.data = data;
            },
            template: "<div style='margin:2rem'><story /></div>"
        }),
        vueRouter(ROUTER_ROUTES, {initialRoute: "/executions/123/645"}),
    ];
}

const meta: Meta<typeof Executions> = {
    title: "Components/Filter/KSFilter",
    component: Executions,
    parameters: {layout: "fullscreen"}
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
    decorators: getDecorators(fixture.results),
    args: {embed: false, topbar: false, filter: true, visibleCharts: false}
};
