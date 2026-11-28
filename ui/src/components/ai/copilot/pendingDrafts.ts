import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
import {parseArtefactYaml} from "./draftApply"
import type {ChatMessage} from "./useAiChat"
import type {ArtefactDraftEvent} from "./types"

export interface PendingDraft {
    draft: ArtefactDraftEvent
    namespace: string
    id: string
    /** The pending flow draft this draft targets (`namespace.id`), which has to be applied first. */
    waitsFor?: string
}

function targetFlowId(yaml: string): string {
    try {
        return YAML_UTILS.parse<{flowId?: string}>(yaml)?.flowId ?? ""
    } catch {
        return ""
    }
}

/**
 * The drafts of a thread that still wait on the user, flows first. A later draft of the same artefact
 * supersedes an earlier one, and an app or a test that targets a flow which is itself still a pending
 * draft is flagged as waiting for it, so the flow gets saved before the resource that needs it.
 */
export function collectPendingDrafts(
    messages: ChatMessage[],
    resolvedDraftIds: (draftId: string) => boolean,
    currentFlowYaml?: string,
): PendingDraft[] {
    const latest = new Map<string, PendingDraft>()
    for (const message of messages) {
        const draft = message.type === "ARTEFACT_DRAFT" ? message.draft : undefined
        if (!draft || resolvedDraftIds(draft.draftId)) continue
        const {namespace, id} = parseArtefactYaml(draft.yaml)
        if (!id) continue
        const key = `${draft.kind}|${namespace}|${id}`
        latest.delete(key)
        if (draft.kind === "FLOW" && draft.yaml === currentFlowYaml) continue
        latest.set(key, {draft, namespace, id})
    }

    const pending = [...latest.values()]
    const flows = new Set(pending.filter(({draft}) => draft.kind === "FLOW").map(({namespace, id}) => `${namespace}|${id}`))
    for (const item of pending) {
        if (item.draft.kind === "FLOW") continue
        const flowId = targetFlowId(item.draft.yaml)
        if (flowId && flows.has(`${item.namespace}|${flowId}`)) item.waitsFor = `${item.namespace}.${flowId}`
    }

    return pending.sort((a, b) => Number(b.draft.kind === "FLOW") - Number(a.draft.kind === "FLOW"))
}
