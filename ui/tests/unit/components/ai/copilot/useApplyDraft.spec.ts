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

// Flow store — assert the in-place refresh (loadFlow/loadGraph) when applying to an open flow, and
// the "before" source read for the diff preview (the live buffer, or a store:false fetch).
const loadFlow = vi.fn()
const loadGraph = vi.fn().mockResolvedValue(undefined)
const flowYaml = "id: my-flow\nnamespace: company.team\ndescription: unsaved edit"
vi.mock("../../../../../src/stores/flow", () => ({useFlowStore: () => ({loadFlow, loadGraph, flowYaml})}))

const confirm = vi.fn()
const alert = vi.fn().mockResolvedValue(undefined)
// The flow-apply confirm goes through the raw callable form (`KsMessageBox({...})`, for a custom
// VNode message carrying the diff) instead of `.confirm()`; the dashboard path still uses `.confirm()`.
const messageBox = vi.fn()
vi.mock("@kestra-io/design-system", () => ({
    KsMessageBox: Object.assign(
        (...a: unknown[]) => messageBox(...a),
        {confirm: (...a: unknown[]) => confirm(...a), alert: (...a: unknown[]) => alert(...a)},
    ),
}))

let parsed: {namespace?: string; id?: string} = {}
vi.mock("@kestra-io/topology", () => ({flowYamlUtils: {parse: () => parsed}}))

const createFlow = vi.fn().mockResolvedValue({})
const updateFlow = vi.fn().mockResolvedValue({})
vi.mock("@kestra-io/kestra-sdk/flows", () => ({
    createFlow: (...a: unknown[]) => createFlow(...a),
    updateFlow: (...a: unknown[]) => updateFlow(...a),
}))

const clientPost = vi.fn().mockResolvedValue({data: {}})
const clientPut = vi.fn().mockResolvedValue({data: {}})
vi.mock("@kestra-io/kestra-sdk", async (importOriginal) => ({
    ...await importOriginal<typeof import("@kestra-io/kestra-sdk")>(),
    useClient: () => ({get: vi.fn(), post: (...a: unknown[]) => clientPost(...a), put: (...a: unknown[]) => clientPut(...a), delete: vi.fn()}),
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1/main",
    apiUrlWithoutTenants: () => "/api/v1",
    basePath: () => "/ui/main",
    baseUrl: "/",
}))

// A create rejection carrying the entity-already-exists problem document. The fallback branches on the
// problem type, so neither the status nor the wording of `detail` affects it.
const problem = (detail: string) => ({
    response: {
        status: 409,
        data: {
            type: "https://kestra.io/docs/api-reference/problems/entity-already-exists",
            title: "Entity already exists",
            status: 409,
            detail,
        },
    },
})
const alreadyExists = problem("A flow with id 'my-flow' already exists in namespace 'company.team'.")
const dashboardExists = problem("A dashboard with id 'my-dash' already exists.")

