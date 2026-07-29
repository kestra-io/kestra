import {describe, it, expect, vi, beforeEach} from "vitest"

// --- mocks (hoisted) ---
const push = vi.fn()
let routeName: string | undefined = undefined
let routeParams: Record<string, any> = {}
vi.mock("vue-router", () => ({
    useRouter: () => ({push}),
    useRoute: () => ({name: routeName, params: routeParams}),
}))
vi.mock("vue-i18n", () => ({useI18n: () => ({t: (k: string) => k})}))

// Flow store — assert the in-place refresh (loadFlow/loadGraph) when applying to an open flow.
const loadFlow = vi.fn()
const loadGraph = vi.fn().mockResolvedValue(undefined)
vi.mock("../../../../../src/stores/flow", () => ({useFlowStore: () => ({loadFlow, loadGraph})}))

const confirm = vi.fn()
const alert = vi.fn().mockResolvedValue(undefined)
vi.mock("@kestra-io/design-system", () => ({KsMessageBox: {confirm: (...a: unknown[]) => confirm(...a), alert: (...a: unknown[]) => alert(...a)}}))

let parsed: {namespace?: string; id?: string} = {}
vi.mock("@kestra-io/topology", () => ({flowYamlUtils: {parse: () => parsed}}))

const createFlow = vi.fn().mockResolvedValue({})
const updateFlow = vi.fn().mockResolvedValue({})
vi.mock("@kestra-io/kestra-sdk/flows", () => ({
    createFlow: (...a: unknown[]) => createFlow(...a),
    updateFlow: (...a: unknown[]) => updateFlow(...a),
}))

const createDashboard = vi.fn().mockResolvedValue({})
const updateDashboard = vi.fn().mockResolvedValue({})
vi.mock("@kestra-io/kestra-sdk/dashboards", () => ({
    createDashboard: (...a: unknown[]) => createDashboard(...a),
    updateDashboard: (...a: unknown[]) => updateDashboard(...a),
}))

// A create rejection shaped like the backend's "already exists" 422.
const alreadyExists = {response: {status: 422, data: {message: "Flow id already exists: my-flow"}}}
const dashboardExists = {response: {status: 422, data: {message: "Dashboard id already exists: my-dash"}}}

import {useApplyDraft} from "../../../../../src/components/ai/copilot/useApplyDraft"

const draft = (over = {}) => ({draftId: "d1", kind: "FLOW" as const, yaml: "id: my-flow\nnamespace: company.team", valid: true, constraints: null, ...over})

