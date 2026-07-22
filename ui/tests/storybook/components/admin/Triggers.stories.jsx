import {vi} from "vitest";

// triggerStore.search(), flowStore.findFlows() and pluginsStore.listTriggers() all call their
// generated SDK submodule functions directly, which go through the SDK's own internal client
// rather than the axios instance setMockClient() swaps - so each has to be intercepted at the
// submodule level. The Template below still uses setMockClient() as a catch-all for anything
// exercised by user interaction (unlock/restart/backfill actions), since this story has no
// play() function driving those paths.
const mockState = vi.hoisted(() => ({triggers: []}))
vi.mock("@kestra-io/kestra-sdk/triggers", () => ({
    searchTriggers: async () => ({results: mockState.triggers, total: mockState.triggers.length}),
}))
vi.mock("@kestra-io/kestra-sdk/flows", () => ({
    searchFlows: async () => ({results: [], total: 0}),
}))
vi.mock("@kestra-io/kestra-sdk/plugins", () => ({
    listTriggerPlugins: async () => ({results: [], total: 0}),
}))

import Triggers from "../../../../src/components/admin/triggers/Triggers.vue";
import {vueRouter} from "storybook-vue3-router";
import {setMockClient} from "@kestra-io/kestra-sdk"

const meta = {
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
]

const Template = (args) => ({
    setup() {
        mockState.triggers = args.triggers

        const store = {}
        store.get = async function (uri) {
            if (uri.includes("/distinct-namespaces")) {
                return {
                    data: [
                        "io.kestra.company",
                        "company.team",
                        "io.kestra.plugin",
                        "io.kestra",
                    ]
                }
            }

            console.log("get request", uri)
            return {data: {}}
        }

        store.post = async function (uri) {
            console.log("post request", uri)
            return {data: {}}
        }

        store.put = async function (uri) {
            console.log("put request", uri)
            return {data: {}}
        }

        setMockClient(store);

        return () =>
            <Triggers />
    }
});

export const Default = {
    render: Template,
    args: {
        triggers: triggersData,
    },
}