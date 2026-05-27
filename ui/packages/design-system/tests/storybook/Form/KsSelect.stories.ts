import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import {ElOption, ElTag} from "element-plus"
import {within, userEvent, expect} from "storybook/test"
import KsSelect from "../../../src/components/Form/KsSelect/KsSelect.vue"

const meta: Meta<typeof KsSelect> = {
    title: "Components/Form/KsSelect",
    component: KsSelect,
    tags: ["autodocs"],
    argTypes: {
        modelValue: {control: false},
        size: {control: "select", options: ["small", "default", "large"]},
        placeholder: {control: "text"},
        filterable: {control: "boolean"},
        clearable: {control: "boolean"},
        multiple: {control: "boolean"},
        collapseTags: {control: "boolean"},
        disabled: {control: "boolean"},
        allowCreate: {control: "boolean"},
    },
    parameters: {
        docs: {
            description: {
                component:
                    "KsSelect is the Kestra design-system abstraction over `ElSelect` from Element Plus. " +
                    "Only the props, events and slots actually used across the Kestra UI are exposed.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsSelect>

// ─── Shared setup helpers ─────────────────────────────────────────────────────

const STATUS_OPTIONS = [
    {value: "CREATED", label: "Created"},
    {value: "RUNNING", label: "Running"},
    {value: "PAUSED", label: "Paused"},
    {value: "SUCCESS", label: "Success"},
    {value: "WARNING", label: "Warning"},
    {value: "FAILED", label: "Failed"},
    {value: "KILLED", label: "Killed"},
    {value: "CANCELLED", label: "Cancelled", disabled: true},
]

const LOG_LEVELS = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]

const TIMEZONE_OPTIONS = [
    "UTC", "America/New_York", "America/Los_Angeles",
    "Europe/Paris", "Europe/London", "Asia/Tokyo",
    "Asia/Shanghai", "Australia/Sydney",
]

// ─── Stories ─────────────────────────────────────────────────────────────────

/** Single selection – basic usage */
export const Default: Story = {
    render: (args) => ({
        components: {KsSelect, ElOption},
        setup() {
            const value = ref("")
            return {args, value, STATUS_OPTIONS}
        },
        template: `
            <div style="padding:24px;min-height:320px;display:flex;flex-direction:column;gap:12px">
                <ks-select v-model="value" v-bind="args" style="width:240px">
                    <ks-option
                        v-for="opt in STATUS_OPTIONS"
                        :key="opt.value"
                        :value="opt.value"
                        :label="opt.label"
                        :disabled="opt.disabled"
                    />
                </ks-select>
                <span style="font-size:13px;opacity:0.6">Selected: {{ value || '(none)' }}</span>
            </div>
        `,
    }),
    args: {placeholder: "Select a status"},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const trigger = canvas.getByRole("combobox")
        await userEvent.click(trigger)
        const dropdown = document.querySelector(".kel-select-dropdown")
        await expect(dropdown).toBeTruthy()
        const runningOption = Array.from(document.querySelectorAll(".kel-select-dropdown__item")).find(
            (el) => el.textContent?.trim() === "Running",
        ) as HTMLElement | undefined
        if (runningOption) {
            await userEvent.click(runningOption)
        }
        await expect(trigger).toBeTruthy()
    },
}

/** Filterable – type to narrow options */
export const Filterable: Story = {
    render: (args) => ({
        components: {KsSelect, ElOption},
        setup() {
            const value = ref("")
            return {args, value, TIMEZONE_OPTIONS}
        },
        template: `
            <div style="padding:24px;min-height:320px;display:flex;flex-direction:column;gap:12px">
                <ks-select v-model="value" v-bind="args" style="width:280px">
                    <ks-option v-for="tz in TIMEZONE_OPTIONS" :key="tz" :value="tz" :label="tz" />
                </ks-select>
                <span style="font-size:13px;opacity:0.6">Selected: {{ value || '(none)' }}</span>
            </div>
        `,
    }),
    args: {placeholder: "Select timezone", filterable: true, clearable: true},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const trigger = canvas.getByRole("combobox")
        await userEvent.click(trigger)
        const dropdown = document.querySelector(".kel-select-dropdown")
        await expect(dropdown).toBeTruthy()
    },
}

/** Multiple selection with collapseTags */
export const Multiple: Story = {
    render: (args) => ({
        components: {KsSelect, ElOption},
        setup() {
            const value = ref<string[]>([])
            return {args, value, STATUS_OPTIONS}
        },
        template: `
            <div style="padding:24px;min-height:320px;display:flex;flex-direction:column;gap:12px">
                <ks-select v-model="value" v-bind="args" style="width:300px">
                    <ks-option v-for="opt in STATUS_OPTIONS" :key="opt.value" :value="opt.value" :label="opt.label" />
                </ks-select>
                <span style="font-size:13px;opacity:0.6">Selected: {{ value.join(', ') || '(none)' }}</span>
            </div>
        `,
    }),
    args: {multiple: true, collapseTags: true, filterable: true, clearable: true, placeholder: "Select statuses"},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const trigger = canvas.getByRole("combobox")
        await userEvent.click(trigger)
        const options = document.querySelectorAll(".kel-select-dropdown__item")
        if (options.length >= 2) {
            await userEvent.click(options[0] as HTMLElement)
            await userEvent.click(options[1] as HTMLElement)
        }
        const tags = canvasElement.querySelectorAll(".kel-tag")
        await expect(tags.length).toBeGreaterThan(0)
    },
}

