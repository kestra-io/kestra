import {computed, h, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import type {RouteLocationNormalizedLoaded} from "vue-router"
import {useI18n} from "vue-i18n"
import {KsMessageBox, KsText} from "@kestra-io/design-system"
import {useClient} from "@kestra-io/kestra-sdk"
import type {AxiosLikeConfig} from "@kestra-io/kestra-sdk"
import * as FlowsAPI from "@kestra-io/kestra-sdk/flows"
import {apiUrl} from "override/utils/route"
import {useAppDraftActions} from "override/components/ai/copilot/appDraftActions"
import {useMiscStore} from "override/stores/misc"
import {useTestSuiteDraftActions} from "override/components/ai/copilot/testSuiteDraftActions"
import {useFlowStore} from "../../../stores/flow"
import {routeFamily} from "../../../utils/routeFamily"
import DiffView from "./DiffView.vue"
import {SILENT_REQUEST, alertError, confirmApply as confirmApplyDialog, isAlreadyExists, parseArtefactYaml} from "./draftApply"
import type {ArtefactDraftEvent} from "./types"

export {parseArtefactYaml}

/**
 * True when the given flow is the one currently open in the editor — reused by the editor's live
 * diff preview (`CopilotChat.vue`) to decide whether a drafted flow should also mirror into
 * `flowStore.previewSource`, so the two "is this the flow I'm looking at" checks can't drift apart.
 */
export function isViewingFlow(route: RouteLocationNormalizedLoaded, namespace: string, id: string): boolean {
    return routeFamily(route.name) === "flows/update"
        && String(route.params.namespace) === namespace
        && String(route.params.id) === id
}

/**
 * Actions for an AI-drafted artefact:
 *   - `openInEditor` — hand the drafted YAML to the matching creation editor to review + save there.
 *   - `apply` — create (or update) the artefact directly, behind a confirm.
 *
 * Handles flow and dashboard drafts. Custom dashboards are EE-only: in OSS `dashboards/create`
 * resolves to the Enterprise demo page and the CRUD API is locked, so dashboard drafts only ever
 * occur in EE. Apps and unit tests are EE-only too; they have no OSS editor/API, so those paths are
 * added in `ui-ee` through the `override/` draft actions.
 */
export function useApplyDraft() {
    const route = useRoute()
    const router = useRouter()
    const {t} = useI18n()

    /** True while a direct apply is in flight (disables the button). */
    const applying = ref(false)

    // App and unit-test drafts are EE-only; OSS reports them unsupported (no-op). EE shadows these
    // via `override/`.
    const appActions = useAppDraftActions()
    const appSupported = appActions.supported
    const testSuiteActions = useTestSuiteDraftActions()
    const testSuiteSupported = testSuiteActions.supported

    // Dashboard drafts are only actionable when the backend can serve custom dashboards
    // (`GET /configs` capability flag, false once custom dashboards are locked in OSS).
    // The store is resolved lazily inside the computed for the same reason as the flow
    // store below: merely rendering a draft card must not require Pinia to be set up.
    const dashboardSupported = computed(() => useMiscStore().configs?.isCustomDashboardsEnabled !== false)

    const tenantParam = (): Record<string, string | string[]> => (route.params.tenant ? {tenant: route.params.tenant} : {})

    const silent = SILENT_REQUEST as Parameters<typeof FlowsAPI.createFlow>[1]

    /** The shared confirm dialog, bound to this component's translator. */
    const confirmApply = (message: string, title: string): Promise<boolean> => confirmApplyDialog(t, message, title)

    /** (A) Open the drafted YAML in the matching creation editor to review + save there. */
    function openInEditor(draft: ArtefactDraftEvent): void {
        if (draft.kind === "APP") {
            appActions.openInEditor(draft)
            return
        }
        if (draft.kind === "TEST_SUITE") {
            testSuiteActions.openInEditor(draft)
            return
        }
        if (draft.kind === "DASHBOARD") {
            // The dashboard create editor seeds its source from a `sourceYaml` query (see Create.vue).
            router.push({name: "dashboards/create", query: {sourceYaml: draft.yaml}, params: {...tenantParam()}})
            return
        }
        // FLOW: reuse the `blueprintSourceYaml` handoff FlowCreate.vue already understands.
        router.push({
            name: "flows/create",
            query: {blueprintId: "copilot-draft", blueprintSourceYaml: draft.yaml},
            params: {...tenantParam()},
        })
    }

    /** (B) Create (or update) the artefact directly. Confirms first; dispatches on the draft kind.
     *  Resolves `true` once the artefact was actually written (not merely confirmed) — the caller uses
     *  this to stop treating the draft as pending (see `CopilotChat.vue`'s applied-draft tracking). */
    async function apply(draft: ArtefactDraftEvent): Promise<boolean> {
        if (draft.kind === "DASHBOARD") {
            return applyDashboard(draft)
        }
        if (draft.kind === "TEST_SUITE") {
            // EE owns the whole path (its own API, confirm copy and post-apply navigation); the
            // in-flight flag still lives here so the card's Apply button disables while it runs.
            applying.value = true
            try {
                return await testSuiteActions.apply(draft)
            } finally {
                applying.value = false
            }
        }
        return applyFlow(draft)
    }

    async function applyFlow(draft: ArtefactDraftEvent): Promise<boolean> {
        const {namespace, id} = parseArtefactYaml(draft.yaml)
        if (!namespace || !id) {
            await KsMessageBox.alert(t("ai.copilot.draft.applyNoTarget"), t("ai.copilot.draft.applyTitle"), {type: "error"})
            return false
        }

        // Resolved once per apply (not merely rendering a draft card, so a store dependency here is
        // fine) — reused for the diff preview below and, on success, for the in-place refresh.
        const flowStore = useFlowStore()
        const onThisFlow = isViewingFlow(route, namespace, id)

        // Set before the confirm (which itself fetches the "before" diff source below) so a second
        // click can't open a second confirm dialog while the first is still loading.
        applying.value = true
        try {
            const confirmed = await confirmApplyFlow(namespace, id, draft.yaml, onThisFlow, flowStore)
            if (!confirmed) return false

            // Try to create; if the flow already exists, update it instead — one round trip rather
            // than probing with a GET first. Applied as a draft revision, not a live one: drafts aren't
            // picked up by webhooks/schedules/subflows and skip constraint validation, so the user
            // reviews and publishes it explicitly rather than a Copilot proposal going live unattended.
            try {
                await FlowsAPI.createFlow(
                    {body: draft.yaml, draft: true} as Parameters<typeof FlowsAPI.createFlow>[0],
                    silent,
                )
            } catch (e) {
                if (!isAlreadyExists(e)) throw e
                await FlowsAPI.updateFlow(
                    {namespace, id, body: draft.yaml, draft: true} as Parameters<typeof FlowsAPI.updateFlow>[0],
                    silent,
                )
            }
            // When the user is already viewing this flow, apply transparently — like a save: refresh
            // the store (source buffer + graph) in place and stay on the current tab, instead of
            // bouncing to the flow overview and forcing a hard refresh to see the change. Otherwise
            // open the flow so the result is visible.
            if (onThisFlow) {
                const data = await flowStore.loadFlow({namespace, id})
                if (data?.source) await flowStore.loadGraph({flow: data})
            } else {
                router.push({name: "flows/update", params: {namespace, id, ...tenantParam()}})
            }
            return true
        } catch (e) {
            await alertError(e, t("ai.copilot.draft.applyError"), t("ai.copilot.draft.applyTitle"))
            return false
        } finally {
            applying.value = false
        }
    }

    /**
     * Confirm applying a flow draft, showing a diff against the flow's current content instead of the
     * plain confirm text alone. The "before" side is the live editor buffer when the flow is open
     * (reflecting any unsaved edits), otherwise a fetch of the persisted source — empty (rendering the
     * draft as a pure addition) when the flow doesn't exist yet or the fetch fails, so the preview never
     * blocks the apply itself.
     */
    async function confirmApplyFlow(
        namespace: string,
        id: string,
        yaml: string,
        onThisFlow: boolean,
        flowStore: ReturnType<typeof useFlowStore>,
    ): Promise<boolean> {
        const before = onThisFlow ? (flowStore.flowYaml || "") : await persistedFlowSource(namespace, id, flowStore)
        return KsMessageBox({
            type: "warning",
            title: t("ai.copilot.draft.applyTitle"),
            message: () => h("div", null, [
                h(KsText, {tag: "p"}, () => t("ai.copilot.draft.applyConfirm", {namespace, id})),
                h(DiffView, {oldValue: before, newValue: yaml}),
            ]),
            showCancelButton: true,
            confirmButtonText: t("ai.copilot.draft.apply"),
            cancelButtonText: t("cancel"),
            // The default message box tops out at ~420px — too narrow for the diff editor below the
            // confirm text. Match KsDialog's "large" variant width rather than inventing a new size.
            customStyle: {maxWidth: "min(750px, 90vw)"},
        }).then(() => true).catch(() => false)
    }

    async function persistedFlowSource(namespace: string, id: string, flowStore: ReturnType<typeof useFlowStore>): Promise<string> {
        try {
            // A brand-new flow (the primary path — nothing persisted yet) 404s here by design; ignore
            // it like the fallback below does, rather than letting the global interceptor raise its own
            // error toast before the confirm dialog even opens.
            const data = await flowStore.loadFlow({namespace, id, store: false}, {...silent, ignoreNotFound: true})
            return data?.source ?? ""
        } catch {
            return ""
        }
    }

    async function applyDashboard(draft: ArtefactDraftEvent): Promise<boolean> {
        // Dashboards are tenant-scoped and identified by `id` alone (no namespace).
        const {id} = parseArtefactYaml(draft.yaml)
        if (!id) {
            await KsMessageBox.alert(t("ai.copilot.draft.applyNoTarget"), t("ai.copilot.draft.applyTitleDashboard"), {type: "error"})
            return false
        }

        const confirmed = await confirmApply(t("ai.copilot.draft.applyConfirmDashboard", {id}), t("ai.copilot.draft.applyTitleDashboard"))
        if (!confirmed) return false

        applying.value = true
        try {
            // Create, falling back to update if the id already exists — same no-probe rationale as flows.
            /** Raw client because dashboard writes are Enterprise-only routes, absent from the OSS SDK; see the dashboard store. */
            const yaml = {...silent, headers: {"Content-Type": "application/x-yaml"}} as AxiosLikeConfig
            try {
                await useClient().post(`${apiUrl()}/dashboards`, draft.yaml, yaml)
            } catch (e) {
                if (!isAlreadyExists(e)) throw e
                await useClient().put(`${apiUrl()}/dashboards/${id}`, draft.yaml, yaml)
            }
            router.push({name: "dashboards/update", params: {dashboard: id, ...tenantParam()}})
            return true
        } catch (e) {
            await alertError(e, t("ai.copilot.draft.applyErrorDashboard"), t("ai.copilot.draft.applyTitleDashboard"))
            return false
        } finally {
            applying.value = false
        }
    }

    return {applying, appSupported, dashboardSupported, testSuiteSupported, openInEditor, apply}
}
