<template>
    <ElFormItem v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.label" #label>
            <slot name="label" />
        </template>
        <template v-if="$slots.error" #error="p">
            <slot name="error" v-bind="p" />
        </template>
    </ElFormItem>
</template>

<script setup lang="ts">
    import {ElFormItem, type FormItemRule} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        label?: string
        prop?: string | string[]
        rules?: FormItemRule[]
        required?: boolean
        labelWidth?: string | number
        error?: string
        showMessage?: boolean
    }>(), {
        label: undefined,
        prop: undefined,
        rules: undefined,
        labelWidth: undefined,
        error: undefined,
        showMessage: undefined,
    })

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
        label?(): unknown
        error?: (scope: {error: string}) => unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/form-item';
    .kel-form-item {
        .kel-form-item__error {
            &.kel-form-item__error--inline {
                margin-top: 3px;
                width: 100%;
                margin-left: 6px;
            }
        }

        .kel-input-group__append, .kel-input-group__prepend {
            background-color: transparent;
            color: var(--ks-text-primary);
        }
    }
</style>
