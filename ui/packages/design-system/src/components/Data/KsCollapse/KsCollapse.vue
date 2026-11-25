<template>
    <ElCollapse
        :modelValue="props.modelValue"
        @update:modelValue="(value: CollapseModelValue) => emit('update:modelValue', value as T)"
        v-bind="({...filteredProps(), ...$attrs} as CollapseProps)"
        :class="{'is-flush': flush}"
        @change="(value: CollapseModelValue) => emit('change', value as T)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElCollapse>
</template>

<script setup lang="ts" generic="T extends CollapseModelValue = CollapseModelValue">
    import {ElCollapse, type CollapseModelValue, type CollapseProps} from "element-plus"

    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        modelValue?: T
        accordion?: boolean
        /** Compact navigation tree: no padding on headers or content, and the arrow sits next to the title (the docs table of contents). */
        flush?: boolean
    }>()

    const emit = defineEmits<{
        "update:modelValue": [value: T]
        change: [value: T]
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const filteredProps = useFilteredProps(props, ["flush", "modelValue"])
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/collapse';
</style>

<style>
    .kel-collapse {
        --kel-collapse-content-text-color: var(--ks-text-primary);
        --kel-collapse-header-text-color: var(--ks-text-primary);
        --kel-collapse-header-border-color: var(--ks-border-default);
        --kel-collapse-header-height: auto;
        --kel-collapse-header-font-size: var(--ks-font-size-base);
        --kel-collapse-content-font-size: var(--ks-font-size-base);
        --kel-collapse-border-color: var(--ks-border-default);
        border: none;

        & .kel-collapse-item__header {
            padding: var(--ks-collapse-header-padding, .5rem);
            border: var(--ks-collapse-header-border, none);
            border-radius: var(--ks-collapse-header-radius, 0);
            background-color: var(--ks-collapse-header-bg, var(--kel-collapse-header-bg-color));
        }

        & .kel-collapse-item.is-active .kel-collapse-item__header {
            border: var(--ks-collapse-header-border, none);
        }

        & .kel-collapse-item__content {
            padding: var(--ks-collapse-content-padding, .5rem);
        }

        & .kel-collapse-item__wrap {
            border: none;
        }

        &.is-flush {
            & .kel-collapse-item__header {
                padding: 0;
                min-height: 2rem;
                line-height: 1.2;
            }

            & .kel-collapse-item__content {
                padding: 0;
            }

            & .kel-collapse-item__arrow {
                margin: 0 var(--ks-spacing-2);
            }
        }
    }
</style>
