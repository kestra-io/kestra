import {computed, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {useI18n} from "vue-i18n"
import {KsMessageBox} from "@kestra-io/design-system"
import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"
import * as FlowsAPI from "@kestra-io/kestra-sdk/flows"
import * as DashboardsAPI from "@kestra-io/kestra-sdk/dashboards"
import {useAppDraftActions} from "override/components/ai/copilot/appDraftActions"
import {useMiscStore} from "override/stores/misc"
import {useFlowStore} from "../../../stores/flow"
import type {ArtefactDraftEvent} from "./types"

/**
 * Actions for an AI-drafted artefact:
 *   - `openInEditor` — hand the drafted YAML to the matching creation editor to review + save there.
 *   - `apply` — create (or update) the artefact directly, behind a confirm.
 *
 * Handles flow and dashboard drafts. Custom dashboards are EE-only: in OSS `dashboards/create`
 * resolves to the Enterprise demo page and the CRUD API is locked, so dashboard drafts only ever
 * occur in EE. Apps are EE-only too; they have no OSS editor/API, so that path is added in `ui-ee`.
 */
export function useApplyDraft() {
    const route = useRoute()
    const router = useRouter()
    const {t} = useI18n()

    /** True while a direct apply is in flight (disables the button). */
    const applying = ref(false)

    // App drafts are EE-only; OSS reports them unsupported (no-op). EE shadows this via `override/`.
    const appActions = useAppDraftActions()
    const appSupported = appActions.supported

    // Dashboard drafts are only actionable when the backend can serve custom dashboards
    // (`GET /configs` capability flag, false once custom dashboards are locked in OSS).
    // The store is resolved lazily inside the computed for the same reason as the flow
    // store below: merely rendering a draft card must not require Pinia to be set up.
    const dashboardSupported = computed(() => useMiscStore().configs?.isCustomDashboardsEnabled !== false)

    const tenantParam = (): Record<string, string | string[]> => (route.params.tenant ? {tenant: route.params.tenant} : {})

    // Per-request client option (the SDK endpoints' SECOND arg, spread into the request options and
    // read by the global error interceptor). `showMessageOnError: false` opts this call out of the
    // global error toast — we handle failures locally: a create that hits "already exists" is an
    // expected step of the create→update fallback, and any real failure gets our own alert.
    const silent = {showMessageOnError: false} as Parameters<typeof FlowsAPI.createFlow>[1]

    /** (A) Open the drafted YAML in the matching creation editor to review + save there. */
    function openInEditor(draft: ArtefactDraftEvent): void {
        if (draft.kind === "APP") {
            appActions.openInEditor(draft)
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
        await applyFlow(draft)
    }

    async function applyFlow(draft: ArtefactDraftEvent): Promise<void> {
        const {namespace, id} = parseYaml(draft.yaml)
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
        const {id} = parseYaml(draft.yaml)
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

    function confirmApply(message: string, title: string): Promise<boolean> {
        return KsMessageBox.confirm(message, title, {
            type: "warning",
            confirmButtonText: t("ai.copilot.draft.apply"),
            cancelButtonText: t("cancel"),
        }).then(() => true).catch(() => false)
    }

    async function alertError(e: unknown, fallback: string, title: string): Promise<void> {
        const err = e as {response?: {data?: {message?: string}}; message?: string}
        await KsMessageBox.alert(err?.response?.data?.message ?? err?.message ?? fallback, title, {type: "error"})
    }

    /** Parse an artefact's namespace + id out of its YAML; empty strings when they can't be read. */
    function parseYaml(yaml: string): {namespace: string; id: string} {
        try {
            const parsed = YAML_UTILS.parse(yaml)
            return {namespace: parsed?.namespace ?? "", id: parsed?.id ?? ""}
        } catch {
            return {namespace: "", id: ""}
        }
    }

    /**
     * A create failed because the artefact already exists (→ update instead). The SDK throws the
     * parsed error body with a `.response = {status, data}` attached; the "already exists" text can
     * sit at `data.message` or nested in the validation errors (`_embedded.errors[].message`), so
     * match against the whole serialized body rather than a single field.
     */
    function isAlreadyExists(e: unknown): boolean {
        const err = e as {status?: number; response?: {status?: number; data?: unknown}}
        const status = err?.response?.status ?? err?.status
        if (status !== 422) return false
        const body = err?.response?.data ?? err
        return /already exists/i.test(typeof body === "string" ? body : JSON.stringify(body ?? ""))
    }

    return {applying, appSupported, dashboardSupported, openInEditor, apply}
}
