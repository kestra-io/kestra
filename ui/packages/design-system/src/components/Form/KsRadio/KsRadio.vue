<template>
    <ElRadio
        v-model="model"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElRadio>
</template>

<script setup lang="ts">
    import {ElRadio} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<string | number | boolean>()

    const props = defineProps<{
        value?: string | number | boolean
        label?: string | number | boolean
        disabled?: boolean
    }>()

    const emit = defineEmits<{
        change: [value: any]
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/radio';

    .kel-radio {
        .kel-radio__inner::after {
            width: 6px;
            height: 6px;
        }

        .kel-radio__input.is-checked + .kel-radio__label {
            color: var(--kel-radio-text-color);
        }
    }
</style>
