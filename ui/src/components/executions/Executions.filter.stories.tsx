import type {Execution, MiscControllerConfiguration} from "@kestra-io/kestra-sdk";
import {vueRouter} from "storybook-vue3-router";
import type {Meta, StoryObj} from "@storybook/vue3-vite";
import {Me, useAuthStore} from "override/stores/auth";
import {useMiscStore} from "override/stores/misc";
import {useNamespacesStore} from "override/stores/namespaces";
import {mockStoryApiRoutes, type ApiRouteContext} from "../../../.storybook/apiMock";
import fixture from "../../../tests/storybook/components/executions/Executions.fixture.json";
import Executions from "./Executions.vue";

type FixtureExecution = Pick<Execution, "id" | "namespace" | "flowId"> & {
    labels?: {key: string; value?: string}[];
    state: {current: string};
    kind?: string;
    scope?: string;
    childFilter?: string;
    triggerExecutionId?: string;
};

const SEARCHABLE_FIELDS = ["id", "namespace", "flowId"] as const;
const LABEL_FILTER_PATTERN = /filters\[labels]\[(\w+)]\[(.+)]/;

const toArray = (value: string) => value.split(",");

const FILTER_MAP: {[key: string]: (e: FixtureExecution, value: string) => boolean} = {
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

const hasLabel = (e: FixtureExecution, key: string, value: string) =>
    e.labels?.some((l) => l.key === key && l.value === value);

const filterExecutions = (executions: FixtureExecution[], params: Record<string, string>): FixtureExecution[] =>
    Object.entries(params).reduce((filtered, [key, value]) => {
        if (!value) return filtered;

        if (key === "filters[q][EQUALS]") {
            return filtered.filter((e) =>
                SEARCHABLE_FIELDS.some(field =>
                    e[field].toLowerCase().includes(value.toLowerCase())
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
                    ? hasLabel(e, match[2], value)
                    : !hasLabel(e, match[2], value)
            );
        }

        return filtered;
    }, [...executions]);

const ENUM_FIELD_TO_KEY: Record<string, string> = {QUERY: "q"};
function enumFieldToKey(field: string): string {
    return ENUM_FIELD_TO_KEY[field] ?? field.toLowerCase().replace(/_([a-z])/g, (_, c) => c.toUpperCase());
}

function flatFilterParams(query: URLSearchParams): Record<string, string> {
    const flat: Record<string, string> = {};
    for (const [key, value] of query) {
        const match = key.match(/^filters\[(\w+)](.*)$/);
        if (match) {
            flat[`filters[${enumFieldToKey(match[1])}]${match[2]}`] = value;
        }
    }
    return flat;
}

const getNamespaces = (data: FixtureExecution[]): string[] => (
    Array.from(new Set(data
        .map(item => item.namespace).filter(Boolean)))
        .sort()
);

const MOCK_CONFIGS: MiscControllerConfiguration = {
    hiddenLabelsPrefixes: ["system_"],
    edition: "OSS"
};

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
    },
    {
        path: "/namespaces/edit/:id",
        name: "namespaces/update",
        component: {template: "<div>namespace</div>"}
    }
];

function getDecorators(data: FixtureExecution[]) {
    const FIXTURE_NAMESPACES = getNamespaces(data);

    return [
        () => ({
            setup() {
                useAuthStore().user = new Me();
                useMiscStore().configs = MOCK_CONFIGS;
                useNamespacesStore().loadAutocomplete = () => Promise.resolve(FIXTURE_NAMESPACES);

                mockStoryApiRoutes({
                    "GET /executions/search": ({query}: ApiRouteContext) => {
                        const page = Number(query.get("page") ?? 1);
                        const size = Number(query.get("size") ?? 25);
                        const filtered = filterExecutions(data, flatFilterParams(query));
                        const start = (page - 1) * size;
                        return {results: filtered.slice(start, start + size), total: filtered.length};
                    },
                });
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