import type {RouteLocationNormalizedLoaded} from "vue-router"
import {useApplyDraft, isViewingFlow} from "../../../../../src/components/ai/copilot/useApplyDraft"

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
        clientPost.mockResolvedValue({data: {}})
        clientPut.mockResolvedValue({data: {}})
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

    it("apply CREATES the flow as a draft revision, then navigates to it", async () => {
        messageBox.mockResolvedValueOnce(undefined) // user confirms
        await useApplyDraft().apply(draft())
        // The create opts out of the global error toast (2nd arg) so the create→update fallback and
        // our own alert stay the only user-facing failure paths. `draft: true` so a Copilot proposal
        // is saved for review rather than going live unattended.
        expect(createFlow).toHaveBeenCalledWith(
            expect.objectContaining({body: "id: my-flow\nnamespace: company.team", draft: true}),
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
        messageBox.mockResolvedValueOnce(undefined)
        createFlow.mockRejectedValueOnce(alreadyExists) // existing flow → update in place
        await useApplyDraft().apply(draft())
        expect(updateFlow).toHaveBeenCalled()
        // Stays on the current tab and refreshes the store like a save — no bounce to overview.
        expect(loadFlow).toHaveBeenCalledWith({namespace: "company.team", id: "my-flow"})
        expect(loadGraph).toHaveBeenCalledWith({flow: expect.objectContaining({source: expect.any(String)})})
        expect(push).not.toHaveBeenCalled()
    })

    it("apply UPDATES the flow when create reports it already exists", async () => {
        messageBox.mockResolvedValueOnce(undefined)
        createFlow.mockRejectedValueOnce(alreadyExists) // create → entity-already-exists → fall back to update
        await useApplyDraft().apply(draft())
        expect(updateFlow).toHaveBeenCalledWith(
            expect.objectContaining({namespace: "company.team", id: "my-flow", body: "id: my-flow\nnamespace: company.team"}),
            expect.objectContaining({showMessageOnError: false}),
        )
        expect(push).toHaveBeenCalledWith(expect.objectContaining({name: "flows/update"}))
    })

    it("does NOT fall back to update for a different problem that merely mentions existing", async () => {
        // The old implementation regexed /already exists/i over the whole serialized body, so a validation
        // failure whose text happened to contain the phrase would silently overwrite the user's flow.
        messageBox.mockResolvedValueOnce(undefined)
        createFlow.mockRejectedValueOnce({
            response: {
                status: 422,
                data: {
                    type: "https://kestra.io/docs/api-reference/problems/validation-failed",
                    title: "Validation failed",
                    status: 422,
                    detail: "A task referencing a flow that already exists is not allowed here.",
                },
            },
        })
        await useApplyDraft().apply(draft())
        expect(updateFlow).not.toHaveBeenCalled()
    })

    it("apply surfaces an error (no update) when create fails for another reason", async () => {
        messageBox.mockResolvedValueOnce(undefined)
        createFlow.mockRejectedValueOnce({
            response: {
                status: 422,
                data: {
                    type: "https://kestra.io/docs/api-reference/problems/validation-failed",
                    title: "Validation failed",
                    status: 422,
                    detail: "bad task",
                },
            },
        })
        await useApplyDraft().apply(draft())
        expect(updateFlow).not.toHaveBeenCalled()
        expect(alert).toHaveBeenCalled()
        expect(push).not.toHaveBeenCalled()
    })

    it("apply does nothing when the confirm is cancelled", async () => {
        messageBox.mockRejectedValueOnce(new Error("cancel")) // user cancels
        await useApplyDraft().apply(draft())
        expect(createFlow).not.toHaveBeenCalled()
        expect(updateFlow).not.toHaveBeenCalled()
    })

    it("apply alerts and skips confirm when the draft has no namespace/id", async () => {
        parsed = {} // no namespace/id parsed from the YAML
        await useApplyDraft().apply(draft({yaml: "not: a-flow"}))
        expect(alert).toHaveBeenCalled()
        expect(messageBox).not.toHaveBeenCalled()
        expect(createFlow).not.toHaveBeenCalled()
    })

    // --- isViewingFlow (route-identity check reused by the editor's live diff preview) ---

    describe("isViewingFlow", () => {
        // `flows/update` migrated from a flat `:tab?` param to vue-router children (routeFamily.ts), so
        // the real route name on the flow-editor page is nested, e.g. `flows/update/edit`, never the flat
        // `flows/update` alone (kestra-io/kestra#19330 follow-up: this check never matched in the running
        // app, silently disabling the live diff mirror on the page users actually land on).
        const route = (name: string, namespace: string, id: string) =>
            ({name, params: {namespace, id}}) as unknown as RouteLocationNormalizedLoaded

        it("matches the default nested edit tab", () => {
            expect(isViewingFlow(route("flows/update/edit", "company.team", "my-flow"), "company.team", "my-flow")).toBe(true)
        })

        it("matches another nested tab", () => {
            expect(isViewingFlow(route("flows/update/topology", "company.team", "my-flow"), "company.team", "my-flow")).toBe(true)
        })

        it("matches the flat pre-migration route name", () => {
            expect(isViewingFlow(route("flows/update", "company.team", "my-flow"), "company.team", "my-flow")).toBe(true)
        })

        it("does not match a different route family", () => {
            expect(isViewingFlow(route("flows/list", "company.team", "my-flow"), "company.team", "my-flow")).toBe(false)
        })

        it("does not match when the namespace or id differs", () => {
            expect(isViewingFlow(route("flows/update/edit", "other.team", "my-flow"), "company.team", "my-flow")).toBe(false)
        })
    })

    // --- diff preview (the confirm dialog's "before" side) ---

    it("uses the live editor buffer as the diff's before-source when the flow is already open, without an extra fetch", async () => {
        routeName = "flows/update"
        routeParams = {tenant: "main", namespace: "company.team", id: "my-flow"}
        messageBox.mockResolvedValueOnce(undefined)
        createFlow.mockRejectedValueOnce(alreadyExists)
        await useApplyDraft().apply(draft())
        // loadFlow is called exactly once — the post-apply refresh — not again beforehand to fetch a
        // "before" source that's already available as the live buffer.
        expect(loadFlow).toHaveBeenCalledTimes(1)
        expect(loadFlow).toHaveBeenCalledWith({namespace: "company.team", id: "my-flow"})
    })

    it("fetches the persisted flow source (store: false) as the diff's before-source when the flow isn't open, ignoring a not-yet-created flow's 404", async () => {
        messageBox.mockResolvedValueOnce(undefined)
        await useApplyDraft().apply(draft())
        expect(loadFlow).toHaveBeenCalledWith(
            {namespace: "company.team", id: "my-flow", store: false},
            expect.objectContaining({ignoreNotFound: true, showMessageOnError: false}),
        )
    })

    // The confirm dialog itself fetches the "before" diff source (a round trip), so `applying` must be
    // set before that fetch — not only around the eventual create/update — or a second click while the
    // first confirm is still loading opens a second dialog.
    it("marks applying while the confirm dialog's diff fetch is in flight, not only during the write", async () => {
        let resolveConfirm: (() => void) | undefined
        messageBox.mockReturnValueOnce(new Promise((resolve) => {
            resolveConfirm = () => resolve(undefined)
        }))
        const {applying, apply} = useApplyDraft()
        const applied = apply(draft())
        await Promise.resolve()
        expect(applying.value).toBe(true)
        resolveConfirm?.()
        await applied
        expect(applying.value).toBe(false)
    })

    it("still shows the confirm (before-source falls back to empty) when the persisted-flow fetch fails", async () => {
        messageBox.mockResolvedValueOnce(undefined)
        loadFlow.mockRejectedValueOnce(new Error("not found"))
        await useApplyDraft().apply(draft())
        expect(messageBox).toHaveBeenCalled()
        expect(createFlow).toHaveBeenCalled()
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
        expect(clientPost).toHaveBeenCalledWith(
            "/api/v1/main/dashboards",
            "id: my-dash\ntitle: My dash",
            expect.objectContaining({showMessageOnError: false, headers: {"Content-Type": "application/x-yaml"}}),
        )
        expect(clientPut).not.toHaveBeenCalled()
        expect(push).toHaveBeenCalledWith(expect.objectContaining({name: "dashboards/update", params: {dashboard: "my-dash", tenant: "main"}}))
    })

    it("apply UPDATES the dashboard when create reports it already exists", async () => {
        parsed = {id: "my-dash"}
        confirm.mockResolvedValueOnce(true)
        clientPost.mockRejectedValueOnce(dashboardExists)
        await useApplyDraft().apply(dashboardDraft())
        expect(clientPut).toHaveBeenCalledWith(
            "/api/v1/main/dashboards/my-dash",
            "id: my-dash\ntitle: My dash",
            expect.objectContaining({showMessageOnError: false, headers: {"Content-Type": "application/x-yaml"}}),
        )
    })

    it("apply alerts and skips confirm when the dashboard draft has no id", async () => {
        parsed = {} // no id parsed
        await useApplyDraft().apply(dashboardDraft({yaml: "title: nope"}))
        expect(alert).toHaveBeenCalled()
        expect(confirm).not.toHaveBeenCalled()
        expect(clientPost).not.toHaveBeenCalled()
    })

    // --- apps (EE-only) ---

    it("reports apps unsupported in OSS and no-ops openInEditor for an app draft", () => {
        const {appSupported, openInEditor} = useApplyDraft()
        expect(appSupported).toBe(false) // EE shadows override/…/appDraftActions to enable this
        openInEditor({draftId: "da", kind: "APP", yaml: "id: my-app", valid: true, constraints: null})
        expect(push).not.toHaveBeenCalled()
    })
})
