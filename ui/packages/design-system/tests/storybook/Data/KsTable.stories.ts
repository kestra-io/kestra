import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsTable from "../../../src/components/Data/KsTable/KsTable.vue"
import KsTableColumn from "../../../src/components/Data/KsTable/KsTableColumn.vue"
import KsTag from "../../../src/components/Data/KsTag/KsTag.vue"

const SAMPLE_DATA = [
    {id: "flow-001", namespace: "company.team", status: "SUCCESS", duration: "1.2s"},
    {id: "flow-002", namespace: "company.data", status: "RUNNING", duration: "3.5s"},
    {id: "flow-003", namespace: "company.team", status: "FAILED", duration: "0.8s"},
    {id: "flow-004", namespace: "company.infra", status: "SUCCESS", duration: "2.1s"},
]

const meta: Meta<typeof KsTable> = {
    title: "Components/Data/KsTable",
    component: KsTable,
    tags: ["autodocs"],
    argTypes: {
        size: {control: "select", options: ["large", "default", "small"]},
    },
    parameters: {
        docs: {description: {component: "KsTable is the Kestra design-system abstraction over `ElTable` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsTable>

export const Default: Story = {
    args: {
        showHeader: true,
    },

    render: (args) => ({
        components: {KsTable, KsTableColumn, KsTag},
        setup() { return {args, SAMPLE_DATA} },
        template: `
            <div style="padding:24px">
                <ks-table :data="SAMPLE_DATA" v-bind="args">
                    <ks-table-column prop="id" label="Flow ID" />
                    <ks-table-column prop="namespace" label="Namespace" />
                    <ks-table-column prop="status" label="Status">
                        <template #default="{row}">
                            <ks-tag :type="row.status === 'SUCCESS' ? 'success' : row.status === 'RUNNING' ? 'primary' : 'danger'" size="small">
                                {{ row.status }}
                            </ks-tag>
                        </template>
                    </ks-table-column>
                    <ks-table-column prop="duration" label="Duration" />
                </ks-table>
            </div>
        `,
    }),
}

export const Sortable: Story = {
    render: () => ({
        components: {KsTable, KsTableColumn},
        setup() { return {SAMPLE_DATA} },
        template: `
            <div style="padding:24px">
                <ks-table :data="SAMPLE_DATA">
                    <ks-table-column prop="id" label="Flow ID" sortable />
                    <ks-table-column prop="namespace" label="Namespace" sortable />
                    <ks-table-column prop="duration" label="Duration" sortable />
                </ks-table>
            </div>
        `,
    }),
}

/** Striped rows */
export const Striped: Story = {
    render: () => ({
        components: {KsTable, KsTableColumn},
        setup() { return {SAMPLE_DATA} },
        template: `
            <div style="padding:24px">
                <ks-table :data="SAMPLE_DATA" stripe>
                    <ks-table-column prop="id" label="Flow ID" />
                    <ks-table-column prop="namespace" label="Namespace" />
                    <ks-table-column prop="status" label="Status" />
                    <ks-table-column prop="duration" label="Duration" />
                </ks-table>
            </div>
        `,
    }),
}

/** With border */
export const Border: Story = {
    render: () => ({
        components: {KsTable, KsTableColumn},
        setup() { return {SAMPLE_DATA} },
        template: `
            <div style="padding:24px">
                <ks-table :data="SAMPLE_DATA" border>
                    <ks-table-column prop="id" label="Flow ID" />
                    <ks-table-column prop="namespace" label="Namespace" />
                    <ks-table-column prop="status" label="Status" />
                    <ks-table-column prop="duration" label="Duration" />
                </ks-table>
            </div>
        `,
    }),
}

/** Fixed header – scrollable body */
export const FixedHeader: Story = {
    render: () => ({
        components: {KsTable, KsTableColumn},
        setup() {
            const data = Array.from({length: 15}, (_, i) => ({
                id: `flow-${String(i + 1).padStart(3, "0")}`,
                namespace: i % 2 === 0 ? "company.team" : "company.data",
                status: ["SUCCESS", "RUNNING", "FAILED"][i % 3],
                duration: `${(Math.random() * 5 + 0.5).toFixed(1)}s`,
            }))
            return {data}
        },
        template: `
            <div style="padding:24px">
                <ks-table :data="data" max-height="200">
                    <ks-table-column prop="id" label="Flow ID" />
                    <ks-table-column prop="namespace" label="Namespace" />
                    <ks-table-column prop="status" label="Status" />
                    <ks-table-column prop="duration" label="Duration" />
                </ks-table>
            </div>
        `,
    }),
}

/** Multiple selection with checkboxes */
export const MultipleSelect: Story = {
    render: () => ({
        components: {KsTable, KsTableColumn, KsTag},
        setup() {
            const selected = ref<any[]>([])
            return {SAMPLE_DATA, selected}
        },
        template: `
            <div style="padding:24px">
                <ks-table :data="SAMPLE_DATA" @selection-change="selected = $event">
                    <ks-table-column type="selection" width="50" />
                    <ks-table-column prop="id" label="Flow ID" />
                    <ks-table-column prop="namespace" label="Namespace" />
                    <ks-table-column prop="status" label="Status" />
                </ks-table>
                <p style="margin-top:8px;font-size:13px;opacity:0.6">
                    Selected: {{ selected.map(r => r.id).join(', ') || 'none' }}
                </p>
            </div>
        `,
    }),
}

/** Custom column template via slot */
export const CustomColumn: Story = {
    render: () => ({
        components: {KsTable, KsTableColumn, KsTag},
        setup() { return {SAMPLE_DATA} },
        template: `
            <div style="padding:24px">
                <ks-table :data="SAMPLE_DATA">
                    <ks-table-column prop="id" label="Flow ID" />
                    <ks-table-column prop="namespace" label="Namespace" />
                    <ks-table-column label="Status">
                        <template #default="{row}">
                            <ks-tag
                                :type="row.status === 'SUCCESS' ? 'success' : row.status === 'RUNNING' ? 'primary' : 'danger'"
                                size="small"
                            >{{ row.status }}</ks-tag>
                        </template>
                    </ks-table-column>
                    <ks-table-column label="Actions">
                        <template #default="{row}">
                            <button style="font-size:12px;cursor:pointer" @click="() => {}">View {{ row.id }}</button>
                        </template>
                    </ks-table-column>
                </ks-table>
            </div>
        `,
    }),
}

/** Expandable row */
export const ExpandableRow: Story = {
    render: () => ({
        components: {KsTable, KsTableColumn},
        setup() { return {SAMPLE_DATA} },
        template: `
            <div style="padding:24px">
                <ks-table :data="SAMPLE_DATA">
                    <ks-table-column type="expand">
                        <template #default="{row}">
                            <div style="padding:12px;font-size:13px;opacity:0.7">
                                <strong>Flow ID:</strong> {{ row.id }}<br/>
                                <strong>Namespace:</strong> {{ row.namespace }}<br/>
                                <strong>Duration:</strong> {{ row.duration }}<br/>
                                <strong>Status:</strong> {{ row.status }}
                            </div>
                        </template>
                    </ks-table-column>
                    <ks-table-column prop="id" label="Flow ID" />
                    <ks-table-column prop="namespace" label="Namespace" />
                    <ks-table-column prop="status" label="Status" />
                </ks-table>
            </div>
        `,
    }),
}

export const Empty: Story = {
    render: () => ({
        components: {KsTable, KsTableColumn},
        template: `
            <div style="padding:24px">
                <ks-table :data="[]" empty-text="No flows found">
                    <ks-table-column prop="id" label="Flow ID" />
                    <ks-table-column prop="namespace" label="Namespace" />
                </ks-table>
            </div>
        `,
    }),
}
