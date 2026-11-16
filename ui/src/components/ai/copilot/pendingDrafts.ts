import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
import type {ChatMessage} from "./useAiChat"
import type {ArtefactDraftEvent} from "./types"

export interface PendingDraft {
    draft: ArtefactDraftEvent
    namespace: string
    id: string
    /** The pending flow draft this draft targets (`namespace.id`), which has to be applied first. */
    waitsFor?: string
}

function parseArtefact(yaml: string): {namespace: string; id: string; flowId: string} {
    try {
        const parsed = YAML_UTILS.parse<{namespace?: string; id?: string; flowId?: string}>(yaml)
        return {namespace: parsed?.namespace ?? "", id: parsed?.id ?? "", flowId: parsed?.flowId ?? ""}
    } catch {
        return {namespace: "", id: "", flowId: ""}
    }
}

/**
 * The drafts of a thread that still wait on the user, flows first. A later draft of the same artefact
 * supersedes an earlier one, and an app that targets a flow which is itself still a pending draft is
 * flagged as waiting for it, so the flow gets saved before the resource that needs it.
 */
export function collectPendingDrafts(
    messages: ChatMessage[],
    isResolved: (draftId: string) => boolean,
): PendingDraft[] {
    const latest = new Map<string, PendingDraft & {flowId: string}>()
    for (const message of messages) {
        const draft = message.type === "ARTEFACT_DRAFT" ? message.draft : undefined
        if (!draft || isResolved(draft.draftId)) continue
        const {namespace, id, flowId} = parseArtefact(draft.yaml)
        if (!id) continue
        const key = `${draft.kind}|${namespace}|${id}`
        latest.delete(key)
        latest.set(key, {draft, namespace, id, flowId})
    }

    const pending = [...latest.values()]
    const flows = new Set(pending.filter(({draft}) => draft.kind === "FLOW").map(({namespace, id}) => `${namespace}|${id}`))
    return pending
        .map(({flowId, ...item}): PendingDraft => ({
            ...item,
            waitsFor: item.draft.kind !== "FLOW" && flowId && flows.has(`${item.namespace}|${flowId}`)
                ? `${item.namespace}.${flowId}`
                : undefined,
        }))
        .sort((a, b) => Number(b.draft.kind === "FLOW") - Number(a.draft.kind === "FLOW"))
}
