import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, userEvent, waitFor} from "storybook/test"
import {mockStoryApiRoutes, type ApiRouteContext} from "../../../../.storybook/apiMock"
import CopilotArtefactDraft from "./CopilotArtefactDraft.vue"
import fixture from "./CopilotArtefactDraft.fixture.json"

const meta: Meta<typeof CopilotArtefactDraft> = {
    title: "Components/AI/Copilot/CopilotArtefactDraft",
    component: CopilotArtefactDraft,
    args: {
        draft: {
            draftId: "draft-1",
            kind: "FLOW",
            yaml: fixture.after.yaml,
            valid: true,
            constraints: null,
        },
    },
    decorators: [() => ({template: "<div style='max-width: 28rem'><story /></div>"})],
    beforeEach() {
        mockStoryApiRoutes({
            "GET /flows/:namespace/:id": {
                id: "orders_sync",
                namespace: "company.sales",
                source: fixture.before.yaml,
            },
            "POST /flows/graph": ({body}: ApiRouteContext) => (
                String(body).includes("dedupe") ? fixture.after.graph : fixture.before.graph
            ),
        })
    },
}

export default meta
type Story = StoryObj<typeof meta>

export const Yaml: Story = {}

export const Graph: Story = {
    async play({canvasElement}) {
        const graphTab = await waitFor(() => {
            const tab = canvasElement.querySelector<HTMLElement>("#tab-graph")
            expect(tab).not.toBeNull()
            return tab as HTMLElement
        })
        await userEvent.click(graphTab)
        await waitFor(() => {
            expect(canvasElement.querySelector("[data-test='flow-change-review']")).not.toBeNull()
            const badges = canvasElement.querySelectorAll("[data-test='topology-diff-badge']")
            expect(badges.length).toBeGreaterThan(0)
        }, {timeout: 10000})
    },
}

export const InvalidDraft: Story = {
    args: {
        draft: {
            draftId: "draft-2",
            kind: "FLOW",
            yaml: "id: broken",
            valid: false,
            constraints: "namespace: must not be null",
        },
    },
}
