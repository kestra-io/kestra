import {onUnmounted} from "vue"
import {useRouter, type Router} from "vue-router"
import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, spyOn, userEvent, waitFor, within} from "storybook/test"
import {vueRouter} from "storybook-vue3-router"
import {configureClient} from "@kestra-io/kestra-sdk"

import {apiFetch, mockStoryApiRoutes} from "../../../.storybook/apiMock"
import FlowRevisions from "./FlowRevisions.vue"
import {useFlowStore, type FlowRevision} from "../../stores/flow"

const NAMESPACE = "company.team"
const FLOW_ID = "restore-revisions"
const FLOW_PATH = `/flows/edit/${NAMESPACE}/${FLOW_ID}`

function revision(revisionNumber: number, description: string): FlowRevision {
    return {
        id: FLOW_ID,
        namespace: NAMESPACE,
        revision: revisionNumber,
        tasks: [],
        disabled: false,
        draft: false,
        deleted: false,
        source: `id: ${FLOW_ID}\nnamespace: ${NAMESPACE}\ndescription: ${description}\ntasks: []\n`,
    }
}

let revisions: FlowRevision[]
let storyRouter: Router
let responseMode: "save-error" | "refresh-error" | "delayed-refresh" | undefined
let refreshStarted: Promise<void>
let refreshFinished: Promise<void>
let signalRefreshStarted: () => void
let finishRefresh: () => void

function errorResponse(detail: string): Response {
    return Response.json({type: "about:blank", title: "Error", status: 400, detail}, {status: 400})
}

const meta: Meta<typeof FlowRevisions> = {
    title: "Components/Flows/FlowRevisions",
    component: FlowRevisions,
    loaders: [async () => {
        await import("@kestra-io/design-system/components/Form/KsEditor.vue")
        return {}
    }],
    decorators: [
        vueRouter([
            {path: "/flows/edit/:namespace/:id/revisions", component: {template: "<div />"}},
        ], {initialRoute: `${FLOW_PATH}/revisions?revisionLeft=3&revisionRight=4&keep=filter`}),
    ],
    beforeEach() {
        responseMode = undefined
        refreshStarted = new Promise(resolve => {signalRefreshStarted = resolve})
        refreshFinished = new Promise(resolve => {finishRefresh = resolve})
        revisions = [
            revision(1, "RESTORED_REVISION_ONE"),
            revision(3, "HISTORICAL_REVISION_THREE"),
            revision(4, "CURRENT_REVISION_FOUR"),
        ]
        mockStoryApiRoutes({
            [`GET /flows/${NAMESPACE}/${FLOW_ID}/revisions`]: () => revisions,
            [`PUT /flows/${NAMESPACE}/${FLOW_ID}`]: ({body}: {body?: unknown}) => {
                const saved = {...revision(9, "RESTORED_REVISION_ONE"), source: String(body)}
                revisions = [...revisions, saved]
                return saved
            },
            "POST /flows/validate": [],
        })
    },
    render: () => ({
        components: {FlowRevisions},
        setup() {
            storyRouter = useRouter()
            configureClient({fetch: async (input, init) => {
                const url = input instanceof Request ? input.url : String(input)
                const method = init?.method ?? (input instanceof Request ? input.method : "GET")
                if (responseMode === "save-error" && method === "PUT") return errorResponse("Restore failed")
                if (method === "GET" && url.includes(`/flows/${NAMESPACE}/${FLOW_ID}/revisions`) && revisions.some(item => item.revision === 9)) {
                    if (responseMode === "refresh-error") return errorResponse("Revision refresh failed")
                    if (responseMode === "delayed-refresh") {
                        signalRefreshStarted()
                        await refreshFinished
                    }
                }
                return apiFetch(input, init)
            }})
            onUnmounted(() => {
                finishRefresh()
                configureClient({fetch: apiFetch})
            })
            const flowStore = useFlowStore()
            const current = revisions[revisions.length - 1]
            flowStore.flow = {...current, source: current.source!}
            flowStore.revisions = [...revisions]
            flowStore.flowYaml = current.source!
            flowStore.flowYamlOrigin = current.source!
            return {}
        },
        template: '<div style="height: 100vh"><FlowRevisions /></div>',
    }),
}

export default meta
type Story = StoryObj<typeof meta>

function restoreStory(side: "left" | "right"): Story {
    return {
        async play({canvasElement}) {
            const canvas = within(canvasElement)
            await waitFor(() => expect(canvas.getByText("Revision 4 (current)", {exact: true})).toBeVisible())
            await userEvent.click(canvas.getAllByRole("combobox")[side === "left" ? 1 : 2])
            await userEvent.click(within(document.body).getByRole("option", {name: /Revision 1/}))
            await waitFor(() => expect(canvas.getByText(/RESTORED_REVISION_ONE/)).toBeVisible(), {timeout: 10000})
            await userEvent.click(canvas.getByTestId(`restore-${side}`))
            const dialog = within(await within(document.body).findByRole("dialog", {name: "Confirmation"}))
            await userEvent.click(dialog.getByRole("button", {name: "OK"}))

            await waitFor(() => expect(canvas.getByText("Revision 9 (current)", {exact: true})).toBeVisible())
            await expect(storyRouter.currentRoute.value.query).toEqual({revisionLeft: "4", revisionRight: "9", keep: "filter"})
            await waitFor(() => expect(canvas.getByText("Revision 4", {exact: true})).toBeVisible())
            await waitFor(() => expect(canvas.getByText(/RESTORED_REVISION_ONE/)).toBeVisible())
            await expect(canvas.getByText(/CURRENT_REVISION_FOUR/)).toBeVisible()
            await expect(canvas.queryByText(/HISTORICAL_REVISION_THREE/)).not.toBeInTheDocument()
            const flowStore = useFlowStore()
            await expect(flowStore.haveChange).toBe(false)
            await expect(flowStore.flowYamlOrigin).toBe(flowStore.flow?.source)
        },
    }
}

