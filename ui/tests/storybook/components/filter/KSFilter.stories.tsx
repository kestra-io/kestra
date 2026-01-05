import {vueRouter} from "storybook-vue3-router";
import type {Meta, StoryObj} from "@storybook/vue3";
import {useAuthStore} from "override/stores/auth";
import {useMiscStore} from "override/stores/misc";
import {useNamespacesStore} from "override/stores/namespaces";
import {useAxios} from "../../../../src/utils/axios";
import fixture from "../executions/Executions.fixture.json";
import Executions from "../../../../src/components/executions/Executions.vue";

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

const getNamespaces = (data: any[]): string[] => (
    Array.from(new Set(data
        .map(item => item.namespace).filter(Boolean)))
        .sort()
);

const hasLabel = (e: any, key: string, value: string) => 
    e.labels?.some((l: any) => l.key === key && l.value === value);

const filterExecutions = (executions: any[], params: any): any[] => 
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

                const axios = useAxios();
                (axios as any).get = (url: string, config?: any) =>
                    url.includes("/executions/search")
                        ? (() => {
                            const {page = "1", size = "25", ...params} = config?.params ?? {};
                            const filtered = filterExecutions(data, params);
                            const start = (parseInt(page) - 1) * parseInt(size);
                            return Promise.resolve({
                                data: {
                                    results: filtered.slice(start, start + parseInt(size)),
                                    total: filtered.length
                                }
                            });
                        })()
                        : Promise.resolve({data: []});

                (axios as any).post = (url: string) => 
                    url.includes("/namespaces/autocomplete") 
                        ? Promise.resolve({data: FIXTURE_NAMESPACES}) 
                        : Promise.resolve({data: {}});
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