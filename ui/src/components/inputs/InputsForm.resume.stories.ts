import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import {expect, userEvent, waitFor, within} from "storybook/test"
import InputsForm from "./InputsForm.vue"
import {useExecutionsStore, type Execution, type InputMetaData} from "../../stores/executions"

const initialInputs: InputMetaData[] = [
    {id: "count", type: "INT", defaults: "{{ outputs.prepare.values.count }}", description: "{{ outputs.prepare.values.description }}", required: true},
    {id: "approved", type: "BOOL", defaults: "{{ outputs.prepare.values.approved }}", required: false},
]

const meta: Meta<typeof InputsForm> = {
    title: "inputs/InputsForm/Resume",
    component: InputsForm,
    render: () => ({
        components: {InputsForm},
        setup() {
            const store = useExecutionsStore()
            store.validateResume = async ({formData}) => ({
                inputs: initialInputs.map(input => {
                    const submitted = formData?.get(input.id)
                    return {
                        enabled: true,
                        isDefault: submitted == null,
                        value: input.type === "INT" ? Number(submitted ?? 0) : submitted === "true",
                        input: {...input, description: input.id === "count" ? "Days remaining for holiday" : undefined},
                    }
                }),
            })
            const values = ref<Record<string, unknown>>({})
            const execution: Execution = {id: "paused-execution", namespace: "company.team", flowId: "approval", flowRevision: 1, state: {current: "PAUSED", histories: []}}
            return {initialInputs, values, execution}
        },
        template: "<InputsForm :initialInputs=\"initialInputs\" :execution=\"execution\" v-model=\"values\" /><pre data-testid=\"resume-values\">{{ JSON.stringify(values) }}</pre>",
    }),
}
export default meta
type Story = StoryObj<typeof InputsForm>

export const DynamicDefaults: Story = {
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const count = canvas.getByRole("spinbutton")
        await waitFor(() => expect(count).toHaveValue(0))
        await expect(await canvas.findByText("Days remaining for holiday")).toBeVisible()
        await expect(canvas.getByRole("switch")).not.toBeChecked()
        await waitFor(() => expect(canvas.getByTestId("resume-values")).toHaveTextContent("\"approved\":false"))
        await userEvent.clear(count)
        await userEvent.type(count, "2")
        await userEvent.tab()
        await waitFor(() => expect(canvas.getByTestId("resume-values")).toHaveTextContent("\"count\":2"))
        await expect(count).toHaveValue(2)
    },
}

export const DynamicDefaultsDark: Story = {
    ...DynamicDefaults,
    globals: {theme: "dark"},
}
