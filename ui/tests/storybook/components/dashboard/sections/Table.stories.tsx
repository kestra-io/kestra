import Table from "../../../../../src/components/dashboard/sections/Table.vue";
import type {Chart} from "../../../../../src/components/dashboard/types.ts";
import type {Meta, StoryObj} from "@storybook/vue3-vite";
import {vueRouter} from "storybook-vue3-router";
import {setMockClient} from "@kestra-io/kestra-sdk"
import {expect, within} from "storybook/test";

const meta: Meta<typeof Table> = {
    title: "Dashboard/Sections/Table",
    component: Table,
    decorators: [
        vueRouter([
            {
                path: "/",
                component: {template: "<div></div>"}
            },
            {
                path: "/:tenant?/flows/edit/:namespace/:id/:tab?",
                name: "flows/update",
                component: {template: "<div></div>"}
            },
            {
                path: "/:tenant?/executions/:namespace/:flowId/:id/:tab?",
                name: "executions/update",
                component: {template: "<div></div>"}
            },
            {
                path: "/:tenant?/namespaces/edit/:id/:tab?",
                name: "namespaces/update",
                component: {template: "<div></div>"}
            },
        ])
    ]
}

export default meta;

const id1 = "2wJlDoXRsMc7jXJfQUWTE7"
const id2 = "2yiYHSqLwNbocm9FB8qK5L"
const id3 = "2Iq5tjur4bB9fRYYazstV4"
const id4 = "69d95APmpdw94OkaMduCep"

export const SimpleExecutionsCase: StoryObj<typeof Table> = {
    render: () => ({
        setup() {
            const store = {} as any;
            store.post = async function (uri: string) {
                if (uri.includes("charts/executions_finished")) {
                    return {
                        data: {
                            results: [
                                {
                                    "namespace": "company.team",
                                    "id": id1,
                                    "state": "RUNNING",
                                    "flow": "sleep",
                                    "start_date": "2025-11-25T09:28:00.000+00:00",
                                },
                                {
                                    "namespace": "company.team",
                                    "id": id2,
                                    "state": "RUNNING",
                                    "flow": "sleep",
                                    "start_date": "2025-11-25T09:28:00.000+00:00"
                                },
                                {
                                    "duration": 6,
                                    "namespace": "company.team",
                                    "id": id3,
                                    "state": "SUCCESS",
                                    "flow": "sleep",
                                    "end_date": "2025-11-25T09:27:00.000+00:00",
                                    "start_date": "2025-11-25T09:27:00.000+00:00",
                                },
                                {
                                    "namespace": "company.team",
                                    "id": id4,
                                    "state": "RUNNING",
                                    "flow": "sleep",
                                    "start_date": "2025-11-25T09:27:00.000+00:00"
                                }
                            ],
                            total: 4
                        }
                    }
                } else {
                    console.warn("Unknown URI", uri)
                }
                return {results: []}
            }

            setMockClient(store);

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
                    <Table chart={chart} dashboardId="default" />
                </div>
            );
        }
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        await expect(await canvas.findByText(id1.slice(0, 8))).toBeVisible();
        await expect(await canvas.findByText(id2.slice(0, 8))).toBeVisible();
        await expect(await canvas.findByText(id3.slice(0, 8))).toBeVisible();
        await expect(await canvas.findByText(id4.slice(0, 8))).toBeVisible();
    }
}
