<template>
    <div class="ks-radio-card-group" role="radiogroup" :aria-label="ariaLabel">
        <label
            v-for="option in options"
            :key="`${typeof option.value}:${option.value}`"
            class="card"
            :class="{selected: model === option.value, disabled: option.disabled}"
        >
            <input
                v-model="model"
                type="radio"
                :name="name"
                :value="option.value"
                :disabled="option.disabled"
                @change="emit('change', option.value)"
            >
            <span class="title">{{ option.label }}</span>
            <component
                :is="option.icon"
                v-if="option.icon"
                :size="16"
                class="icon"
            />
            <span v-if="option.hint" class="hint">{{ option.hint }}</span>
        </label>
    </div>
</template>

<script setup lang="ts">
    import {useId, type Component} from "vue"

    const model = defineModel<string | number | boolean>()

    defineProps<{
        options: {
            value: string | number | boolean
            label: string
            hint?: string
            disabled?: boolean
            icon?: Component
        }[]
        ariaLabel?: string
    }>()

    const emit = defineEmits<{
        change: [value: string | number | boolean]
    }>()

    const name = useId()
</script>

<style scoped lang="scss">
    .ks-radio-card-group {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        width: 100%;

        .card {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-4);
            padding: var(--ks-spacing-2) var(--ks-spacing-4);
            border: 1px solid var(--ks-border-default);
            border-radius: var(--ks-radius-lg);
            background: var(--ks-bg-inactive);
            color: var(--ks-text-primary);
            cursor: pointer;
            transition: all 0.15s;

            input[type="radio"] {
                appearance: none;
                -webkit-appearance: none;
                flex-shrink: 0;
                display: grid;
                place-content: center;
                width: 1.25rem;
                height: 1.25rem;
                margin: 0;
                border: 2px solid var(--ks-border-strong);
                border-radius: 50%;
                background: transparent;
                cursor: pointer;
                transition: border-color 0.15s ease;

                &::after {
                    content: "";
                    width: 0.625rem;
                    height: 0.625rem;
                    border-radius: 50%;
                    background: var(--ks-toggle-active);
                    transform: scale(0);
                    transition: transform 0.15s ease;
                }

                &:checked {
                    border-color: var(--ks-toggle-active);

                    &::after {
                        transform: scale(1);
                    }
                }

                &:focus-visible {
                    outline: 2px solid var(--ks-border-focus);
                    outline-offset: 2px;
                }
            }

            .title {
                display: inline-flex;
                align-items: center;
                gap: var(--ks-spacing-2);
                font-size: var(--ks-font-size-sm);
                color: var(--ks-text-primary);
            }

            .icon {
                flex-shrink: 0;
            }

            .hint {
                margin-left: auto;
                font-size: var(--ks-font-size-sm);
                color: var(--ks-text-secondary);
                text-align: right;
            }

            &.selected {
                border-color: var(--ks-border-strong);
                background: var(--ks-bg-active);
            }

            &.disabled {
                opacity: 0.4;
                cursor: not-allowed;

                input[type="radio"] {
                    cursor: not-allowed;
                }
            }

            &:hover:not(.selected):not(.disabled) {
                border-color: var(--ks-border-strong);
            }
        }
    }
</style>
