import type {ArtefactDraftEvent} from "../../../../components/ai/copilot/types"

/** Unit-test drafts are EE-only, so OSS reports them unsupported; EE shadows this file via `override/`. */
export interface TestSuiteDraftActions {
    /** Whether unit-test drafts can be acted on (true only in EE). */
    readonly supported: boolean
    openInEditor(draft: ArtefactDraftEvent): void
    /** Create (or update) the test suite directly, behind a confirm; resolves true once it was written. */
    apply(draft: ArtefactDraftEvent): Promise<boolean>
}

export function useTestSuiteDraftActions(): TestSuiteDraftActions {
    return {supported: false, openInEditor: () => {}, apply: async () => false}
}
