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

        <!-- Apply actions. Flows + dashboards can be opened in the editor or applied directly. Apps
             are EE-only: they can only be opened in the app editor (no direct apply), and only when
             the EE app path is present — in OSS an app draft never occurs, so no actions show. -->
        <div v-if="showActions" class="copilot-draft-footer">
            <KsButton size="small" data-test="copilot-draft-open" @click="openInEditor(draft)">
                {{ $t("ai.copilot.draft.openInEditor") }}
            </KsButton>
            <KsButton
                v-if="draft.kind !== 'APP'"
                size="small"
                type="primary"
                :disabled="!draft.valid || applying"
                data-test="copilot-draft-apply"
                @click="apply(draft)"
            >
                {{ $t("ai.copilot.draft.apply") }}
            </KsButton>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue"
    import {useApplyDraft} from "./useApplyDraft"
    import type {ArtefactDraftEvent} from "./types"

    const props = defineProps<{draft: ArtefactDraftEvent}>()

    const {applying, appSupported, openInEditor, apply} = useApplyDraft()

    // Flow + dashboard drafts always have actions; app drafts only when the EE app path is present.
    const showActions = computed(
        () => props.draft.kind === "FLOW" || props.draft.kind === "DASHBOARD" || (props.draft.kind === "APP" && appSupported),
    )

    // Render the YAML as a fenced code block so KsMarkdown syntax-highlights it (matching the
    // assistant transcript), rather than showing it as flat monospace text.
    const yamlBlock = computed(() => "```yaml\n" + props.draft.yaml + "\n```")
</script>

<style scoped>
    .copilot-draft {
        /* Fill the assistant column (up to the 90% gutter) so the YAML preview uses the available
           width instead of shrinking to its longest line. */
        width: 100%;
        max-width: 90%;
        border: 1px solid var(--ks-border-subtle);
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
</style>
