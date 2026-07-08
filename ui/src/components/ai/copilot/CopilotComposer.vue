<template>
    <div class="copilot-composer">
        <!--
            Native borderless textarea inside the bordered wrapper so the composer reads as a
            single box (Figma). KsInput/el-textarea can't be made borderless without overriding
            its `.el-textarea__inner` inset-shadow border, which the design-system rules forbid.
        -->
        <textarea
            ref="textareaEl"
            v-model="draft"
            class="copilot-textarea"
            :rows="rows ?? 1"
            :placeholder="placeholder ?? t('ai.copilot.placeholder')"
            :disabled="disabled"
            data-test="copilot-composer-input"
            @keydown="onKeydown"
            @input="autosize"
        />

        <div class="copilot-composer-actions">
            <KsDropdown trigger="click" data-test="copilot-mode-selector">
                <KsButton size="small" class="copilot-mode-trigger">
                    <span class="copilot-mode-item">
                        <component :is="currentMode?.icon" :size="16" />
                        {{ currentMode?.label }}
                        <ChevronDown class="copilot-mode-chevron" />
                    </span>
                </KsButton>
                <template #dropdown>
                    <KsDropdownMenu>
                        <KsDropdownItem
                            v-for="option in modeOptions"
                            :key="option.value"
                            :class="{'copilot-mode-item--active': option.value === mode}"
                            @click="emit('update:mode', option.value)"
                        >
                            <span class="copilot-mode-item">
                                <component :is="option.icon" :size="16" />
                                {{ option.label }}
                            </span>
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
    import {ref, computed, nextTick, watch, type Component} from "vue"
    import {useI18n} from "vue-i18n"
    import ArrowUp from "vue-material-design-icons/ArrowUp.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    // Per-mode icons taken from the UI-2.0 Figma: Build → wrench ("build"), Plan → map.
    // Ask isn't shown in that file, so we use a Q&A chat icon to match its read-only intent.
    import ChatQuestionOutline from "vue-material-design-icons/ChatQuestionOutline.vue"
    import Wrench from "vue-material-design-icons/Wrench.vue"
    import MapOutline from "vue-material-design-icons/MapOutline.vue"
    import type {Mode} from "./types"

    const props = defineProps<{
        mode: Mode
        /** Disables input while a turn is streaming or awaiting confirmation. */
        disabled?: boolean
        /** Overrides the placeholder (e.g. the descriptive helper text in the empty state). */
        placeholder?: string
        /** Initial visible rows (empty state uses more so the helper text wraps); collapses on input. */
        rows?: number
    }>()

    const emit = defineEmits<{
        (e: "submit", prompt: string): void
        (e: "update:mode", mode: Mode): void
    }>()

    const {t} = useI18n()

    const draft = ref("")
    const textareaEl = ref<HTMLTextAreaElement>()

    // Values are the backend Mode enum; labels follow the Figma wording
    // (EDIT is surfaced as "Build"); icons match the Figma mode pills.
    const modeOptions = computed<{label: string; value: Mode; icon: Component}[]>(() => [
        {label: t("ai.copilot.mode.ask"), value: "ASK", icon: ChatQuestionOutline},
        {label: t("ai.copilot.mode.edit"), value: "EDIT", icon: Wrench},
        {label: t("ai.copilot.mode.plan"), value: "PLAN", icon: MapOutline},
    ])

    const currentMode = computed(() => modeOptions.value.find((o) => o.value === props.mode))

    const canSubmit = computed(() => !props.disabled && draft.value.trim().length > 0)

    // Grow the textarea with its content, up to the CSS max-height (then it scrolls).
    function autosize(): void {
        const el = textareaEl.value
        if (!el) return
        el.style.height = "auto"
        el.style.height = `${el.scrollHeight}px`
    }

    function submit(): void {
        if (!canSubmit.value) return
        emit("submit", draft.value.trim())
        draft.value = ""
    }

    // Keep the height in sync when the draft is cleared (e.g. after submit).
    watch(draft, () => nextTick(autosize))

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
        width: 100%;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-4);
        border: 1px solid var(--ks-border-strong);
        border-radius: var(--ks-radius-lg);
        background: var(--ks-bg-input);
        box-shadow: var(--ks-shadow-element);
    }

    .copilot-textarea {
        width: 100%;
        max-height: 9rem;
        border: none;
        outline: none;
        resize: none;
        padding: 0;
        background: transparent;
        color: var(--ks-text-primary);
        font: inherit;
        line-height: 1.5;
    }

    .copilot-textarea::placeholder {
        color: var(--ks-text-secondary);
    }

    .copilot-composer-actions {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-2);
    }

    /* Figma mode control: a subtle bg-tag pill (label + chevron), not a solid button. */
    .copilot-mode-trigger {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-2);
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

    .copilot-mode-item {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-3);
    }
</style>
