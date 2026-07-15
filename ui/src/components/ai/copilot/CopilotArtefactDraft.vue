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

        <!-- The drafted YAML — read-only preview, nothing is saved -->
        <pre class="copilot-draft-yaml" data-test="copilot-draft-yaml">{{ draft.yaml }}</pre>

        <div class="copilot-draft-footer">
            <KsButton
                type="default"
                data-test="copilot-draft-copy"
                @click="copy"
            >
                {{ copied ? t("ai.copilot.draft.copied") : t("ai.copilot.draft.copy") }}
            </KsButton>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref} from "vue"
    import {useI18n} from "vue-i18n"
    import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue"
    import type {ArtefactDraftEvent} from "./types"

    const props = defineProps<{draft: ArtefactDraftEvent}>()

    const {t} = useI18n()

    const copied = ref(false)
    let resetTimer: ReturnType<typeof setTimeout> | undefined

    // Dock fallback for "accept": there is no editor mounted alongside the global
    // copilot yet (that arrives with the flow-editor cutover), so the useful action
    // today is to hand the user the YAML to paste. Loading it into an in-place editor
    // will be wired when the copilot is embedded in the editor.
    async function copy(): Promise<void> {
        try {
            await navigator.clipboard?.writeText(props.draft.yaml)
        } catch {
            return
        }
        copied.value = true
        clearTimeout(resetTimer)
        resetTimer = setTimeout(() => (copied.value = false), 2000)
    }
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

    .copilot-draft-yaml {
        margin: 0;
        max-height: 18rem;
        overflow: auto;
        padding: var(--ks-spacing-3);
        font-family: monospace;
        font-size: var(--ks-font-size-sm);
        white-space: pre;
        color: var(--ks-text-secondary);
    }

    .copilot-draft-footer {
        display: flex;
        justify-content: flex-end;
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        background: var(--ks-bg-elevated);
    }
</style>
