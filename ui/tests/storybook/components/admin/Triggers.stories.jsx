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
        const store = {}
        store.get = async function (uri) {
            if (uri.includes("/triggers/search")) {
                return {
                    data: {
                        results: args.triggers,
                        total: args.triggers.length,
                    }
                }
            }

            if (uri.includes("/flows/search")) {
                return {
                    data: {
                        results: [],
                        total: 0,
                    }
                }
            }

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