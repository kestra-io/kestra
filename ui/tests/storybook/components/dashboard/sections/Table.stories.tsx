import Table from "../../../../../src/components/dashboard/sections/Table.vue";
import type {Chart} from "../../../../../src/components/dashboard/types.ts";
import type {Meta, StoryObj} from "@storybook/vue3-vite";
import {vueRouter} from "storybook-vue3-router";
import {useAxios} from "../../../../../src/utils/axios.ts";
import {expect, within} from "storybook/test";

const meta: Meta<typeof Table> = {
    title: "Dashboard/Sections/Table",
    component: Table,
    decorators: [
        vueRouter([
            {
                path: "/",
                component: () => <div></div>
            },
            {
                path: "/flows/update",
                name: "flows/update",
                component: () => <div></div>
            },
            {
                path: "/executions/update",
                name: "executions/update",
                component: () => <div></div>
            },
            {
                path: "/namespaces/update",
                name: "namespaces/update",
                component: () => <div></div>
            },
        ])
    ]
}

export default meta;

export const SimpleExecutionsCase: StoryObj<typeof Table> = {
    render: () => ({
        setup() {
            const store = useAxios() as any;
            store.post = async function (uri: string) {
                console.log("post request", uri)

                if (uri.includes("charts/executions_finished")) {
                    console.log("match charts/executions_finished", uri)

                    return {
                        data: {
                            results: [
                                {
                                    "namespace": "company.team",
                                    "id": "2wJlDoXRsMc7jXJfQUWTE7",
                                    "state": "RUNNING",
                                    "flow": "sleep",
                                    "start_date": "2025-11-25T09:28:00.000+00:00",
                                },
                                {
                                    "namespace": "company.team",
                                    "id": "2yiYHSqLwNbocm9FB8qK5L",
                                    "state": "RUNNING",
                                    "flow": "sleep",
                                    "start_date": "2025-11-25T09:28:00.000+00:00"
                                },
                                {
                                    "duration": 6,
                                    "namespace": "company.team",
                                    "id": "2Iq5tjur4bB9fRYYazstV4",
                                    "state": "SUCCESS",
                                    "flow": "sleep",
                                    "end_date": "2025-11-25T09:27:00.000+00:00",
                                    "start_date": "2025-11-25T09:27:00.000+00:00",
                                },
                                {
                                    "namespace": "company.team",
                                    "id": "69d95APmpdw94OkaMduCep",
                                    "state": "RUNNING",
                                    "flow": "sleep",
                                    "start_date": "2025-11-25T09:27:00.000+00:00"
                                }
                            ],
                            total: 4
                        }
                    }
                }
                return {results: []}
            }

            const chart: Chart = {
                "id": "executions_finished",
                "type": "io.kestra.plugin.core.dashboard.chart.Table",
                "chartOptions": {

                            "displayName": "Executions Finished",
                            "width": 12,
                            "header": {"enabled": true},
                            "pagination": {"enabled": true}
                },
                "data": {
                    "columns": {
                        "id": {"field": "ID", "displayName": "Execution ID", "columnAlignment": "LEFT"},
                        "flow": {"field": "FLOW_ID", "displayName": "Flow", "columnAlignment": "LEFT"},
                        "state": {"field": "STATE", "displayName": "State", "columnAlignment": "LEFT"},
                        "duration": {"field": "DURATION", "displayName": "Duration", "columnAlignment": "LEFT"},
                        "end_date": {"field": "END_DATE", "displayName": "End date", "columnAlignment": "LEFT"},
                        "namespace": {
                            "field": "NAMESPACE",
                            "displayName": "Namespace",
                            "columnAlignment": "LEFT"
                        },
                        "start_date": {
                            "field": "START_DATE",
                            "displayName": "Start date",
                            "columnAlignment": "LEFT"
                        }
                    }
                },
            } as any;
            return () => (
                <div style="padding: 20px; background: #f5f5f5; border-radius: 8px;">
                    <Table chart={chart}/>
                </div>
            );
        }
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        await expect(await canvas.findByText("2wJlDoXR")).toBeVisible();
        await expect(await canvas.findByText("2yiYHSqL")).toBeVisible();
        await expect(await canvas.findByText("2Iq5tjur")).toBeVisible();
        await expect(await canvas.findByText("69d95APm")).toBeVisible();
    }
}
