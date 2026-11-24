import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, userEvent, within} from "storybook/test"
import type {Check} from "../../stores/executions"
import FailingChecksAlert from "./FailingChecksAlert.vue"

const check = (message: string, style: Check["style"], behavior: Check["behavior"] = "BLOCK_EXECUTION"): Check =>
    ({when: "{{ true }}", message, style, behavior})

const meta: Meta<typeof FailingChecksAlert> = {
    title: "Components/Inputs/FailingChecksAlert",
    component: FailingChecksAlert,
}

export default meta
type Story = StoryObj<typeof meta>

export const Blocking: Story = {
    args: {
        checks: [
            check("A load balancer row cannot request vCPU or RAM.", "ERROR"),
            check("Rows without an owner will be assigned to the requester.", "INFO", "CREATE_EXECUTION"),
        ],
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const alerts = canvas.getAllByRole("alert")
        await expect(alerts.map(alert => alert.textContent?.trim())).toEqual([
            "A load balancer row cannot request vCPU or RAM.",
            "Rows without an owner will be assigned to the requester.",
        ])
        await expect(canvas.queryByTestId("failing-checks-toggle")).toBeNull()
    },
}

export const NonBlocking: Story = {
    args: {
        checks: [check("This environment is shared with another team.", "WARNING", "CREATE_EXECUTION")],
    },
}

export const NoFailingCheck: Story = {
    args: {checks: []},
    async play({canvasElement}) {
        await expect(within(canvasElement).queryByTestId("failing-checks")).toBeNull()
    },
}

const manyMessages = [
    "Row 3: a load balancer cannot request vCPU.",
    "Row 7: RAM exceeds the quota of the project.",
    "Row 12: the region is not allowed for this cost center.",
    "Row 40: a very long message that explains in detail why this component cannot be provisioned as requested, so that the alert has to wrap over several lines on a narrow screen.",
]

export const ManyCollapsed: Story = {
    args: {
        checks: [
            check(manyMessages[0], "WARNING"),
            check(manyMessages[1], "ERROR"),
            check(manyMessages[2], "INFO"),
            check(manyMessages[3], "SUCCESS"),
        ],
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await expect(canvas.getAllByRole("alert")).toHaveLength(1)
        await expect(canvas.getByRole("alert").textContent).toContain("4 checks failing")

        const toggle = canvas.getByTestId("failing-checks-toggle")
        await expect(toggle.getAttribute("aria-expanded")).toBe("false")
        await userEvent.click(toggle)

        const listed = (await canvas.findAllByRole("alert")).slice(1)
        await expect(listed.map(alert => alert.textContent?.trim())).toEqual(manyMessages)
        await expect(toggle.getAttribute("aria-expanded")).toBe("true")
    },
}