export const RestoreLeft: Story = restoreStory("left")
export const RestoreRight: Story = restoreStory("right")
export const RestoreInDarkMode: Story = {
    ...restoreStory("left"),
    parameters: {themes: {themeOverride: "dark"}},
}

export const CancelRestore: Story = {
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await waitFor(() => expect(canvas.getByText("Revision 4 (current)", {exact: true})).toBeVisible())
        await userEvent.click(canvas.getByTestId("restore-left"))
        const dialog = within(await within(document.body).findByRole("dialog", {name: "Confirmation"}))
        await userEvent.click(dialog.getByRole("button", {name: "Cancel"}))
        await waitFor(() => expect(within(document.body).queryByRole("dialog", {name: "Confirmation"})).not.toBeInTheDocument())
        await expect(canvas.getByText("Revision 4 (current)", {exact: true})).toBeVisible()
        await expect(canvas.getByText(/HISTORICAL_REVISION_THREE/)).toBeVisible()
        await expect(canvas.getByText(/CURRENT_REVISION_FOUR/)).toBeVisible()
        await expect(revisions.map(item => item.revision)).toEqual([1, 3, 4])
    },
}

async function confirmRestore(canvasElement: HTMLElement) {
    const canvas = within(canvasElement)
    await waitFor(() => expect(canvas.getByText(/HISTORICAL_REVISION_THREE/)).toBeVisible(), {timeout: 10000})
    await userEvent.click(canvas.getByTestId("restore-left"))
    const dialog = within(await within(document.body).findByRole("dialog", {name: "Confirmation"}))
    await userEvent.click(dialog.getByRole("button", {name: "OK"}))
}

export const SaveFailure: Story = {
    beforeEach() {responseMode = "save-error"},
    async play({canvasElement}) {
        await confirmRestore(canvasElement)
        await within(document.body).findByText("Restore failed")
        await expect(within(canvasElement).getByText("Revision 4 (current)", {exact: true})).toBeVisible()
        await expect(useFlowStore().flow?.revision).toBe(4)
        await expect(storyRouter.currentRoute.value.query).toEqual({revisionLeft: "3", revisionRight: "4", keep: "filter"})
    },
}

export const RefreshFailure: Story = {
    beforeEach() {responseMode = "refresh-error"},
    async play({canvasElement}) {
        await confirmRestore(canvasElement)
        await within(document.body).findByText("Revision refresh failed")
        const canvas = within(canvasElement)
        await expect(canvas.getByText("Revision 4 (current)", {exact: true})).toBeVisible()
        await expect(canvas.getByText(/HISTORICAL_REVISION_THREE/)).toBeVisible()
        await expect(useFlowStore().haveChange).toBe(false)
        await expect(storyRouter.currentRoute.value.query).toEqual({revisionLeft: "3", revisionRight: "4", keep: "filter"})
    },
}

export const LeaveDuringRefresh: Story = {
    beforeEach() {responseMode = "delayed-refresh"},
    async play({canvasElement}) {
        await confirmRestore(canvasElement)
        await refreshStarted
        storyRouter.addRoute({path: `${FLOW_PATH}/edit`, component: {template: "<div />"}})
        await storyRouter.push({path: `${FLOW_PATH}/edit`, query: {keep: "editor"}})
        finishRefresh()
        await waitFor(() => expect(useFlowStore().revisions?.some(item => item.revision === 9)).toBe(true))
        await expect(storyRouter.currentRoute.value.path).toBe(`${FLOW_PATH}/edit`)
        await expect(storyRouter.currentRoute.value.query).toEqual({keep: "editor"})
    },
}

export const SwitchFlowDuringRefresh: Story = {
    beforeEach() {responseMode = "delayed-refresh"},
    async play({canvasElement}) {
        const flowStore = useFlowStore()
        const loadRevisions = spyOn(flowStore, "loadRevisions")
        await confirmRestore(canvasElement)
        await refreshStarted
        const pendingRefresh = loadRevisions.mock.results.at(-1)?.value
        const otherFlow = {...revision(12, "OTHER_FLOW"), id: "other-flow"}
        storyRouter.addRoute({path: "/other-flow/edit", component: {template: "<div />"}})
        await storyRouter.push({path: "/other-flow/edit", query: {keep: "other"}})
        flowStore.flow = {...otherFlow, source: otherFlow.source!}
        flowStore.revisions = [otherFlow]
        finishRefresh()
        await pendingRefresh
        await expect(flowStore.revisions).toEqual([otherFlow])
        await expect(storyRouter.currentRoute.value.query).toEqual({keep: "other"})
    },
}
