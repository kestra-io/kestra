import type {Meta, StoryFn, StoryObj} from "@storybook/vue3-vite";
import Triggers from "./Triggers.vue";
import {vueRouter} from "storybook-vue3-router";
import {setMockClient, type ApiTriggerAndState, type AxiosLikeClient} from "@kestra-io/kestra-sdk"
import {mockClientFallback, mockResponse, mockStoryApiRoutes} from "../../../../.storybook/apiMock";

const meta: Meta<typeof Triggers> = {
    title: "Components/Admin/Triggers",
    component: Triggers,
    decorators: [
        vueRouter([
            {
                path: "/",
                name: "home",
                component: {template: "<div>home</div>"}
            },
            {
                path: "/:tab?",
                name: "admin/triggers",
                component: Triggers
            },
            {
                path: "/flows/edit/:namespace/:id",
                name: "flows/update",
                component: {template: "<div>update flow</div>"}
            },
        ])
    ],
}

export default meta;

const triggersData = [
    {
        "trigger": {
            "id": "every10min",
            "type": "io.kestra.plugin.core.trigger.Schedule",
            "disabled": true,
            "cron": "10 * * * *"
        },
        "state": {
            "namespace": "company.team",
            "flowId": "trigger_test_foo",
            "triggerId": "every10min",
            "updatedAt": "2025-04-15T14:34:19Z",
            "disabled": true,
            "locked": false
        }
    },
    {
        "trigger": {
            "id": "every5min",
            "type": "io.kestra.plugin.core.trigger.Schedule",
            "disabled": false,
            "cron": "5 * * * *"
        },
        "state": {
            "namespace": "io.kestra.company",
            "flowId": "trigger_tests_bar",
            "triggerId": "every5min",
            "updatedAt": "2025-04-15T14:34:19Z",
            "disabled": false,
            "locked": false
        }
    },
    {
        "trigger": {
            "backfill": true,
            "id": "every1min",
            "type": "io.kestra.plugin.core.trigger.Schedule",
            "disabled": false,
            "cron": "1 * * * *"
        },
        "state": {
            "namespace": "io.kestra.company",
            "flowId": "trigger_tests_backfill_running",
            "triggerId": "every1min",
            "updatedAt": "2025-04-15T14:34:19Z",
            "disabled": false,
            "locked": true
        }
    },
    {
        "trigger": {
            "backfill": {
                "paused": true
            },
            "id": "every1min",
            "type": "io.kestra.plugin.core.trigger.Schedule",
            "disabled": false,
            "cron": "1 * * * *"
        },
        "state": {
            "namespace": "io.kestra.company",
            "flowId": "trigger_tests_backfill_paused",
            "triggerId": "every1min",
            "updatedAt": "2025-04-15T14:34:19Z",
            "disabled": false,
            "locked": false
        }
    }
] as unknown as ApiTriggerAndState[]

const Template: StoryFn<{triggers: ApiTriggerAndState[]}> = (args) => ({
    setup() {
        mockStoryApiRoutes({
            "GET /triggers/search": {results: args.triggers, total: args.triggers.length},
            "GET /flows/search": {results: [], total: 0},
        })

        const store: Partial<AxiosLikeClient> = {}
        store.get = async function<T>(uri: string) {
            if (uri.includes("/distinct-namespaces")) {
                return mockResponse<T>([
                    "io.kestra.company",
                    "company.team",
                    "io.kestra.plugin",
                    "io.kestra",
                ])
            }

            // Anything this story doesn't answer itself falls back to the shared table in
            // .storybook/apiMock.ts, which reports the route if nothing there covers it either.
            return mockClientFallback<T>("GET", uri)
        }

        store.post = async function<T>(uri: string, data?: unknown) {
            return mockClientFallback<T>("POST", uri, data)
        }

        store.put = async function<T>(uri: string, data?: unknown) {
            return mockClientFallback<T>("PUT", uri, data)
        }

        setMockClient(store);

        return () =>
            <Triggers />
    }
});

export const Default: StoryObj<{triggers: ApiTriggerAndState[]}> = {
    render: Template,
    args: {
        triggers: triggersData,
    },
}
