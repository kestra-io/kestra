<template>
    <div class="copilot-draft" data-test="copilot-draft">
        <!-- Header band: what was drafted + whether it validates -->
        <div class="copilot-draft-header">
            <KsText size="small" class="copilot-draft-title">
                <KsIcon class="copilot-draft-icon"><FileDocumentOutline /></KsIcon>
                {{ t(`ai.copilot.draft.title.${draft.kind.toLowerCase()}`) }}
            </KsText>
            <KsCodeStatus
                :status="draft.valid ? 'valid' : 'error'"
                :label="draft.valid ? t('ai.copilot.draft.valid') : t('ai.copilot.draft.invalid')"
            />
        </div>

        <!-- Validation issues to fix, when the draft didn't pass -->
        <KsAlert
            v-if="!draft.valid && draft.constraints"
            type="warning"
            :closable="false"
            class="copilot-draft-constraints"
        >
            {{ draft.constraints }}
        </KsAlert>

        <!-- The drafted YAML — read-only, syntax-highlighted preview (nothing is saved).
             KsMarkdown provides its own copy-to-clipboard control, so no separate copy button.
             A one-click "apply" (load into the flow editor) arrives with the flow-editor cutover. -->
        <KsMarkdown class="copilot-draft-yaml" data-test="copilot-draft-yaml" :content="yamlBlock" />
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue"
    import type {ArtefactDraftEvent} from "./types"

    const props = defineProps<{draft: ArtefactDraftEvent}>()

    const {t} = useI18n()

    // Render the YAML as a fenced code block so KsMarkdown syntax-highlights it (matching the
    // assistant transcript), rather than showing it as flat monospace text.
    const yamlBlock = computed(() => "```yaml\n" + props.draft.yaml + "\n```")
</script>

<style scoped>
    .copilot-draft {
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
</style>
