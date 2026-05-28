import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsDataTable from "../../../src/components/Data/KsDataTable/KsDataTable.vue"
import KsTableColumn from "../../../src/components/Data/KsTable/KsTableColumn.vue"
import KsTag from "../../../src/components/Data/KsTag/KsTag.vue"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"
import KsInput from "../../../src/components/Form/KsInput.vue"

const SAMPLE_DATA = Array.from({length: 30}, (_, i) => ({
    id: `flow-${String(i + 1).padStart(3, "0")}`,
    namespace: ["company.team", "company.data", "company.infra"][i % 3],
    status: ["SUCCESS", "RUNNING", "FAILED"][i % 3],
    duration: `${(Math.random() * 5 + 0.5).toFixed(1)}s`,
}))

const meta: Meta<typeof KsDataTable> = {
    title: "Components/Data/KsDataTable",
    component: KsDataTable,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component: "KsDataTable is an all-in-one data table with built-in pagination, row selection (with shift-click range), loading state, and optional filter navbar.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsDataTable>

export const Default: Story = {
    render: () => ({
        components: {KsDataTable, KsTableColumn, KsTag},
        setup() {
            const page = ref(1)
            const size = ref(10)
            const pagedData = ref(SAMPLE_DATA.slice(0, 10))
            const onPageChanged = ({page: p, size: s}: {page: number; size: number}) => {
                page.value = p
                size.value = s
                pagedData.value = SAMPLE_DATA.slice((p - 1) * s, p * s)
            }
            return {pagedData, page, size, total: SAMPLE_DATA.length, onPageChanged}
        },
        template: `
            <div style="padding: 24px">
                <ks-data-table
                    :data="pagedData"
                    :total="total"
                    :current-page="page"
                    :page-size="size"
                    @page-changed="onPageChanged"
                >
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
                </ks-data-table>
            </div>
        `,
    }),
}

export const WithNavbar: Story = {
    render: () => ({
        components: {KsDataTable, KsTableColumn, KsTag, KsInput},
        setup() {
            const search = ref("")
            const page = ref(1)
            const size = ref(10)
            const filteredData = ref(SAMPLE_DATA.slice(0, 10))
            const total = ref(SAMPLE_DATA.length)

            const onFilter = () => {
                const filtered = SAMPLE_DATA.filter(r =>
                    r.id.includes(search.value) || r.namespace.includes(search.value),
                )
                total.value = filtered.length
                filteredData.value = filtered.slice(0, size.value)
                page.value = 1
            }

            const onPageChanged = ({page: p, size: s}: {page: number; size: number}) => {
                page.value = p
                size.value = s
                const filtered = SAMPLE_DATA.filter(r =>
                    r.id.includes(search.value) || r.namespace.includes(search.value),
                )
                filteredData.value = filtered.slice((p - 1) * s, p * s)
            }

            return {search, filteredData, total, page, size, onFilter, onPageChanged}
        },
        template: `
            <div style="padding: 24px">
                <ks-data-table
                    :data="filteredData"
                    :total="total"
                    :current-page="page"
                    :page-size="size"
                    @page-changed="onPageChanged"
                >
                    <template #navbar>
                        <ks-input v-model="search" placeholder="Search flows..." @input="onFilter" style="width: 250px" />
                    </template>

                    <ks-table-column prop="id" label="Flow ID" sortable />
                    <ks-table-column prop="namespace" label="Namespace" sortable />
                    <ks-table-column prop="status" label="Status">
                        <template #default="{row}">
                            <ks-tag :type="row.status === 'SUCCESS' ? 'success' : row.status === 'RUNNING' ? 'primary' : 'danger'" size="small">
                                {{ row.status }}
                            </ks-tag>
                        </template>
                    </ks-table-column>
                    <ks-table-column prop="duration" label="Duration" />
                </ks-data-table>
            </div>
        `,
    }),
}

export const WithSelection: Story = {
    render: () => ({
        components: {KsDataTable, KsTableColumn, KsTag, KsButton},
        setup() {
            const page = ref(1)
            const size = ref(10)
            const pagedData = ref(SAMPLE_DATA.slice(0, 10))
            const selection = ref<typeof SAMPLE_DATA>([])

            const onPageChanged = ({page: p, size: s}: {page: number; size: number}) => {
                page.value = p
                size.value = s
                pagedData.value = SAMPLE_DATA.slice((p - 1) * s, p * s)
            }

            const onSelectionChange = (rows: typeof SAMPLE_DATA) => {
                selection.value = rows
            }

            return {pagedData, page, size, total: SAMPLE_DATA.length, selection, onPageChanged, onSelectionChange}
        },
        template: `
            <div style="padding: 24px">
                <ks-data-table
                    :data="pagedData"
                    :total="total"
                    :current-page="page"
                    :page-size="size"
                    :selectable="true"
                    row-key="id"
                    @page-changed="onPageChanged"
                    @selection-change="onSelectionChange"
                >
                    <template #bulk-actions>
                        <ks-button @click="() => {}">Delete selected</ks-button>
                    </template>

                    <ks-table-column prop="id" label="Flow ID" />
                    <ks-table-column prop="namespace" label="Namespace" />
                    <ks-table-column prop="status" label="Status">
                        <template #default="{row}">
                            <ks-tag :type="row.status === 'SUCCESS' ? 'success' : row.status === 'RUNNING' ? 'primary' : 'danger'" size="small">
                                {{ row.status }}
                            </ks-tag>
                        </template>
                    </ks-table-column>
                </ks-data-table>
                <p style="margin-top: 8px; font-size: 13px; opacity: 0.6">
                    Selected: {{ selection.map(r => r.id).join(', ') || 'none' }}
                    <br /><small>Shift+click to range-select rows</small>
                </p>
            </div>
        `,
    }),
}

export const Loading: Story = {
    render: () => ({
        components: {KsDataTable, KsTableColumn},
        setup() {
            const isLoading = ref(true)
            setTimeout(() => { isLoading.value = false }, 2000)
            return {isLoading, SAMPLE_DATA: SAMPLE_DATA.slice(0, 5), total: SAMPLE_DATA.length}
        },
        template: `
            <div style="padding: 24px">
                <ks-data-table
                    :data="SAMPLE_DATA"
                    :total="total"
                    :loading="isLoading"
                >
                    <ks-table-column prop="id" label="Flow ID" />
                    <ks-table-column prop="namespace" label="Namespace" />
                    <ks-table-column prop="status" label="Status" />
                </ks-data-table>
            </div>
        `,
    }),
}

export const Empty: Story = {
    render: () => ({
        components: {KsDataTable, KsTableColumn},
        template: `
            <div style="padding: 24px">
                <ks-data-table :data="[]" :total="0" no-data-text="No flows found">
                    <ks-table-column prop="id" label="Flow ID" />
                    <ks-table-column prop="namespace" label="Namespace" />
                    <ks-table-column prop="status" label="Status" />
                </ks-data-table>
            </div>
        `,
    }),
}

export const CustomContent: Story = {
    name: "Custom #table Slot",
    render: () => ({
        components: {KsDataTable, KsTag},
        setup() {
            const page = ref(1)
            const size = ref(9)
            const pagedData = ref(SAMPLE_DATA.slice(0, 9))
            const onPageChanged = ({page: p, size: s}: {page: number; size: number}) => {
                page.value = p
                size.value = s
                pagedData.value = SAMPLE_DATA.slice((p - 1) * s, p * s)
            }
            return {pagedData, total: SAMPLE_DATA.length, page, size, onPageChanged}
        },
        template: `
            <div style="padding: 24px">
                <ks-data-table :total="total" :current-page="page" :page-size="size" @page-changed="onPageChanged">
                    <template #table>
                        <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; padding: 8px 0">
                            <div
                                v-for="row in pagedData"
                                :key="row.id"
                                style="border: 1px solid var(--ks-border-primary); border-radius: 8px; padding: 12px"
                            >
                                <strong style="font-size: 13px">{{ row.id }}</strong>
                                <p style="margin: 4px 0; font-size: 12px; color: var(--ks-text-secondary)">{{ row.namespace }}</p>
                                <ks-tag :type="row.status === 'SUCCESS' ? 'success' : row.status === 'RUNNING' ? 'primary' : 'danger'" size="small">
                                    {{ row.status }}
                                </ks-tag>
                            </div>
                        </div>
                    </template>
                </ks-data-table>
            </div>
        `,
    }),
}
