import {ref} from "vue"
import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, userEvent, waitFor, within} from "storybook/test"
import {KsDialog} from "@kestra-io/design-system"
import PlayBoxMultiple from "vue-material-design-icons/PlayBoxMultiple.vue"
import FlowRun from "./FlowRun.vue"
import {useExecutionsStore, type InputMetaData} from "../../stores/executions"
import {flattenInputs} from "../../utils/inputs"
import {mockStoryApiRoutes} from "../../../.storybook/apiMock"

const inputs = [
    {id: "env", type: "FORM", displayName: "Environment", inputs: [{id: "region", type: "STRING", defaults: "eu-west-1"}]},
    {id: "top", type: "STRING", defaults: "hello"},
] satisfies InputMetaData[]

const meta: Meta<typeof FlowRun> = {
    title: "Components/Flows/FlowRun",
    component: FlowRun,
    render: () => ({
        components: {FlowRun, KsDialog},
        setup() {
            const store = useExecutionsStore()
            store.flow = {
                id: "replay_form_silent",
                namespace: "company.bughunt",
                disabled: false,
                draft: false,
                deleted: false,
                inputs,
                tasks: [],
                triggers: [{id: "webhook", type: "io.kestra.plugin.core.trigger.Webhook"}],
            }
            store.execution = {
                id: "previous",
                namespace: store.flow.namespace,
                flowId: store.flow.id,
                flowRevision: 1,
                state: {current: "SUCCESS", histories: []},
                inputs: {env: {region: "us-east-2"}, top: "custom"},
            }
            mockStoryApiRoutes({
                "GET /flows/company.bughunt/replay_form_silent": store.flow,
                "POST /flows/validate": [],
                "POST /executions/company.bughunt/replay_form_silent/validate": {
                    inputs: flattenInputs(inputs).map(input => ({
                        input,
                        enabled: true,
                        errors: [],
                        isDefault: false,
                    })),
                },
            })
            return {open: ref(true), PlayBoxMultiple}
        },
        template: `<KsDialog v-model="open" :title="$t('replay execution title')">
            <FlowRun autoPrefill buttonText="replay" :buttonIcon="PlayBoxMultiple" buttonTestId="replay-dialog-button" />
        </KsDialog>`,
    }),
}

export default meta
type Story = StoryObj<typeof meta>

async function reviewInputs(body: ReturnType<typeof within>) {
    await body.findByTestId("input-form-env.region", {}, {timeout: 15000})
    await userEvent.click(body.getByRole("button", {name: "Next"}))
    await body.findByTestId("input-form-top")
    await userEvent.click(body.getByRole("button", {name: "Next"}))
    await body.findByTestId("inputs-wizard-recap")
}

export const ReplayReview: Story = {
    async play({canvasElement}) {
        const body = within(canvasElement.ownerDocument.body)
        await reviewInputs(body)
        await waitFor(() => {
            const back = body.getByRole("button", {name: "Back"}).getBoundingClientRect()
            const prefill = body.getByRole("button", {name: "Prefill"}).getBoundingClientRect()
            const replay = body.getByRole("button", {name: "Replay"}).getBoundingClientRect()
            expect(Math.abs(back.y - prefill.y)).toBeLessThan(1)
            expect(Math.abs(back.y - replay.y)).toBeLessThan(1)
            expect(Math.abs(back.width - prefill.width)).toBeLessThan(1)
        })
        await userEvent.click(body.getByRole("button", {name: "Back"}))
        await body.findByTestId("input-form-top")
        expect(body.queryByRole("button", {name: "Replay"})).toBeNull()
        await userEvent.click(body.getByRole("button", {name: "Next"}))
        await body.findByTestId("inputs-wizard-recap")
        expect(body.getByTestId("inputs-wizard-recap")).toHaveTextContent("us-east-2")
        expect(body.getByTestId("inputs-wizard-recap")).toHaveTextContent("custom")
    },
}

export const ReplayReviewDark: Story = {
    ...ReplayReview,
    parameters: {themes: {themeOverride: "dark"}},
}

export const CommandTabs: Story = {
    async play({canvasElement}) {
        const body = within(canvasElement.ownerDocument.body)
        await reviewInputs(body)
        for (const name of ["cURL command", "Webhook cURL command"]) {
            await userEvent.click(body.getByRole("tab", {name}))
            await waitFor(() => expect(body.queryByRole("button", {name: "Prefill"})).toBeNull())
            expect(body.queryByRole("button", {name: "Back"})).toBeNull()
        }
        await userEvent.click(body.getByRole("tab", {name: "Other properties"}))
        expect(body.getByRole("button", {name: "Prefill"})).toBeVisible()
        await userEvent.click(body.getByRole("tab", {name: "Inputs"}))
        await userEvent.click(body.getByRole("button", {name: "Prefill"}))
        expect(body.getByTestId("inputs-wizard-recap")).toHaveTextContent("us-east-2")
        expect(body.getByTestId("inputs-wizard-recap")).toHaveTextContent("custom")
    },
}
