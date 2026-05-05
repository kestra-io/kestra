<template>
    <ElBreadcrumb
        v-bind="({...filteredProps(), ...$attrs} as any)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElBreadcrumb>
</template>

<script setup lang="ts">
    import {ElBreadcrumb} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        separator?: string
         
        separatorIcon?: any
    }>()

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/breadcrumb';

    .kel--breadcrumb {
        display: flex;

        a {
            font-weight: normal;
            color: var(--ks-content-primary) !important;
            white-space: nowrap;
            cursor: pointer !important;
        }

        .kel--breadcrumb__separator {
            color: var(--ks-content-tertiary);
        }

        .kel--breadcrumb__item {
            display: flex;
            flex-wrap: nowrap;
            float: none;
        }

        .material-design-icon {
            height: 0.75rem;
            width: 0.75rem;
            margin-right: .5rem;
        }

        html.dark & {
            .kel--breadcrumb__separator {
                color: var(--ks-content-secondary) !important;
            }
        }
    }
</style>
