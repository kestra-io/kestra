import type {ArtefactDraftEvent} from "../../../../components/ai/copilot/types"

/**
 * Actions for an AI-drafted **app** artefact. Apps are an EE-only feature — they have no OSS editor
 * or API, and `author-app` (and therefore APP drafts) only exist in EE. So the OSS implementation is
 * a no-op that reports apps unsupported; EE shadows this file via the `override/` alias.
 */
export interface AppDraftActions {
    /** Whether app drafts can be acted on (true only in EE). */
    readonly supported: boolean
    /** Open the drafted app YAML in the app editor to review + save (no direct write). */
    openInEditor(draft: ArtefactDraftEvent): void
}

export function useAppDraftActions(): AppDraftActions {
    return {supported: false, openInEditor: () => {}}
}
