<template>
    <ElFormItem
        :class="{'is-inline-row': inline, 'is-stacked': stacked, 'is-wide-label': wideLabel}"
        v-bind="({...filteredProps(), ...$attrs} as FormItemProps)"
    >
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
    import type {HTMLAttributes} from "vue"
    import {ElFormItem, type FormItemProps} from "element-plus"
    import {usePassedProps} from "../../../utils/filteredProps"
    import type {NativeAttributes} from "../../../utils/nativeAttributes"

    export interface KsFormItemProps extends FormItemProps {
        /** Lay the item out as a single row: label on the left, control pushed to the right. */
        inline?: boolean
        /** Put each child of the content on its own full-width line instead of wrapping them in a row. */
        stacked?: boolean
        /** Stretch the label across the row so a label slot can lay out its own content. */
        wideLabel?: boolean
    }

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<KsFormItemProps & /* @vue-ignore */ Omit<NativeAttributes<HTMLAttributes>, keyof KsFormItemProps>>(), {
        inline: false,
        stacked: false,
        wideLabel: false,
    })

    const filteredProps = usePassedProps(props, ["inline", "stacked", "wideLabel"])

    defineSlots<{
        default?(): unknown
        label?(): unknown
        error?: (scope: {error: string}) => unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/form-item';
</style>

<style>
    :where(.kel-form-item) .kel-form-item__label {
        font-size: var(--ks-form-item-label-font-size, var(--kel-form-label-font-size));
        font-weight: var(--ks-form-item-label-font-weight, inherit);
        color: var(--ks-form-item-label-color, var(--kel-text-color-regular));
    }

    .kel-form-item {
        &:has(.kel-switch:not(.is-disabled)) .kel-form-item__label {
            cursor: pointer;
        }

        & .kel-form-item__error {
            &.kel-form-item__error--inline {
                margin-top: 3px;
                width: 100%;
                margin-left: 6px;
            }
        }

        & .kel-input-group__append, & .kel-input-group__prepend {
            background-color: transparent;
            color: var(--ks-text-primary);
        }

        &.is-inline-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: var(--ks-spacing-4);

            & .kel-form-item__label {
                width: auto;
                margin: 0;
                padding: 0;
            }

            & .kel-form-item__content {
                flex: 0 0 var(--ks-form-item-content-width, auto);
                max-width: var(--ks-form-item-content-width, none);
            }
        }

        &.is-stacked .kel-form-item__content {
            display: block;
        }

        &.is-wide-label > .kel-form-item__label {
            width: 100%;
            display: flex;
            align-items: center;
            padding: 0;
        }
    }
</style>
