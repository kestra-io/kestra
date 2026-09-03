<template>
    <ElCollapse
        v-model="model"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event as string | string[])"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElCollapse>
</template>

<script setup lang="ts">
    import {ElCollapse} from "element-plus"

    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<any>()

    const props = defineProps<{
        accordion?: boolean
    }>()

    const emit = defineEmits<{
        change: [value: string | string[]]
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/collapse';

    .kel-collapse {
        --kel-collapse-content-text-color: var(--ks-text-primary);
        --kel-collapse-header-text-color: var(--ks-text-primary);
        --kel-collapse-header-border-color: var(--ks-border-default);
        --kel-collapse-header-height: auto;
        --kel-collapse-header-font-size: var(--ks-font-size-base);
        --kel-collapse-content-font-size: var(--ks-font-size-base);
        --kel-collapse-border-color: var(--ks-border-default);

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
