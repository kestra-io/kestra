import {ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {useI18n} from "vue-i18n"
import {KsMessageBox} from "@kestra-io/design-system"
import * as FlowsAPI from "@kestra-io/kestra-sdk/flows"
import * as DashboardsAPI from "@kestra-io/kestra-sdk/dashboards"
import {useAppDraftActions} from "override/components/ai/copilot/appDraftActions"
import {useTestSuiteDraftActions} from "override/components/ai/copilot/testSuiteDraftActions"
import {useFlowStore} from "../../../stores/flow"
import {SILENT_REQUEST, alertError, confirmApply as confirmApplyDialog, isAlreadyExists, parseTarget} from "./draftApply"
import type {ArtefactDraftEvent} from "./types"

/**
 * Actions for an AI-drafted artefact:
 *   - `openInEditor` — hand the drafted YAML to the matching creation editor to review + save there.
 *   - `apply` — create (or update) the artefact directly, behind a confirm.
 *
 * Handles flow and dashboard drafts (both OSS features today). Apps and unit tests are EE-only —
 * they have no OSS editor/API, and their drafts only ever occur in EE, so those paths are added in
 * `ui-ee` through the `override/` draft actions.
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

    /** (B) Create (or update) the artefact directly. Confirms first; dispatches on the draft kind. */
    async function apply(draft: ArtefactDraftEvent): Promise<void> {
        if (draft.kind === "DASHBOARD") {
            await applyDashboard(draft)
            return
        }
        if (draft.kind === "TEST_SUITE") {
            // EE owns the whole path (its own API, confirm copy and post-apply navigation); the
            // in-flight flag still lives here so the card's Apply button disables while it runs.
            applying.value = true
            try {
                await testSuiteActions.apply(draft)
            } finally {
                applying.value = false
            }
            return
        }
        await applyFlow(draft)
    }

    async function applyFlow(draft: ArtefactDraftEvent): Promise<void> {
        const {namespace, id} = parseTarget(draft.yaml)
        if (!namespace || !id) {
            await KsMessageBox.alert(t("ai.copilot.draft.applyNoTarget"), t("ai.copilot.draft.applyTitle"), {type: "error"})
            return
        }

        const confirmed = await confirmApply(t("ai.copilot.draft.applyConfirm", {namespace, id}), t("ai.copilot.draft.applyTitle"))
        if (!confirmed) return

        applying.value = true
        try {
            // Try to create; if the flow already exists, update it instead. We deliberately don't
            // probe with a GET first — a 404 on a not-yet-existing flow trips the global error page.
            try {
                await FlowsAPI.createFlow(
                    {body: draft.yaml, draft: false} as Parameters<typeof FlowsAPI.createFlow>[0],
                    silent,
                )
            } catch (e) {
                if (!isAlreadyExists(e)) throw e
                await FlowsAPI.updateFlow(
                    {namespace, id, body: draft.yaml} as Parameters<typeof FlowsAPI.updateFlow>[0],
                    silent,
                )
            }
            // When the user is already viewing this flow, apply transparently — like a save: refresh
            // the store (source buffer + graph) in place and stay on the current tab, instead of
            // bouncing to the flow overview and forcing a hard refresh to see the change. Otherwise
            // open the flow so the result is visible.
            const onThisFlow = route.name === "flows/update"
                && String(route.params.namespace) === namespace
                && String(route.params.id) === id
            if (onThisFlow) {
                // Resolve the store lazily (only when we actually refresh an open flow) so merely
                // rendering a draft card doesn't require Pinia to be set up.
                const flowStore = useFlowStore()
                const data = await flowStore.loadFlow({namespace, id})
                if (data?.source) await flowStore.loadGraph({flow: data})
            } else {
                router.push({name: "flows/update", params: {namespace, id, ...tenantParam()}})
            }
        } catch (e) {
            await alertError(e, t("ai.copilot.draft.applyError"), t("ai.copilot.draft.applyTitle"))
        } finally {
            applying.value = false
        }
    }

    async function applyDashboard(draft: ArtefactDraftEvent): Promise<void> {
        // Dashboards are tenant-scoped and identified by `id` alone (no namespace).
        const {id} = parseTarget(draft.yaml)
        if (!id) {
            await KsMessageBox.alert(t("ai.copilot.draft.applyNoTarget"), t("ai.copilot.draft.applyTitleDashboard"), {type: "error"})
            return
        }

        const confirmed = await confirmApply(t("ai.copilot.draft.applyConfirmDashboard", {id}), t("ai.copilot.draft.applyTitleDashboard"))
        if (!confirmed) return

        applying.value = true
        try {
            // Create, falling back to update if the id already exists — same no-probe rationale as flows.
            try {
                await DashboardsAPI.createDashboard(
                    {body: draft.yaml} as Parameters<typeof DashboardsAPI.createDashboard>[0],
                    silent,
                )
            } catch (e) {
                if (!isAlreadyExists(e)) throw e
                await DashboardsAPI.updateDashboard(
                    {id, body: draft.yaml} as Parameters<typeof DashboardsAPI.updateDashboard>[0],
                    silent,
                )
            }
            router.push({name: "dashboards/update", params: {dashboard: id, ...tenantParam()}})
        } catch (e) {
            await alertError(e, t("ai.copilot.draft.applyErrorDashboard"), t("ai.copilot.draft.applyTitleDashboard"))
        } finally {
            applying.value = false
        }
    }

    return {applying, appSupported, testSuiteSupported, openInEditor, apply}
}
