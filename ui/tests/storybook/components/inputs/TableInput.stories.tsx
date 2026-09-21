import {ref} from "vue"
import type {Meta, StoryFn} from "@storybook/vue3-vite"
import {expect, userEvent, waitFor} from "storybook/test"
import TableInput from "../../../../src/components/inputs/TableInput.vue"
import type {InputMetaData} from "../../../../src/stores/executions"

export default {
    title: "inputs/TableInput",
    component: TableInput,
    parameters: {
        docs: {
            description: {
                component:
                    "The renderer for a `TABLE` flow input: a row is a record of the declared columns, and the user adds as many as the flow allows. The value is the JSON array of objects the execution is created with.",
            },
        },
    },
} as Meta<typeof TableInput>

const disks: InputMetaData = {
    id: "disks",
    type: "TABLE",
    rows: {min: 1, max: 3},
    columns: [
        {id: "size_gb", type: "INT", min: 10, max: 2048},
        {id: "name", type: "STRING"},
        {id: "mountpoint", type: "STRING"},
    ],
}

const Template: StoryFn<typeof TableInput> = (args) => ({
    setup() {
        const model = ref(args.modelValue)
        return () => (
            <div style="padding:24px;max-width:720px">
                <TableInput {...args} modelValue={model.value} onUpdate:modelValue={(value: string | undefined) => model.value = value} />
                <pre data-test="story-payload" style="margin-top:16px">{model.value ?? "undefined"}</pre>
            </div>
        )
    },
})

/** `rows.min: 1` means the grid opens on one row rather than asking the user to add it. */
export const Default = Template.bind({})
Default.args = {input: disks}
Default.play = async ({canvasElement}) => {
    const addRow = canvasElement.querySelector("[data-test='table-row-add-disks']")!
    await expect(canvasElement.querySelectorAll("tbody tr").length).toBe(1)
    await expect(canvasElement.querySelector("[data-test='table-row-remove-disks-0']")).toBeDisabled()

    await userEvent.click(addRow)
    await userEvent.click(addRow)
    await waitFor(() => expect(canvasElement.querySelectorAll("tbody tr").length).toBe(3))
    await expect(addRow).toBeDisabled()
}

export const Prefilled = Template.bind({})
Prefilled.args = {
    input: disks,
    modelValue: JSON.stringify([
        {size_gb: 10, name: "root", mountpoint: "/dev/sda"},
        {size_gb: 20, name: "data", mountpoint: "/dev/sdb"},
    ]),
}
Prefilled.play = async ({canvasElement}) => {
    await userEvent.type(canvasElement.querySelector("[data-test='table-cell-disks-1-name']")!, "-2")
    await waitFor(() => expect(canvasElement.querySelector("[data-test='story-payload']")!.textContent).toContain("\"data-2\""))
}

/**
 * The cells the backend rejected, flagged where they are. `path` is what locates them: the message
 * for `disks[1].size_gb` belongs under that cell and nowhere else.
 */
export const CellErrors = Template.bind({})
CellErrors.args = {
    input: disks,
    modelValue: JSON.stringify([
        {size_gb: 10, name: "root", mountpoint: "/dev/sda"},
        {size_gb: 4096, name: "logs", mountpoint: "var/log"},
    ]),
    errors: [
        {message: "Invalid value for input `disks[1].size_gb`. Cause: it must be less than `2048`", path: "disks[1].size_gb"},
        {message: "Invalid value for input `disks[1].mountpoint`. Cause: it must match `^/[a-zA-Z0-9/_-]*$`", path: "disks[1].mountpoint"},
    ],
}
CellErrors.play = async ({canvasElement}) => {
    const rows = canvasElement.querySelectorAll("tbody tr")
    await expect(rows[1].textContent).toContain("it must be less than `2048`")
    await expect(rows[1].textContent).toContain("it must match")
    await expect(rows[0].textContent).not.toContain("Cause")
    // The cell carries the cause alone; the path already says which cell it is.
    await expect(canvasElement.textContent).not.toContain("Invalid value for input")
}

/** Every column type a cell can hold, so the control picked per type is reviewable at a glance. */
export const ColumnTypes = Template.bind({})
ColumnTypes.args = {
    input: {
        id: "settings",
        type: "TABLE",
        columns: [
            {id: "name", type: "STRING"},
            {id: "replicas", type: "INT", min: 1, max: 9},
            {id: "ratio", type: "FLOAT"},
            {id: "enabled", type: "BOOL"},
            {id: "region", type: "SELECT", values: ["eu-west-1", "us-east-1"]},
            {id: "starts_on", type: "DATE"},
            {id: "timeout", type: "DURATION"},
        ],
    } as InputMetaData,
    modelValue: JSON.stringify([
        {name: "api", replicas: 3, ratio: 0.5, enabled: true, region: "eu-west-1", starts_on: "2026-01-01", timeout: "PT30M"},
    ]),
}
ColumnTypes.play = async ({canvasElement}) => {
    // Seven declared columns plus the remove-row one.
    await expect(canvasElement.querySelectorAll("thead th").length).toBe(8)
    await expect(canvasElement.querySelector("tbody tr")!.textContent).toContain("eu-west-1")
}
