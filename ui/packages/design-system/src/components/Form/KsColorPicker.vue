<template>
    <ElColorPicker
        v-model="model"
        v-bind="({...filteredProps(), ...$attrs, ...(alphaOnSurface && {popperClass: [$attrs.popperClass ?? $attrs['popper-class'], ALPHA_ON_SURFACE]})} as ColorPickerProps)"
        :class="{[ALPHA_ON_SURFACE]: alphaOnSurface}"
        @change="emit('change', $event)"
        @active-change="emit('activeChange', $event as string)"
    />
</template>

<script setup lang="ts">
    import {ElColorPicker, type ColorPickerProps} from "element-plus"

    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<string | null>()

    const props = defineProps<{
        showAlpha?: boolean
        disabled?: boolean
        size?: "large" | "default" | "small"
        predefine?: string[]
        alphaOnSurface?: boolean
    }>()

    const emit = defineEmits<{
        change: [value: string | null]
        activeChange: [value: string]
    }>()

    const filteredProps = useFilteredProps(props, ["alphaOnSurface"])

    const ALPHA_ON_SURFACE = "ks-color-picker--alpha-on-surface"
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/color-picker';
    @use 'element-plus/theme-chalk/src/color-picker-panel';

    .kel-color-picker {
        .kel-color-picker__trigger {
            border-radius: var(--kel-border-radius-base);

            .kel-color-picker__color {
                border-radius: 0;
            }
        }

        &.kel-color-picker--small {
            .kel-color-picker__trigger {
                border-radius: var(--kel-border-radius-small);
            }
        }
    }

    .kel-color-predefine__color-selector {
        border: 1px solid var(--ks-border-strong);
    }

    .kel-color-picker.ks-color-picker--alpha-on-surface,
    .kel-color-picker__panel.ks-color-picker--alpha-on-surface .kel-color-picker-panel {
        --kel-color-picker-alpha-bg-a: var(--ks-bg-base);
        --kel-color-picker-alpha-bg-b: var(--ks-bg-base);
    }

    .kel-color-picker__panel.ks-color-picker--alpha-on-surface .kel-color-predefine__color-selector.is-alpha {
        background: var(--ks-bg-base);
    }
</style>
