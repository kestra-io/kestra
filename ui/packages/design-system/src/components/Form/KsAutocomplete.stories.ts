import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref, type ConcreteComponent} from "vue"
import KsAutocomplete from "./KsAutocomplete.vue"

const SUGGESTIONS = [
    "company.team.payments",
    "company.team.logistics",
    "company.team.analytics",
    "company.data.raw",
    "company.data.curated",
    "company.infra.monitoring",
]

const meta: Meta<typeof KsAutocomplete> = {
    title: "Components/Form/KsAutocomplete",
    // A generic <script setup> component types as a bare function, which Storybook's component field does not accept.
    component: KsAutocomplete as ConcreteComponent,
    tags: ["autodocs"],
    argTypes: {
        disabled: {control: "boolean"},
        clearable: {control: "boolean"},
        triggerOnFocus: {control: "boolean"},
    },
    parameters: {
        docs: {description: {component: "KsAutocomplete is the Kestra design-system abstraction over `ElAutocomplete` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsAutocomplete>

export const Default: Story = {
    render: (args) => ({
        components: {KsAutocomplete},
        setup() {
            const value = ref("")
            function fetchSuggestions(query: string, callback: (results: {value: string}[]) => void) {
                const results = SUGGESTIONS
                    .filter(s => s.includes(query))
                    .map(s => ({value: s}))
                callback(results)
            }
            return {args, value, fetchSuggestions}
        },
        template: `
            <div style="padding:24px;width:360px">
                <ks-autocomplete
                    v-model="value"
                    :fetch-suggestions="fetchSuggestions"
                    v-bind="args"
                />
                <span style="display:block;margin-top:8px;font-size:13px;opacity:0.6">Value: {{ value || '(none)' }}</span>
            </div>
        `,
    }),
    args: {placeholder: "Search namespaces...", disabled: false, clearable: true, triggerOnFocus: true, valueKey: "value"},
}

/** Disabled state */
export const Disabled: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsAutocomplete},
        setup() {
            const fetchSuggestions = (_q: string, cb: (r: {value: string}[]) => void) => cb([])
            return {fetchSuggestions}
        },
        template: `
            <div style="padding:24px;width:360px">
                <ks-autocomplete disabled placeholder="Not available" :fetch-suggestions="fetchSuggestions" />
            </div>
        `,
    }),
}

/** Remote search – suggestions fetched asynchronously */
export const RemoteSearch: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsAutocomplete},
        setup() {
            const value = ref("")
            const databases = ["PostgreSQL", "MySQL", "MongoDB", "Redis", "Elasticsearch", "ClickHouse", "BigQuery"]
            function fetchSuggestions(query: string, callback: (results: {value: string}[]) => void) {
                setTimeout(() => {
                    const results = databases
                        .filter(d => d.toLowerCase().includes(query.toLowerCase()))
                        .map(d => ({value: d}))
                    callback(results)
                }, 200)
            }
            return {value, fetchSuggestions}
        },
        template: `
            <div style="padding:24px;width:360px;min-height:280px">
                <ks-autocomplete
                    v-model="value"
                    :fetch-suggestions="fetchSuggestions"
                    placeholder="Search databases..."
                    clearable
                />
                <span style="display:block;margin-top:8px;font-size:13px;opacity:0.6">
                    Selected: {{ value || '(none)' }}
                </span>
            </div>
        `,
    }),
}

export const WithCustomTemplate: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsAutocomplete},
        setup() {
            const value = ref("")
            function fetchSuggestions(query: string, callback: (results: {value: string; count: number}[]) => void) {
                const data = [
                    {value: "my-flow", count: 42},
                    {value: "etl-pipeline", count: 18},
                    {value: "daily-report", count: 7},
                ].filter(item => item.value.includes(query))
                callback(data)
            }
            return {value, fetchSuggestions}
        },
        template: `
            <div style="padding:24px;width:360px;min-height:300px">
                <ks-autocomplete
                    v-model="value"
                    :fetch-suggestions="fetchSuggestions"
                    placeholder="Search flows..."
                    :trigger-on-focus="true"
                >
                    <template #default="{ item }">
                        <span style="display:flex;justify-content:space-between">
                            <span>{{ item.value }}</span>
                            <span style="opacity:0.5;font-size:12px">{{ item.count }} runs</span>
                        </span>
                    </template>
                </ks-autocomplete>
            </div>
        `,
    }),
}

/** Mobile – on touch screens (`pointer: coarse`) the field is 44px high with 16px text */
export const Mobile: Story = {
    parameters: {
        controls: {disable: true},
        viewport: {
            options: {
                mobile: {name: "Mobile", styles: {width: "390px", height: "844px"}},
            },
        },
    },
    globals: {
        viewport: {value: "mobile", isRotated: false},
    },
    render: () => ({
        components: {KsAutocomplete},
        setup() {
            const value = ref("")
            const fetchSuggestions = (query: string, callback: (results: {value: string}[]) => void) => {
                callback(SUGGESTIONS.filter((item) => item.includes(query)).map((item) => ({value: item})))
            }
            return {value, fetchSuggestions}
        },
        template: `
            <div style="padding:16px">
                <ks-autocomplete v-model="value" placeholder="Namespace" :fetch-suggestions="fetchSuggestions" trigger-on-focus />
            </div>
        `,
    }),
}
