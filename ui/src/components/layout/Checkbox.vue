<template>
    <label class="neon-checkbox">
        <input
            type="checkbox"
            :checked="modelValue"
            @change="$emit('update-model-value', ($event.target as HTMLInputElement).checked)"
        >
        <div class="neon-checkbox__frame">
            <div class="neon-checkbox__box">
                <div class="neon-checkbox__check-container">
                    <svg viewBox="0 0 24 24" class="neon-checkbox__check">
                        <path d="M3,12.5l7,7L21,5" />
                    </svg>
                </div>
                <div class="neon-checkbox__glow" />
                <div class="neon-checkbox__borders">
                    <span /><span /><span /><span />
                </div>
            </div>
        </div>
    </label>
</template>

<script setup lang="ts">
    defineProps<{
        modelValue: boolean;
    }>();

    defineEmits<{
        "update-model-value": [value: boolean];
    }>();
</script>

<style lang="scss" scoped>
    .neon-checkbox {
        --primary: #ffffff;
        --border-color: var(--ks-border-primary);
        --background-checked: var(--ks-border-active);
        --size: 16px;
        position: relative;
        width: var(--size);
        height: var(--size);
        cursor: pointer;
        -webkit-tap-highlight-color: transparent;

        input {
            display: none;
        }

        &__frame {
            position: relative;
            width: 100%;
            height: 100%;

            .neon-checkbox__box {
                position: absolute;
                inset: 0;
                background: transparent;
                border-radius: 2px;
                border: 1px solid var(--border-color);
                transition: all 0.4s ease;
            }

            .neon-checkbox__check-container {
                position: absolute;
                inset: 2px;
                display: flex;
                align-items: center;
                justify-content: center;
            }

            .neon-checkbox__check {
                width: 80%;
                height: 80%;
                fill: none;
                stroke: var(--primary);
                stroke-width: 4;
                stroke-linecap: round;
                stroke-linejoin: round;
                stroke-dasharray: 40;
                stroke-dashoffset: 40;
                transform-origin: center;
                transition: stroke-dashoffset 0.4s cubic-bezier(0.16, 1, 0.3, 1), transform 0.2s ease;
            }
        }

        input:checked ~ &__frame {
            .neon-checkbox__box {
                border: 1px solid var(--border-color);
                background: var(--background-checked);
            }

            .neon-checkbox__check {
                stroke-dashoffset: 0;
                transform: scale(1.1);
            }

            .neon-checkbox__glow {
                opacity: 0.2;
            }

            .neon-checkbox__borders span {
                opacity: 1;
            }
        }
    }
</style>