/** Small size – as used in Pagination */
export const Sizes: Story = {
    render: () => ({
        components: {KsSelect, ElOption},
        setup() {
            const value = ref("25")
            const options = [
                {value: "10", label: "10 / page"},
                {value: "25", label: "25 / page"},
                {value: "50", label: "50 / page"},
                {value: "100", label: "100 / page"},
            ]
            return {value, options}
        },
        template: `
            <div style="padding:12px;">
                <ks-select v-model="value" size="small" style="width:130px">
                    <ks-option v-for="opt in options" :key="opt.value" :value="opt.value" :label="opt.label" />
                </ks-select>
            </div>
            <div style="padding:12px;">
                <ks-select v-model="value" style="width:130px">
                    <ks-option v-for="opt in options" :key="opt.value" :value="opt.value" :label="opt.label" />
                </ks-select>
            </div>
            <div style="padding:12px;">
                <ks-select v-model="value" size="large" style="width:130px">
                    <ks-option v-for="opt in options" :key="opt.value" :value="opt.value" :label="opt.label" />
                </ks-select>
            </div>
        `,
    }),
}

/** Disabled state */
export const Disabled: Story = {
    render: (args) => ({
        components: {KsSelect, ElOption},
        setup() {
            const value = ref("INFO")
            return {args, value, LOG_LEVELS}
        },
        template: `
            <div style="padding:24px;min-height:200px">
                <ks-select v-model="value" v-bind="args" style="width:200px">
                    <ks-option v-for="l in LOG_LEVELS" :key="l" :value="l" :label="l" />
                </ks-select>
            </div>
        `,
    }),
    args: {disabled: true, placeholder: "Log level"},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const trigger = canvas.getByRole("combobox")
        await expect(
            trigger.getAttribute("disabled") !== null || trigger.getAttribute("aria-disabled") === "true",
        ).toBe(true)
    },
}

/** Custom option content with coloured status dots */
export const CustomOptionContent: Story = {
    render: (args) => ({
        components: {KsSelect, ElOption},
        setup() {
            const value = ref("")
            const statuses = [
                {value: "CREATED",  color: "#6c757d"},
                {value: "RUNNING",  color: "#0d6efd"},
                {value: "SUCCESS",  color: "#198754"},
                {value: "WARNING",  color: "#ffc107"},
                {value: "FAILED",   color: "#dc3545"},
                {value: "KILLED",   color: "#6610f2"},
            ]
            return {args, value, statuses}
        },
        template: `
            <div style="padding:24px;min-height:320px;display:flex;flex-direction:column;gap:12px">
                <ks-select v-model="value" v-bind="args" style="width:240px">
                    <ks-option v-for="s in statuses" :key="s.value" :value="s.value" :label="s.value">
                        <span style="display:flex;align-items:center;gap:8px">
                            <span :style="{display:'inline-block',width:'10px',height:'10px',borderRadius:'50%',backgroundColor:s.color}" />
                            {{ s.value }}
                        </span>
                    </ks-option>
                </ks-select>
                <span style="font-size:13px;opacity:0.6">Selected: {{ value || '(none)' }}</span>
            </div>
        `,
    }),
    args: {filterable: true, clearable: true, placeholder: "Select status"},
}

/** Prefix slot – as used in DateSelect */
export const WithPrefixSlot: Story = {
    render: (args) => ({
        components: {KsSelect, ElOption},
        setup() {
            const value = ref("")
            const presets = [
                {value: "5m",  label: "Last 5 minutes"},
                {value: "1h",  label: "Last 1 hour"},
                {value: "24h", label: "Last 24 hours"},
                {value: "7d",  label: "Last 7 days"},
                {value: "30d", label: "Last 30 days"},
            ]
            return {args, value, presets}
        },
        template: `
            <div style="padding:24px;min-height:280px">
                <ks-select v-model="value" v-bind="args" style="width:220px">
                    <template #prefix>
                        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24"
                             fill="none" stroke="currentColor" stroke-width="2"
                             stroke-linecap="round" stroke-linejoin="round">
                            <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
                        </svg>
                    </template>
                    <ks-option v-for="p in presets" :key="p.value" :value="p.value" :label="p.label" />
                </ks-select>
            </div>
        `,
    }),
    args: {clearable: true, placeholder: "Select time range"},
}

