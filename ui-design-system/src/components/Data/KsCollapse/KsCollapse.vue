<script setup lang="ts">
    import {ElCollapse, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        modelValue?: string | string[]
        accordion?: boolean
    }>()

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        "update:modelValue": [value: string | string[]]
        change: [value: string | string[]]
    }>()

    defineSlots<{
        default?(): unknown
    }>()
</script>

<template>
    <el-collapse
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @update:model-value="emit('update:modelValue', $event as string | string[])"
        @change="emit('change', $event as string | string[])"
    >
        <template v-if="$slots.default" #default><slot /></template>
    </el-collapse>
</template>

<style lang="scss">
    .kel-collapse {
        --kel-collapse-content-text-color: var(--ks-content-primary);
        --kel-collapse-header-text-color: var(--ks-content-primary);
        --kel-collapse-header-border-color: var(--ks-border-primary);
        --kel-collapse-header-height: auto;
        --kel-collapse-header-font-size: var(--font-size-base);
        --kel-collapse-content-font-size: var(--font-size-base);
        --kel-collapse-border-color: var(--ks-border-primary);

        border: none;

        .kel-collapse-item__header {
            padding: .5rem;
            border: none;
        }

        .kel-collapse-item__content {
            padding: .5rem;
        }

        .kel-collapse-item__wrap {
            border: none;
        }
    }
</style>