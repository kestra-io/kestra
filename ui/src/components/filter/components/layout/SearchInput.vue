<template>
    <form :class="['form', {'full-width': fullWidth}]">
        <button type="button">
            <svg width="17" height="16" fill="none" role="img" aria-labelledby="search">
                <path d="M7.667 12.667A5.333 5.333 0 107.667 2a5.333 5.333 0 000 10.667zM14.334 14l-2.9-2.9" stroke="currentColor" stroke-width="1.333" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
        </button>
        <input
            class="input"
            :placeholder="placeholder"
            v-model="internalValue"
            @input="handleInput"
            @keydown.enter.prevent="handleEnter"
        >
        <button class="reset" type="button" @click="clearInput">
            <svg class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
        </button>
    </form>
</template>

<script setup lang="ts">
    import {ref, watch} from "vue";

    const props = defineProps<{
        modelValue: string;
        fullWidth?: boolean;
        placeholder?: string;
    }>();

    const emits = defineEmits<{
        "update:model-value": [value: string];
    }>();

    const internalValue = ref(props.modelValue);

    const handleInput = (e: Event) => {
        const value = (e.target as HTMLInputElement).value;
        emits("update:model-value", value);
    };

    const handleEnter = () => {
        emits("update:model-value", internalValue.value);
    };

    const clearInput = () => {
        internalValue.value = "";
        emits("update:model-value", "");
    };

    watch(
        () => props.modelValue,
        newVal => {
            internalValue.value = newVal;
        }
    );
</script>

<style lang="scss" scoped>
$form-timing: 0.3s;
$form-height: 32px;
$form-border-height: 1px;
$form-input-bg: var(--ks-background-input);
$form-border-color: #8405ff;
$form-border-radius: 8px;
$form-after-border-radius: 4px;
$form-box-shadow: 0 2px 4px var(--ks-card-shadow);;
$button-color: #8b8ba7;
$svg-width: 17px;
$input-font-size: 0.9rem;
$placeholder-color: var(--ks-content-tertiary);
$placeholder-font-size: 12px;

.form {
    position: relative;
    height: $form-height;
    display: flex;
    align-items: center;
    padding-inline: 0.25rem;
    border-radius: $form-border-radius;
    transition: border-radius 0.5s ease;
    background: $form-input-bg;
    border: 1px solid var(--ks-border-primary);
    box-shadow: $form-box-shadow;

    button {
        border: none;
        background: none;
        color: $button-color;
        display: flex;
        align-items: center;
        justify-content: center;
    }

    svg {
        width: $svg-width;
    }

    &:before {
        content: "";
        position: absolute;
        background: $form-border-color;
        transform: scaleX(0);
        transform-origin: center;
        width: 100%;
        height: $form-border-height;
        left: 0;
        bottom: 0;
        border-radius: $form-after-border-radius;
        transition: transform $form-timing ease;
    }

    &:focus-within {
        border-radius: $form-after-border-radius;
        border: none;

        &:before {
            transform: scale(1);
        }
    }

    &.full-width {
        margin-right: 0.5rem;
    }

    .input {
        font-size: $input-font-size;
        background-color: transparent;
        width: 100%;
        border: none;
        display: flex;
        align-items: center;

        &:focus {
            outline: none;
        }

        &::placeholder {
            color: $placeholder-color;
            font-size: $placeholder-font-size;
            line-height: 1;
        }

        &:not(:placeholder-shown)~.reset {
            opacity: 1;
            visibility: visible;
        }
    }

    .reset {
        border: none;
        background: none;
        opacity: 0;
        visibility: hidden;
    }
}
</style>