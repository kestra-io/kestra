import type {ArtefactDraftEvent} from "../../../../components/ai/copilot/types"

/**
 * Actions for an AI-drafted **unit test** artefact. Flow unit tests (test suites) are an EE-only
 * feature — they have no OSS editor or API, and `author-test-suite` (and therefore TEST_SUITE
 * drafts) only exist in EE. So the OSS implementation is a no-op that reports them unsupported;
 * EE shadows this file via the `override/` alias.
 */
export interface TestSuiteDraftActions {
    /** Whether unit-test drafts can be acted on (true only in EE). */
    readonly supported: boolean
    /** Open the drafted YAML in the test editor to review + save there. */
    openInEditor(draft: ArtefactDraftEvent): void
    /** Create (or update) the test suite directly, behind a confirm. */
    apply(draft: ArtefactDraftEvent): Promise<void>
}

export function useTestSuiteDraftActions(): TestSuiteDraftActions {
    return {supported: false, openInEditor: () => {}, apply: async () => {}}
}
