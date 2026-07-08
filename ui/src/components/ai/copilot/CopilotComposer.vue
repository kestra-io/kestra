<template>
    <div class="copilot-composer">
        <KsInput
            v-model="draft"
            type="textarea"
            :autosize="{minRows: 1, maxRows: 6}"
            :placeholder="placeholder ?? t('ai.copilot.placeholder')"
            :disabled="disabled"
            data-test="copilot-composer-input"
            @keydown="onKeydown"
        />

        <div class="copilot-composer-actions">
            <KsDropdown trigger="click" data-test="copilot-mode-selector">
                <KsButton size="small" class="copilot-mode-trigger">
                    {{ currentModeLabel }}
                    <ChevronDown class="copilot-mode-chevron" />
                </KsButton>
                <template #dropdown>
                    <KsDropdownMenu>
                        <KsDropdownItem
                            v-for="option in modeOptions"
                            :key="option.value"
                            :class="{'copilot-mode-item--active': option.value === mode}"
                            @click="emit('update:mode', option.value)"
                        >
                            {{ option.label }}
                        </KsDropdownItem>
                    </KsDropdownMenu>
                </template>
            </KsDropdown>

            <KsButton
                circle
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
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import type {Mode} from "./types"

    const props = defineProps<{
        mode: Mode
        /** Disables input while a turn is streaming or awaiting confirmation. */
        disabled?: boolean
        /** Overrides the placeholder (e.g. the descriptive helper text in the empty state). */
        placeholder?: string
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

    const currentModeLabel = computed(() => modeOptions.value.find((o) => o.value === props.mode)?.label)

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
</script>

<style scoped>
    .copilot-composer {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-4);
        padding: var(--ks-spacing-4);
        border: 1px solid var(--ks-border-strong);
        border-radius: var(--ks-radius-lg);
        background: var(--ks-bg-input);
        box-shadow: var(--ks-shadow-element);
    }

    .copilot-composer-actions {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-2);
    }

    /* Figma mode control: a subtle bg-tag pill (icon/label + chevron), not a solid button. */
    .copilot-mode-trigger {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        background: var(--ks-bg-tag);
        border: none;
        color: var(--ks-text-primary);
        border-radius: var(--ks-radius-sm);
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
    }

    .copilot-mode-chevron {
        display: inline-flex;
        font-size: 1rem;
        color: var(--ks-text-secondary);
    }
</style>
