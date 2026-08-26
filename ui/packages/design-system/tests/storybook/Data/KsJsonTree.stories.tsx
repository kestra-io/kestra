import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, waitFor, within} from "storybook/test"
import KsJsonTree from "../../../src/components/Data/KsJsonTree.vue"
import {KsCard} from "@kestra-io/design-system"

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
    },
}

export default meta
type Story = StoryObj<typeof meta>

export const Object_Story: Story = {
    name: "Object",
    args: {value: NESTED_OBJECT, defaultExpanded: true},
    render: (args) => ({
        setup() {
            return () => (
                <KsCard style="font-size:13px;padding:1rem">
                    <KsJsonTree {...args} />
                </KsCard>
            )
        },
    }),
}

export const Array_Story: Story = {
    name: "Array",
    args: {value: ["production", "scheduled", "data-team", "priority-high"], defaultExpanded: true},
    render: (args) => ({
        setup() {
            return () => (
                <KsCard style="font-size:13px;padding:1rem">
                    <KsJsonTree {...args} />
                </KsCard>
            )
        },
    }),
}

export const Collapsed: Story = {
    args: {value: NESTED_OBJECT, defaultExpanded: false},
    render: (args) => ({
        setup() {
            return () => (
                <KsCard style="font-size:13px;padding:1rem">
                    <KsJsonTree {...args} />
                </KsCard>
            )
        },
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
        setup() {
            return () => (
                <KsCard style="font-size:13px;padding:1rem">
                    <KsJsonTree {...args} />
                </KsCard>
            )
        },
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
        setup() {
            return () => (
                <KsCard style="font-size:13px;padding:1rem">
                    <KsJsonTree {...args} />
                </KsCard>
            )
        },
    }),
}

export const RowsWithGutter: Story = {
    args: {
        value: NESTED_OBJECT,
        selectedPath: "execution.meta.executionId",
        basePath: "execution",
        defaultExpanded: true
    },
    render: (args) => ({
        setup() {
            return () => (
                <KsCard style="font-size:13px;padding:1rem">
                    <KsJsonTree {...args} />
                </KsCard>
            )
        },
    }),
}

/**
 * An empty container is not expandable, so it gets no collapsed preview and falls through to the
 * leaf display. `String({})` is `[object Object]` and `String([])` is the empty string, so both
 * used to render as something other than the value they are.
 */
export const EmptyContainers: Story = {
    args: {
        value: {outputFiles: {}, tags: [], nested: {inner: {}}, populated: {a: 1}},
        defaultExpanded: true,
    },
    render: (args) => ({
        setup() {
            return () => (
                <KsCard style="font-size:13px;padding:1rem">
                    <KsJsonTree {...args} />
                </KsCard>
            )
        },
    }),
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        const canvas = within(canvasElement)

        // Keys render quoted, so match the quoted form the component actually writes.
        await waitFor(() => expect(canvas.getByText("\"outputFiles\"")).toBeTruthy())
        expect(canvasElement.textContent).not.toContain("[object Object]")

        // One `{}` per empty object, and the empty array reads as `[]` rather than as a blank.
        expect(canvas.getAllByText("{}")).toHaveLength(2)
        expect(canvas.getByText("[]")).toBeTruthy()
    },
}

/**
 * A container that is the whole value and is empty yields no rows at all. The tree used to render
 * blank, which reads as a broken pane in the variable explorer, and hid a log line whose entire
 * message was `{}` — that message parses as structured, so the raw-message fallback is skipped.
 */
export const EmptyRoot: Story = {
    args: {value: {}, defaultExpanded: true},
    render: (args) => ({
        setup() {
            return () => (
                <KsCard style="font-size:13px;padding:1rem">
                    <KsJsonTree {...args} />
                </KsCard>
            )
        },
    }),
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        await waitFor(() => expect(within(canvasElement).getByText("{}")).toBeTruthy())
    },
}

export const EmptyRootArray: Story = {
    args: {value: [], defaultExpanded: true},
    render: (args) => ({
        setup() {
            return () => (
                <KsCard style="font-size:13px;padding:1rem">
                    <KsJsonTree {...args} />
                </KsCard>
            )
        },
    }),
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        await waitFor(() => expect(within(canvasElement).getByText("[]")).toBeTruthy())
    },
}
