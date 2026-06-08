<template>
    <div class="theme-picker" role="radiogroup">
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
                <span class="theme-picker__preview">
                    <template v-if="option.preview === 'sync'">
                        <ThemeWindow class="theme-picker__window theme-picker__window--dark-2" />
                        <ThemeWindow class="theme-picker__window theme-picker__window--light theme-picker__window--sync" />
                    </template>
                    <ThemeWindow v-else class="theme-picker__window" :class="`theme-picker__window--${option.preview}`" />
                </span>
                <KsText tag="span" size="small" class="theme-picker__label">{{ option.label }}</KsText>
            </span>
        </KsButton>
    </div>
</template>

<script setup lang="ts">
    import {KsButton, KsText} from "@kestra-io/design-system"
    import ThemeWindow from "./ThemeWindow.vue"

    export type ThemeOption = {
        value: string
        label: string
        preview: "dark-2" | "dark" | "light" | "sync"
    }

    defineProps<{
        modelValue: string
        options: ThemeOption[]
    }>()

    const emit = defineEmits<{
        "update:modelValue": [value: string]
    }>()
</script>

<style scoped lang="scss">
    .theme-picker {
        display: flex;
        gap: 0.5rem;
        flex-wrap: wrap;

        &__option.kel-button {
            padding: 0;
            width: 9rem;
            height: auto;
            min-height: 0;

            /* Strip ElButton's chrome on every interactive state.
               Visual feedback is applied to .theme-picker__content below
               to avoid Element Plus' internal pseudo-element conflicts. */
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

        &__content {
            display: flex;
            flex-direction: column;
            gap: 0.5rem;
            width: 100%;
            padding: 0.5rem;
            border-radius: var(--ks-radius-base);
            transition: background-color 0.15s ease, box-shadow 0.15s ease;
        }

        &__option:hover &__content {
            background-color: var(--ks-bg-active);
            box-shadow: inset 0 0 0 1px var(--ks-border-strong);
        }

        &__option:focus-visible &__content {
            box-shadow: inset 0 0 0 2px var(--ks-border-focus);
        }

        &__preview {
            position: relative;
            display: block;
        }

        &__window {
            &--light {
                --tp-sidebar: #ffffff;
                --tp-divider: #e9e9ee;
                --tp-main: #f7f7f8;
                --tp-bar: #e9e9ee;
                --tp-panel: #e9e9ee;
                --tp-frame: #e9e9ee;
            }

            &--dark {
                --tp-sidebar: #1e202a;
                --tp-divider: #2c303f;
                --tp-main: #14181f;
                --tp-bar: #2f3342;
                --tp-panel: #2f3342;
                --tp-frame: #2c303f;
            }

            &--dark-2 {
                --tp-sidebar: #1a1c22;
                --tp-divider: #2e2e3c;
                --tp-main: #111115;
                --tp-bar: #23252e;
                --tp-panel: #23252e;
                --tp-frame: #2e2e3c;
            }

            &--sync {
                position: absolute;
                inset: 0;
                clip-path: polygon(0 0, 58% 0, 42% 100%, 0 100%);
            }
        }

        &__option--selected &__window {
            --tp-frame: var(--ks-border-focus);
        }

        &__label {
            text-align: center;
            color: var(--ks-text-primary);
            white-space: nowrap;
        }

        &__option--selected &__label {
            color: var(--ks-text-primary, #FFF);
        }
    }
</style>
