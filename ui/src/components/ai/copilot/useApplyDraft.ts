import {ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {useI18n} from "vue-i18n"
import {KsMessageBox} from "@kestra-io/design-system"
import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"
import * as FlowsAPI from "@kestra-io/kestra-sdk/flows"
import * as DashboardsAPI from "@kestra-io/kestra-sdk/dashboards"
import {useAppDraftActions} from "override/components/ai/copilot/appDraftActions"
import type {ArtefactDraftEvent} from "./types"

/**
 * Actions for an AI-drafted artefact:
 *   - `openInEditor` — hand the drafted YAML to the matching creation editor to review + save there.
 *   - `apply` — create (or update) the artefact directly, behind a confirm.
 *
 * Handles flow and dashboard drafts (both OSS features today). Apps are EE-only — they have no OSS
 * editor/API, and app drafts only ever occur in EE, so that path is added in `ui-ee`.
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

    const tenantParam = (): Record<string, string | string[]> => (route.params.tenant ? {tenant: route.params.tenant} : {})

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
                    {body: draft.yaml, draft: false, showMessageOnError: false} as Parameters<typeof FlowsAPI.createFlow>[0],
                )
            } catch (e) {
                if (!isAlreadyExists(e)) throw e
                await FlowsAPI.updateFlow(
                    {namespace, id, body: draft.yaml, showMessageOnError: false} as Parameters<typeof FlowsAPI.updateFlow>[0],
                )
            }
            // Land on the applied flow so the result is visible.
            router.push({name: "flows/update", params: {namespace, id, ...tenantParam()}})
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
                    {body: draft.yaml, showMessageOnError: false} as Parameters<typeof DashboardsAPI.createDashboard>[0],
                )
            } catch (e) {
                if (!isAlreadyExists(e)) throw e
                await DashboardsAPI.updateDashboard(
                    {id, body: draft.yaml, showMessageOnError: false} as Parameters<typeof DashboardsAPI.updateDashboard>[0],
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

    /** A create failed because the artefact already exists (→ update instead). */
    function isAlreadyExists(e: unknown): boolean {
        const err = e as {response?: {status?: number; data?: {message?: string}}}
        return err?.response?.status === 422 && /already exists/i.test(err?.response?.data?.message ?? "")
    }

    return {applying, appSupported, openInEditor, apply}
}
