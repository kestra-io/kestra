import {ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {useI18n} from "vue-i18n"
import {KsMessageBox} from "@kestra-io/design-system"
import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"
import * as FlowsAPI from "@kestra-io/kestra-sdk/flows"
import type {ArtefactDraftEvent} from "./types"

/**
 * Actions for an AI-drafted artefact:
 *   - `openInEditor` — hand the drafted YAML to the flow-creation editor to review + save there.
 *   - `apply` — create (or update) the flow directly, behind a confirm.
 *
 * Flow-only for now: apps have no OSS editor and dashboards use a separate store (future work).
 */
export function useApplyDraft() {
    const route = useRoute()
    const router = useRouter()
    const {t} = useI18n()

    /** True while a direct apply is in flight (disables the button). */
    const applying = ref(false)

    /**
     * (A) Open the drafted YAML in the flow-creation editor. Reuses the same `blueprintSourceYaml`
     * handoff the "Use blueprint" / MCP-tool flows use — FlowCreate.vue seeds the editor from it.
     */
    function openInEditor(draft: ArtefactDraftEvent): void {
        router.push({
            name: "flows/create",
            query: {blueprintId: "copilot-draft", blueprintSourceYaml: draft.yaml},
            ...(route.params.tenant ? {params: {tenant: route.params.tenant}} : {}),
        })
    }

    /** (B) Create or update the flow directly. Confirms first; wording depends on whether it exists. */
    async function apply(draft: ArtefactDraftEvent): Promise<void> {
        const {namespace, id} = parseFlowId(draft.yaml)
        if (!namespace || !id) {
            await KsMessageBox.alert(t("ai.copilot.draft.applyNoTarget"), t("ai.copilot.draft.applyTitle"), {type: "error"})
            return
        }

        const confirmed = await KsMessageBox.confirm(
            t("ai.copilot.draft.applyConfirm", {namespace, id}),
            t("ai.copilot.draft.applyTitle"),
            {type: "warning", confirmButtonText: t("ai.copilot.draft.apply"), cancelButtonText: t("cancel")},
        ).then(() => true).catch(() => false)
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
            router.push({
                name: "flows/update",
                params: {namespace, id, ...(route.params.tenant ? {tenant: route.params.tenant} : {})},
            })
        } catch (e) {
            const err = e as {response?: {data?: {message?: string}}; message?: string}
            await KsMessageBox.alert(
                err?.response?.data?.message ?? err?.message ?? t("ai.copilot.draft.applyError"),
                t("ai.copilot.draft.applyTitle"),
                {type: "error"},
            )
        } finally {
            applying.value = false
        }
    }

    /** Parse the flow's namespace + id out of its YAML; empty strings when it can't be read. */
    function parseFlowId(yaml: string): {namespace: string; id: string} {
        try {
            const parsed = YAML_UTILS.parse(yaml)
            return {namespace: parsed?.namespace ?? "", id: parsed?.id ?? ""}
        } catch {
            return {namespace: "", id: ""}
        }
    }

    /** A create failed because the flow already exists (→ update instead), mirroring the flow store. */
    function isAlreadyExists(e: unknown): boolean {
        const err = e as {response?: {status?: number; data?: {message?: string}}}
        return err?.response?.status === 422 && !!err?.response?.data?.message?.includes("Flow id already exists")
    }

    return {applying, openInEditor, apply}
}
