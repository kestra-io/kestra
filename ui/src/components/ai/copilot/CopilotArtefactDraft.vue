<template>
    <div class="copilot-draft" data-test="copilot-draft">
        <div class="copilot-draft-header">
            <KsText size="small" class="copilot-draft-title">
                <KsIcon class="copilot-draft-icon"><FileDocumentOutline /></KsIcon>
                {{ $t(`ai.copilot.draft.title.${draft.kind.toLowerCase()}`) }}
            </KsText>
            <KsCodeStatus
                :status="draft.valid ? 'valid' : 'error'"
                :label="draft.valid ? $t('ai.copilot.draft.valid') : $t('ai.copilot.draft.invalid')"
            />
        </div>

        <KsAlert
            v-if="!draft.valid && draft.constraints"
            type="warning"
            :closable="false"
            class="copilot-draft-constraints"
        >
            {{ draft.constraints }}
        </KsAlert>

        <!-- The drafted YAML — read-only, syntax-highlighted preview (nothing is saved).
             KsMarkdown provides its own copy-to-clipboard control, so no separate copy button. -->
        <KsMarkdown class="copilot-draft-yaml" data-test="copilot-draft-yaml" :content="yamlBlock" />

        <!-- Once dismissed or applied, the card stops locking the editor (CopilotChat.vue excludes it
             from the pending-draft scan) and the footer no longer offers actions — a quiet status line
             instead, matching the cancelled-turn treatment in CopilotMessage.vue. -->
        <div v-if="dismissed" class="copilot-draft-footer" data-test="copilot-draft-dismissed">
            <KsText size="small" class="copilot-draft-status-label">{{ $t("ai.copilot.draft.dismissed") }}</KsText>
        </div>
        <div v-else-if="applied" class="copilot-draft-footer" data-test="copilot-draft-applied">
            <KsText size="small" class="copilot-draft-status-label">{{ $t("ai.copilot.draft.applied") }}</KsText>
        </div>
        <!-- Dismiss is always offered: it's the only way to decline a draft, even one with no other actions. -->
        <div v-else class="copilot-draft-footer">
            <KsButton
                text
                size="small"
                class="copilot-draft-dismiss"
                data-test="copilot-draft-dismiss"
                @click="emit('dismiss', draft.draftId)"
            >
                {{ $t("ai.copilot.draft.dismiss") }}
            </KsButton>
            <template v-if="showActions">
                <KsButton size="small" data-test="copilot-draft-open" @click="openInEditor(draft)">
                    {{ $t("ai.copilot.draft.openInEditor") }}
                </KsButton>
                <KsButton
                    v-if="draft.kind !== 'APP'"
                    size="small"
                    type="primary"
                    :disabled="!draft.valid || applying"
                    data-test="copilot-draft-apply"
                    @click="onApply"
                >
                    {{ $t("ai.copilot.draft.apply") }}
                </KsButton>
            </template>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue"
    import {useApplyDraft} from "./useApplyDraft"
    import type {ArtefactDraftEvent} from "./types"

    const props = defineProps<{
        draft: ArtefactDraftEvent
        /** True once this draft was dismissed (tracked by `CopilotChat.vue`) — hides the actions. */
        dismissed?: boolean
        /** True once this draft was applied (tracked by `CopilotChat.vue`) — hides the actions. */
        applied?: boolean
    }>()

    const emit = defineEmits<{
        (e: "dismiss", draftId: string): void
        (e: "applied", draftId: string): void
    }>()

    const {applying, appSupported, dashboardSupported, testSuiteSupported, openInEditor, apply} = useApplyDraft()

    // Flow drafts always have actions; the others only when their backend or EE path is present.
    const showActions = computed(
        () => props.draft.kind === "FLOW"
            || (props.draft.kind === "DASHBOARD" && dashboardSupported.value)
            || (props.draft.kind === "APP" && appSupported)
            || (props.draft.kind === "TEST_SUITE" && testSuiteSupported),
    )

    async function onApply(): Promise<void> {
        if (await apply(props.draft)) emit("applied", props.draft.draftId)
    }

    // Render the YAML as a fenced code block so KsMarkdown syntax-highlights it (matching the
    // assistant transcript), rather than showing it as flat monospace text.
    const yamlBlock = computed(() => "```yaml\n" + props.draft.yaml + "\n```")
</script>

<style scoped>
    .copilot-draft {
        /* Fill the transcript column so the YAML preview uses the available width instead of
           shrinking to its longest line. */
        width: 100%;
        /* --ks-border-default, not -subtle: in light theme -subtle is the same gray as the card's
           --ks-bg-base, so a -subtle border is invisible (it shows fine in dark). */
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-lg);
        overflow: hidden;
        background: var(--ks-bg-base);
    }

    .copilot-draft-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        background: var(--ks-bg-elevated);
    }

    .copilot-draft-title {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        font-size: var(--ks-font-size-sm);
    }

    .copilot-draft-constraints {
        margin: var(--ks-spacing-2) var(--ks-spacing-3) 0;
    }

    /* Constrain + scroll the highlighted block; KsMarkdown styles the code itself. */
    .copilot-draft-yaml {
        margin: 0;
        max-height: 18rem;
        overflow: auto;
        padding: var(--ks-spacing-3);
        font-size: var(--ks-font-size-sm);
    }

    .copilot-draft-footer {
        display: flex;
        justify-content: flex-end;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        background: var(--ks-bg-elevated);
    }

    /* "Dismiss" reads as a subdued secondary link next to the primary Apply, matching
       ProposedActionCard's "Reply to revise" treatment. */
    .copilot-draft-dismiss {
        --ks-button-text-color: var(--ks-text-secondary);
        color: var(--ks-text-secondary);
    }

    .copilot-draft-status-label {
        --kel-text-color: var(--ks-text-muted);
        font-style: italic;
    }
</style>
