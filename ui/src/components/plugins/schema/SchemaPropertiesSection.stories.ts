import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, userEvent, within} from "storybook/test"
import {KsMarkdown} from "@kestra-io/design-system"
import SchemaPropertiesSection from "./SchemaPropertiesSection.vue"

const APPROVAL_VALUES = ["CANCEL", "CONTINUE", "FAIL", "KILL", "SUCCEED", "WARN"]

const meta = {
    title: "Components/Plugins/SchemaPropertiesSection",
    component: SchemaPropertiesSection,
    args: {
        sectionName: "Properties",
        initiallyExpanded: true,
        noUrlChange: true,
        properties: {
            approveBehavior: {
                type: "string",
                $dynamic: true,
                default: "CONTINUE",
                title: "Behavior after approval.",
                description: "The action to take when the execution is approved.",
                anyOf: [{enum: APPROVAL_VALUES}, {pattern: ".*"}],
            },
            behavior: {type: "string", enum: ["QUEUE", "CANCEL"]},
            states: {type: "array", items: {type: "string", enum: ["SUCCESS", "FAILED"]}},
            levels: {type: "object", additionalProperties: {type: "string", enum: ["INFO", "ERROR"]}},
            message: {type: "string", $dynamic: true, anyOf: [{type: "string"}, {type: "integer"}]},
            timezone: {type: "string", $dynamic: false, anyOf: [{enum: ["Europe/Paris", "UTC"]}, {pattern: ".*"}]},
        },
    },
    render: (args) => ({
        components: {SchemaPropertiesSection, KsMarkdown},
        setup: () => ({args}),
        template: `<SchemaPropertiesSection v-bind="args">
            <template #markdown="{content}"><KsMarkdown :content="content" /></template>
        </SchemaPropertiesSection>`,
    }),
} satisfies Meta<typeof SchemaPropertiesSection>

export default meta
type Story = StoryObj<typeof meta>

export const Compact: Story = {
    args: {compact: true},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        for (const value of APPROVAL_VALUES) {
            expect(canvas.getAllByText(value).some(element => element.checkVisibility())).toBe(true)
        }
        expect(canvas.getAllByText("Possible Values")).toHaveLength(4)
        for (const value of ["QUEUE", "SUCCESS", "FAILED", "INFO", "ERROR"]) {
            expect(canvas.getByText(value)).toBeVisible()
        }
        expect(canvas.queryByText("Europe/Paris")).not.toBeInTheDocument()
        expect(canvas.queryByText("UTC")).not.toBeInTheDocument()
    },
}

export const Full: Story = {
    args: {compact: false},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        for (const name of Object.keys(meta.args.properties)) {
            await userEvent.click(canvas.getByText(name, {exact: true}))
        }
        expect(canvas.getAllByText("Possible Values")).toHaveLength(4)
        for (const value of ["FAIL", "WARN", "QUEUE", "SUCCESS", "INFO"]) {
            expect(canvas.getByText(value)).toBeVisible()
        }
        expect(canvas.queryByText("Europe/Paris")).not.toBeInTheDocument()
    },
}
