<template>
    <div class="copilot-composer">
        <KsInput
            v-model="draft"
            type="textarea"
            :autosize="{minRows: 1, maxRows: 6}"
            :placeholder="t('ai.copilot.placeholder')"
            :disabled="disabled"
            data-test="copilot-composer-input"
            @keydown="onKeydown"
        />

        <div class="copilot-composer-actions">
            <KsSegmented
                :modelValue="mode"
                :options="modeOptions"
                size="small"
                data-test="copilot-mode-selector"
                @change="onModeChange"
            />

            <KsButton
                type="primary"
                :icon="ArrowUp"
                :disabled="!canSubmit"
                :aria-label="t('ai.copilot.send')"
                data-test="copilot-send"
                @click="submit"
            />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue"
    import {useI18n} from "vue-i18n"
    import ArrowUp from "vue-material-design-icons/ArrowUp.vue"
    import type {Mode} from "./types"

    const props = defineProps<{
        mode: Mode
        /** Disables input while a turn is streaming or awaiting confirmation. */
        disabled?: boolean
    }>()

    const emit = defineEmits<{
        (e: "submit", prompt: string): void
        (e: "update:mode", mode: Mode): void
    }>()

    const {t} = useI18n()

    const draft = ref("")

    // Values are the backend Mode enum; labels follow the Figma wording
    // (EDIT is surfaced as "Build").
    const modeOptions = computed<{label: string; value: Mode}[]>(() => [
        {label: t("ai.copilot.mode.ask"), value: "ASK"},
        {label: t("ai.copilot.mode.edit"), value: "EDIT"},
        {label: t("ai.copilot.mode.plan"), value: "PLAN"},
    ])

    const canSubmit = computed(() => !props.disabled && draft.value.trim().length > 0)

    function submit(): void {
        if (!canSubmit.value) return
        emit("submit", draft.value.trim())
        draft.value = ""
    }

    // Enter submits; Shift+Enter inserts a newline.
    function onKeydown(event: KeyboardEvent): void {
        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault()
            submit()
        }
    }

    function onModeChange(value: unknown): void {
        emit("update:mode", value as Mode)
    }
</script>

<style scoped>
    .copilot-composer {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-3);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        background: var(--ks-bg-input);
    }

    .copilot-composer-actions {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-2);
    }
</style>
