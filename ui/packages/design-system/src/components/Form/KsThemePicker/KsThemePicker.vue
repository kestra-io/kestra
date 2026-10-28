<template>
    <div
        class="theme-picker"
        :class="{'theme-picker--grid': columns}"
        :style="columns ? {'--theme-picker-columns': columns} : undefined"
        role="radiogroup"
        :aria-label="ariaLabel"
    >
        <KsButton
            v-for="option in options"
            :key="option.value"
            type="default"
            text
            nativeType="button"
            role="radio"
            :aria-checked="option.value === modelValue"
            :aria-label="option.label"
            class="theme-picker__option"
            :class="{'theme-picker__option--selected': option.value === modelValue}"
            @click="emit('update:modelValue', option.value)"
        >
            <span class="theme-picker__content">
                <span class="theme-picker__preview" :class="{'theme-picker__preview--custom': $slots.preview}">
                    <slot name="preview" :option="option">
                        <template v-if="option.preview === 'sync'">
                            <ThemeWindow class="theme-picker__window theme-picker__window--dark-2" />
                            <ThemeWindow class="theme-picker__window theme-picker__window--light theme-picker__window--sync" />
                        </template>
                        <ThemeWindow v-else-if="option.preview" class="theme-picker__window" :class="`theme-picker__window--${option.preview}`" />
                    </slot>
                </span>
                <KsText tag="span" size="small" class="theme-picker__label">{{ option.label }}</KsText>
            </span>
        </KsButton>
    </div>
</template>

<script setup lang="ts">
    import KsButton from "../../Basic/KsButton/KsButton.vue"
    import KsText from "../../Basic/KsText.vue"
    import ThemeWindow from "./ThemeWindow.vue"

    export type ThemeOption = {
        value: string
        label: string
        preview?: "dark-2" | "dark" | "light" | "sync"
    }

    defineProps<{
        modelValue?: string
        options: ThemeOption[]
        ariaLabel?: string
        columns?: number
    }>()

    defineSlots<{
        preview?: (props: {option: ThemeOption}) => unknown
    }>()

    const emit = defineEmits<{
        "update:modelValue": [value: string]
    }>()
</script>

<style scoped>
    .theme-picker {
        display: flex;
        gap: 0.5rem;
    }

    .theme-picker--grid {
        display: grid;
        grid-template-columns: repeat(var(--theme-picker-columns), minmax(0, 1fr));
    }

    .theme-picker__option.kel-button {
        --ks-button-content-width: 100%;
        flex: 1 1 0;
        min-width: 0;
        margin: 0;
        padding: 0;
        width: auto;
        height: auto;
        min-height: 0;

        &,
        &:hover,
        &:focus,
        &:focus-visible,
        &:active {
            background: none;
            border: none;
            box-shadow: none;
            outline: none;
        }
    }

    .theme-picker__content {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        width: 100%;
        padding: 0.5rem;
        border-radius: var(--ks-radius-base);
        transition: background-color 0.15s ease, box-shadow 0.15s ease;
    }

    .theme-picker__option:hover .theme-picker__content {
        background-color: var(--ks-bg-active);
        box-shadow: inset 0 0 0 1px var(--ks-border-strong);
    }

    .theme-picker__option:focus-visible .theme-picker__content {
        box-shadow: inset 0 0 0 2px var(--ks-border-focus);
    }

    .theme-picker__preview {
        position: relative;
        display: block;
    }

    .theme-picker__window--light {
        --tp-sidebar: var(--ks-white);
        --tp-divider: var(--ks-gray-neutral-100);
        --tp-main: var(--ks-gray-neutral-50);
        --tp-bar: var(--ks-gray-neutral-100);
        --tp-panel: var(--ks-gray-neutral-100);
        --tp-frame: var(--ks-gray-neutral-100);
    }

    .theme-picker__window--dark {
        --tp-sidebar: var(--ks-gray-cool-900);
        --tp-divider: var(--ks-gray-cool-600);
        --tp-main: var(--ks-gray-cool-950);
        --tp-bar: var(--ks-gray-cool-500);
        --tp-panel: var(--ks-gray-cool-500);
        --tp-frame: var(--ks-gray-cool-600);
    }

    .theme-picker__window--dark-2 {
        --tp-sidebar: var(--ks-gray-neutral-900);
        --tp-divider: var(--ks-gray-neutral-700);
        --tp-main: var(--ks-gray-neutral-950);
        --tp-bar: var(--ks-gray-neutral-800);
        --tp-panel: var(--ks-gray-neutral-800);
        --tp-frame: var(--ks-gray-neutral-700);
    }

    .theme-picker__window--sync {
        position: absolute;
        inset: 0;
        clip-path: polygon(0 0, 58% 0, 42% 100%, 0 100%);
    }

    .theme-picker__option--selected .theme-picker__window {
        --tp-frame: var(--ks-border-focus);
    }

    .theme-picker__option--selected .theme-picker__preview--custom {
        border-radius: var(--ks-radius-base);
        box-shadow: 0 0 0 2px var(--ks-border-focus);
    }

    .theme-picker__label {
        text-align: center;
        color: var(--ks-text-primary);
        white-space: nowrap;
    }
</style>
