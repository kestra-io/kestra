import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref, type ConcreteComponent} from "vue"
import KsTable from "./KsTable.vue"
import KsTableColumn from "./KsTableColumn.vue"
import KsTag from "../KsTag/KsTag.vue"
import KsButton from "../../Basic/KsButton/KsButton.vue"
import KsButtonGroup from "../../Basic/KsButton/KsButtonGroup.vue"

const SAMPLE_DATA = [
    {id: "flow-001", namespace: "company.team", status: "SUCCESS", duration: "1.2s"},
    {id: "flow-002", namespace: "company.data", status: "RUNNING", duration: "3.5s"},
    {id: "flow-003", namespace: "company.team", status: "FAILED", duration: "0.8s"},
    {id: "flow-004", namespace: "company.infra", status: "SUCCESS", duration: "2.1s"},
]

const meta: Meta<typeof KsTable> = {
    title: "Components/Data/KsTable",
    // A generic <script setup> component types as a bare function, which Storybook's component field does not accept.
    component: KsTable as ConcreteComponent,
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
        size: "default",
        tableLayout: "fixed",
        fit: true,
        emptyText: "No flows found",
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
    parameters: {controls: {disable: true}},
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
    parameters: {controls: {disable: true}},
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
    parameters: {controls: {disable: true}},
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
    parameters: {controls: {disable: true}},
}

/** Multiple selection with checkboxes */
export const MultipleSelect: Story = {
    render: () => ({
        components: {KsTable, KsTableColumn, KsTag},
        setup() {
            const selected = ref<typeof SAMPLE_DATA[number][]>([])
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
    parameters: {controls: {disable: true}},
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
    parameters: {controls: {disable: true}},
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
    parameters: {controls: {disable: true}},
}

/** Links and button-styled anchors inside cells: a plain link takes the primary text color, while an anchor rendered by KsButton (tag="a") keeps its button styling. */
export const LinksAndButtonAnchors: Story = {
    render: () => ({
        components: {KsTable, KsTableColumn, KsButton, KsButtonGroup},
        setup() { return {SAMPLE_DATA} },
        template: `
            <div style="padding:24px">
                <ks-table :data="SAMPLE_DATA">
                    <ks-table-column prop="id" label="Flow ID" />
                    <ks-table-column label="Link">
                        <template #default="{row}">
                            <a href="#">{{ row.namespace }}</a>
                        </template>
                    </ks-table-column>
                    <ks-table-column label="Actions">
                        <template #default>
                            <ks-button-group>
                                <ks-button tag="a" type="primary" size="small" href="#">Download</ks-button>
                                <ks-button type="primary" size="small">Preview</ks-button>
                            </ks-button-group>
                        </template>
                    </ks-table-column>
                </ks-table>
            </div>
        `,
    }),
    parameters: {controls: {disable: true}},
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
    parameters: {controls: {disable: true}},
}
