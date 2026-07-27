import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsJsonTree from "../../../src/components/Data/KsJsonTree.vue"

const NESTED_OBJECT = {
    event: "deploy.completed",
    status: "success",
    duration: 1840,
    timestamp: "2026-06-04T13:33:56.680Z",
    meta: {
        namespace: "company.data",
        flowId: "etl-pipeline",
        executionId: "4Q9z27FJ26FRIhdv037HtF",
    },
    tags: ["production", "scheduled"],
    error: null,
    retried: false,
}

const meta: Meta<typeof KsJsonTree> = {
    title: "Data/KsJsonTree",
    component: KsJsonTree,
    tags: ["autodocs"],
    argTypes: {
        defaultExpanded: {control: "boolean"},
        depth: {control: "number"},
    },
}

export default meta
type Story = StoryObj<typeof meta>

export const Object_: Story = {
    name: "Object",
    args: {value: NESTED_OBJECT, defaultExpanded: true},
    render: (args) => ({
        components: {KsJsonTree},
        setup() { return {args} },
        template: "<ks-card style=\"font-size:13px;padding:1rem\"><KsJsonTree v-bind=\"args\" /></ks-card>",
    }),
}

export const Array_: Story = {
    name: "Array",
    args: {value: ["production", "scheduled", "data-team", "priority-high"], defaultExpanded: true},
    render: (args) => ({
        components: {KsJsonTree},
        setup() { return {args} },
        template: "<ks-card style=\"font-size:13px;padding:1rem\"><KsJsonTree v-bind=\"args\" /></ks-card>",
    }),
}

export const Collapsed: Story = {
    args: {value: NESTED_OBJECT, defaultExpanded: false},
    render: (args) => ({
        components: {KsJsonTree},
        setup() { return {args} },
        template: "<ks-card style=\"font-size:13px;padding:1rem\"><KsJsonTree v-bind=\"args\" /></ks-card>",
    }),
}

export const DeeplyNested: Story = {
    args: {
        value: {
            level1: {
                level2: {
                    level3: {level4: {value: "deep"}, array: [1, 2, 3]},
                    sibling: true,
                },
                count: 42,
            },
            topLevel: "string",
        },
        defaultExpanded: true,
    },
    render: (args) => ({
        components: {KsJsonTree},
        setup() { return {args} },
        template: "<ks-card style=\"font-size:13px;padding:1rem\"><KsJsonTree v-bind=\"args\" /></ks-card>",
    }),
}

export const MixedTypes: Story = {
    args: {
        value: {
            string: "hello world",
            number: 3.14,
            boolean: true,
            null_: null,
            array: [1, "two", false, null],
            nested: {a: 1, b: 2},
        },
        defaultExpanded: true,
    },
    render: (args) => ({
        components: {KsJsonTree},
        setup() { return {args} },
        template: "<ks-card style=\"font-size:13px;padding:1rem\"><KsJsonTree v-bind=\"args\" /></ks-card>",
    }),
}

export const Leaf: Story = {
    args: {value: "a plain string value", nodeKey: "message"},
    render: (args) => ({
        components: {KsJsonTree},
        setup() { return {args} },
        template: "<ks-card style=\"font-size:13px;padding:1rem\"><KsJsonTree v-bind=\"args\" /></ks-card>",
    }),
}
