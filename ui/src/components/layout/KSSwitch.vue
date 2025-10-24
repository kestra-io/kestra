<template>
    <label class="switch">
        <input
            type="checkbox"
            :checked="modelValue ?? false"
            @change="handleChange"
        >
        <div class="slider">
            <div class="circle">
                <svg
                    v-if="showIcon !== false"
                    xmlns="http://www.w3.org/2000/svg"
                    version="1.1"
                    xmlns:xlink="http://www.w3.org/1999/xlink"
                    width="6"
                    height="6"
                    x="0"
                    y="0"
                    viewBox="0 0 365.696 365.696"
                    style="enable-background:new 0 0 512 512"
                    xml:space="preserve"
                    class="cross"
                >
                    <g>
                        <path
                            d="M243.188 182.86 356.32 69.726c12.5-12.5 12.5-32.766 0-45.247L341.238 9.398c-12.504-12.503-32.77-12.503-45.25 0L182.86 122.528 69.727 9.374c-12.5-12.5-32.766-12.5-45.247 0L9.375 24.457c-12.5 12.504-12.5 32.77 0 45.25l113.152 113.152L9.398 295.99c-12.503 12.503-12.503 32.769 0 45.25L24.48 356.32c12.5 12.5 32.766 12.5 45.247 0l113.132-113.132L295.99 356.32c12.503 12.5 32.769 12.5 45.25 0l15.081-15.082c12.5-12.504 12.5-32.77 0-45.25zm0 0"
                            fill="currentColor"
                            data-original="#000000"
                        />
                    </g>
                </svg>
                <svg
                    v-if="showIcon !== false"
                    xmlns="http://www.w3.org/2000/svg"
                    version="1.1"
                    xmlns:xlink="http://www.w3.org/1999/xlink"
                    width="10"
                    height="10"
                    x="0"
                    y="0"
                    viewBox="0 0 24 24"
                    style="enable-background:new 0 0 512 512"
                    xml:space="preserve"
                    class="checkmark"
                >
                    <g>
                        <path
                            d="M9.707 19.121a.997.997 0 0 1-1.414 0l-5.646-5.647a1.5 1.5 0 0 1 0-2.121l.707-.707a1.5 1.5 0 0 1 2.121 0L9 14.171l9.525-9.525a1.5 1.5 0 0 1 2.121 0l.707.707a1.5 1.5 0 0 1 0 2.121z"
                            fill="currentColor"
                            data-original="#000000"
                            class=""
                        />
                    </g>
                </svg>
            </div>
        </div>
    </label>
</template>

<script setup lang="ts">
    defineProps<{
        modelValue?: boolean;
        showIcon?: boolean;
    }>();

    const emits = defineEmits<{
        "update:modelValue": [value: boolean];
    }>();

    const handleChange = (event: Event) => {
        const target = event.target as HTMLInputElement;
        emits("update:modelValue", target.checked);
    };
</script>

<style lang="scss" scoped>
.switch {
    --switch-width: 42px;
    --switch-height: 24px;
    --switch-bg: var(--ks-background-input);
    --switch-checked-bg: var(--ks-button-background-primary);
    --switch-offset: calc((var(--switch-height) - var(--circle-diameter)) / 2);
    --switch-transition: all 0.2s cubic-bezier(0.27, 0.2, 0.25, 1.51);
    --circle-diameter: 18px;
    --circle-bg: #fff;
    --circle-shadow: none;
    --circle-checked-shadow: none;
    --circle-transition: var(--switch-transition);
    --icon-transition: all 0.2s cubic-bezier(0.27, 0.2, 0.25, 1.51);
    --icon-cross-color: #000;
    --icon-cross-size: 6px;
    --icon-checkmark-color: var(--switch-checked-bg);
    --icon-checkmark-size: 10px;

    display: inline-block;

    input {
        display: none;
    }

    svg {
        transition: var(--icon-transition);
        position: absolute;
        height: auto;
    }

    .checkmark {
        width: var(--icon-checkmark-size);
        height: var(--icon-checkmark-size);
        color: var(--icon-checkmark-color);
        transform: scale(0);
    }

    .cross {
        width: var(--icon-cross-size);
        color: var(--icon-cross-color);
    }

    .slider {
        box-sizing: border-box;
        width: var(--switch-width);
        height: var(--switch-height);
        background: var(--switch-bg);
        outline: 1px solid var(--ks-border-primary);
        border-radius: 999px;
        display: flex;
        align-items: center;
        position: relative;
        transition: var(--switch-transition);
        cursor: pointer;

        .circle {
            width: var(--circle-diameter);
            height: var(--circle-diameter);
            background: var(--circle-bg);
            border-radius: inherit;
            box-shadow: var(--circle-shadow);
            display: flex;
            align-items: center;
            justify-content: center;
            transition: var(--circle-transition);
            z-index: 1;
            position: absolute;
            left: var(--switch-offset);
            html.light & {
                background: #e1e1e1;
            }
        }
    }

    input:checked + .slider {
        background: var(--switch-checked-bg);

        .checkmark {
            transform: scale(1);
        }

        .cross {
            transform: scale(0);
        }

        .circle {
            left: calc(100% - var(--circle-diameter) - var(--switch-offset));
            box-shadow: var(--circle-checked-shadow);
            background: var(--circle-bg);
        }
    }
}
</style>