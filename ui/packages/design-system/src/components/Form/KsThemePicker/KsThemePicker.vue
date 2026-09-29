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
    import KsButton from "../../Basic/KsButton/KsButton.vue"
    import KsText from "../../Basic/KsText.vue"
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
    @use "../../../assets/styles/color-palette" as palette;

    .theme-picker {
        display: flex;
        gap: 0.5rem;

        &__option.kel-button {
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
                --tp-sidebar: #{palette.$base-gray-neutral-white};
                --tp-divider: #{palette.$base-gray-neutral-100};
                --tp-main: #{palette.$base-gray-neutral-50};
                --tp-bar: #{palette.$base-gray-neutral-100};
                --tp-panel: #{palette.$base-gray-neutral-100};
                --tp-frame: #{palette.$base-gray-neutral-100};
            }

            &--dark {
                --tp-sidebar: #{palette.$base-gray-cool-900};
                --tp-divider: #{palette.$base-gray-cool-600};
                --tp-main: #{palette.$base-gray-cool-950};
                --tp-bar: #{palette.$base-gray-cool-500};
                --tp-panel: #{palette.$base-gray-cool-500};
                --tp-frame: #{palette.$base-gray-cool-600};
            }

            &--dark-2 {
                --tp-sidebar: #{palette.$base-gray-neutral-900};
                --tp-divider: #{palette.$base-gray-neutral-700};
                --tp-main: #{palette.$base-gray-neutral-950};
                --tp-bar: #{palette.$base-gray-neutral-800};
                --tp-panel: #{palette.$base-gray-neutral-800};
                --tp-frame: #{palette.$base-gray-neutral-700};
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
    }
</style>
