import Triggers from "../../../../src/components/admin/Triggers.vue";
import {vueRouter} from "storybook-vue3-router";
import {useAxios} from "../../../../src/utils/axios";

const meta = {
    title: "Components/Admin/Triggers",
    component: Triggers,
    decorators: [
        vueRouter([
            {
                path: "/",
                name: "home",
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
        "abstractTrigger": {
            "id": "every10min",
            "type": "io.kestra.plugin.core.trigger.Schedule",
            "cron": "10 * * * *"
        },
        "triggerContext": {
            "tenantId": "ten",
            "namespace": "company.team",
            "flowId": "trigger_test_foo",
            "triggerId": "every10min",
            "date": "2025-04-15T14:34:19+02:00",
            "disabled": true
        }
    },
    {
        "abstractTrigger": {
            "id": "every5min",
            "type": "io.kestra.plugin.core.trigger.Schedule",
            "cron": "5 * * * *"
        },
        "triggerContext": {
            "tenantId": "ten",
            "namespace": "io.kestra.company",
            "flowId": "trigger_tests_bar",
            "triggerId": "every5min",
            "disabled": false
        }
    },
    {
        "abstractTrigger": {
            "backfill": true,
            "id": "every1min",
            "type": "io.kestra.plugin.core.trigger.Schedule",
            "cron": "1 * * * *"
        },
        "triggerContext": {
            "tenantId": "ten",
            "namespace": "io.kestra.company",
            "flowId": "trigger_tests_backfill_running",
            "triggerId": "every1min",
            "disabled": false
        },
    },
    {
        "abstractTrigger": {
            "backfill": {
                "paused": true
            },
            "id": "every1min",
            "type": "io.kestra.plugin.core.trigger.Schedule",
            "cron": "1 * * * *"
        },
        "triggerContext": {
            "tenantId": "ten",
            "namespace": "io.kestra.company",
            "flowId": "trigger_tests_backfill_paused",
            "triggerId": "every1min",
            "disabled": false
        },
    }
]

const Template = (args) => ({
    setup() {
        const store = useAxios()
        store.get = async function (uri) {
            if (uri.includes("/triggers/search")) {
                return {
                    data: {
                        results: args.triggers,
                        total: args.triggers.length,
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