describe("useApplyDraft", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        routeName = undefined
        routeParams = {tenant: "main"}
        parsed = {namespace: "company.team", id: "my-flow"}
        alert.mockResolvedValue(undefined)
        createFlow.mockResolvedValue({})
        updateFlow.mockResolvedValue({})
        createDashboard.mockResolvedValue({})
        updateDashboard.mockResolvedValue({})
        loadFlow.mockResolvedValue({source: "id: my-flow\nnamespace: company.team"})
        loadGraph.mockResolvedValue(undefined)
    })

    const dashboardDraft = (over = {}) => ({draftId: "d9", kind: "DASHBOARD" as const, yaml: "id: my-dash\ntitle: My dash", valid: true, constraints: null, ...over})

    it("openInEditor pushes flows/create with the drafted YAML as blueprintSourceYaml", () => {
        useApplyDraft().openInEditor(draft())
        expect(push).toHaveBeenCalledWith(expect.objectContaining({
            name: "flows/create",
            query: {blueprintId: "copilot-draft", blueprintSourceYaml: "id: my-flow\nnamespace: company.team"},
            params: {tenant: "main"},
        }))
    })

    it("apply CREATES the flow, then navigates to it", async () => {
        confirm.mockResolvedValueOnce(undefined) // user confirms
        await useApplyDraft().apply(draft())
        // The create opts out of the global error toast (2nd arg) so the create→update fallback and
        // our own alert stay the only user-facing failure paths.
        expect(createFlow).toHaveBeenCalledWith(
            expect.objectContaining({body: "id: my-flow\nnamespace: company.team"}),
            expect.objectContaining({showMessageOnError: false}),
        )
        expect(updateFlow).not.toHaveBeenCalled()
        // On success it navigates to the applied flow.
        expect(push).toHaveBeenCalledWith(expect.objectContaining({
            name: "flows/update",
            params: {namespace: "company.team", id: "my-flow", tenant: "main"},
        }))
    })

    it("apply refreshes the flow in place (no navigation) when already viewing it", async () => {
        routeName = "flows/update"
        routeParams = {tenant: "main", namespace: "company.team", id: "my-flow"}
        confirm.mockResolvedValueOnce(undefined)
        createFlow.mockRejectedValueOnce(alreadyExists) // existing flow → update in place
        await useApplyDraft().apply(draft())
        expect(updateFlow).toHaveBeenCalled()
        // Stays on the current tab and refreshes the store like a save — no bounce to overview.
        expect(loadFlow).toHaveBeenCalledWith({namespace: "company.team", id: "my-flow"})
        expect(loadGraph).toHaveBeenCalledWith({flow: expect.objectContaining({source: expect.any(String)})})
        expect(push).not.toHaveBeenCalled()
    })

    it("apply UPDATES the flow when create reports it already exists", async () => {
        confirm.mockResolvedValueOnce(undefined)
        createFlow.mockRejectedValueOnce(alreadyExists) // create → 422 already exists → fall back to update
        await useApplyDraft().apply(draft())
        expect(updateFlow).toHaveBeenCalledWith(
            expect.objectContaining({namespace: "company.team", id: "my-flow", body: "id: my-flow\nnamespace: company.team"}),
            expect.objectContaining({showMessageOnError: false}),
        )
        expect(push).toHaveBeenCalledWith(expect.objectContaining({name: "flows/update"}))
    })

    it("falls back to update when the 'already exists' error is nested in the validation body", async () => {
        // The real backend 422 puts the "already exists" text in the validation errors, not data.message.
        confirm.mockResolvedValueOnce(undefined)
        createFlow.mockRejectedValueOnce({
            status: 422,
            response: {status: 422, data: {message: "Validation failed", _embedded: {errors: [{message: "flow.id: Flow id already exists: my-flow"}]}}},
        })
        await useApplyDraft().apply(draft())
        expect(updateFlow).toHaveBeenCalledWith(
            expect.objectContaining({namespace: "company.team", id: "my-flow"}),
            expect.objectContaining({showMessageOnError: false}),
        )
    })

    it("apply surfaces an error (no update) when create fails for another reason", async () => {
        confirm.mockResolvedValueOnce(undefined)
        createFlow.mockRejectedValueOnce({response: {status: 422, data: {message: "invalid flow: bad task"}}})
        await useApplyDraft().apply(draft())
        expect(updateFlow).not.toHaveBeenCalled()
        expect(alert).toHaveBeenCalled()
        expect(push).not.toHaveBeenCalled()
    })

    it("apply does nothing when the confirm is cancelled", async () => {
        confirm.mockRejectedValueOnce(new Error("cancel")) // user cancels
        await useApplyDraft().apply(draft())
        expect(createFlow).not.toHaveBeenCalled()
        expect(updateFlow).not.toHaveBeenCalled()
    })

    it("apply alerts and skips confirm when the draft has no namespace/id", async () => {
        parsed = {} // no namespace/id parsed from the YAML
        await useApplyDraft().apply(draft({yaml: "not: a-flow"}))
        expect(alert).toHaveBeenCalled()
        expect(confirm).not.toHaveBeenCalled()
        expect(createFlow).not.toHaveBeenCalled()
    })

    // --- dashboards ---

    it("openInEditor pushes dashboards/create seeded with the drafted YAML", () => {
        useApplyDraft().openInEditor(dashboardDraft())
        expect(push).toHaveBeenCalledWith(expect.objectContaining({
            name: "dashboards/create",
            query: {sourceYaml: "id: my-dash\ntitle: My dash"},
            params: {tenant: "main"},
        }))
    })

    it("apply CREATES the dashboard, then navigates to it (id only, no namespace)", async () => {
        parsed = {id: "my-dash"}
        confirm.mockResolvedValueOnce(true)
        await useApplyDraft().apply(dashboardDraft())
        expect(createDashboard).toHaveBeenCalledWith(
            expect.objectContaining({body: "id: my-dash\ntitle: My dash"}),
            expect.objectContaining({showMessageOnError: false}),
        )
        expect(updateDashboard).not.toHaveBeenCalled()
        expect(push).toHaveBeenCalledWith(expect.objectContaining({name: "dashboards/update", params: {dashboard: "my-dash", tenant: "main"}}))
    })

    it("apply UPDATES the dashboard when create reports it already exists", async () => {
        parsed = {id: "my-dash"}
        confirm.mockResolvedValueOnce(true)
        createDashboard.mockRejectedValueOnce(dashboardExists)
        await useApplyDraft().apply(dashboardDraft())
        expect(updateDashboard).toHaveBeenCalledWith(
            expect.objectContaining({id: "my-dash", body: "id: my-dash\ntitle: My dash"}),
            expect.objectContaining({showMessageOnError: false}),
        )
    })

    it("apply alerts and skips confirm when the dashboard draft has no id", async () => {
        parsed = {} // no id parsed
        await useApplyDraft().apply(dashboardDraft({yaml: "title: nope"}))
        expect(alert).toHaveBeenCalled()
        expect(confirm).not.toHaveBeenCalled()
        expect(createDashboard).not.toHaveBeenCalled()
    })

    // --- apps (EE-only) ---

    it("reports apps unsupported in OSS and no-ops openInEditor for an app draft", () => {
        const {appSupported, openInEditor} = useApplyDraft()
        expect(appSupported).toBe(false) // EE shadows override/…/appDraftActions to enable this
        openInEditor({draftId: "da", kind: "APP", yaml: "id: my-app", valid: true, constraints: null})
        expect(push).not.toHaveBeenCalled()
    })
})