/** allowCreate – type custom values – as used in TaskSubflowId */
export const AllowCreate: Story = {
    render: (args) => ({
        components: {KsSelect, ElOption},
        setup() {
            const value = ref<string | null>(null)
            const flows = ["my-flow", "etl-pipeline", "daily-report", "sync-users"]
            return {args, value, flows}
        },
        template: `
            <div style="padding:24px;min-height:280px;display:flex;flex-direction:column;gap:12px">
                <ks-select v-model="value" v-bind="args" style="width:280px">
                    <ks-option v-for="f in flows" :key="f" :value="f" :label="f" />
                </ks-select>
                <span style="font-size:13px;opacity:0.6">Value: {{ value ?? '(none)' }}</span>
            </div>
        `,
    }),
    args: {filterable: true, clearable: true, allowCreate: true, placeholder: "Select or type a flow id"},
}

/** Remote search – as used in NamespaceSelect */
export const RemoteSearch: Story = {
    render: (args) => ({
        components: {KsSelect, ElOption},
        setup() {
            const value = ref("")
            const results = ref<string[]>([])
            const loading = ref(false)
            const ALL_NS = [
                "company.team.payments", "company.team.logistics",
                "company.team.analytics", "company.data.raw",
                "company.data.curated", "company.infra.monitoring",
            ]
            function remoteMethod(query: string) {
                loading.value = true
                setTimeout(() => {
                    results.value = query ? ALL_NS.filter(ns => ns.includes(query)) : []
                    loading.value = false
                }, 300)
            }
            return {args, value, results, loading, remoteMethod}
        },
        template: `
            <div style="padding:24px;min-height:320px;display:flex;flex-direction:column;gap:12px">
                <ks-select v-model="value" v-bind="args" :loading="loading" :remoteMethod="remoteMethod" style="width:320px">
                    <ks-option v-for="ns in results" :key="ns" :value="ns" :label="ns" />
                </ks-select>
                <span style="font-size:13px;opacity:0.6">Selected: {{ value || '(none)' }}</span>
            </div>
        `,
    }),
    args: {remote: true, filterable: true, clearable: true, remoteShowSuffix: true, placeholder: "Type to search namespaces…"},
}

/** Custom tag slot – as used in NamespaceSelect */
export const CustomTagSlot: Story = {
    render: (args) => ({
        components: {KsSelect, ElOption, ElTag},
        setup() {
            const value = ref<string[]>([])
            const namespaces = [
                "company.team.payments", "company.team.logistics",
                "company.team.analytics", "company.data.raw", "company.data.curated",
            ]
            function remove(ns: string) {
                value.value = value.value.filter(v => v !== ns)
            }
            return {args, value, namespaces, remove}
        },
        template: `
            <div style="padding:24px;min-height:320px;display:flex;flex-direction:column;gap:12px">
                <ks-select v-model="value" v-bind="args" style="width:380px">
                    <template #tag>
                        <ks-tag
                            v-for="ns in value" :key="ns"
                            closable size="small" type="primary"
                            style="margin:2px"
                            @close="remove(ns)"
                        >{{ ns.split('.').pop() }}</ks-tag>
                    </template>
                    <ks-option v-for="ns in namespaces" :key="ns" :value="ns" :label="ns" />
                </ks-select>
                <span style="font-size:13px;opacity:0.6">Selected: {{ value.join(', ') || '(none)' }}</span>
            </div>
        `,
    }),
    args: {multiple: true, filterable: true, clearable: true, placeholder: "Select namespaces"},
}

/** Label slot – as used in Plugin.vue for version display */
export const WithLabelSlot: Story = {
    render: (args) => ({
        components: {KsSelect, ElOption},
        setup() {
            const value = ref("0.22.0")
            const versions = ["0.22.0", "0.21.3", "0.21.0", "0.20.5", "0.19.0"]
            return {args, value, versions}
        },
        template: `
            <div style="padding:24px;min-height:260px">
                <ks-select v-model="value" v-bind="args" style="width:220px">
                    <template #label="{ value: v }">
                        <span>Version: </span><strong>{{ v }}</strong>
                    </template>
                    <ks-option v-for="v in versions" :key="v" :value="v" :label="v" />
                </ks-select>
            </div>
        `,
    }),
    args: {size: "small", placeholder: "Version"},
}
