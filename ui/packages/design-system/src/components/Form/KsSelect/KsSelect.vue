<template>
    <ElSelect
        v-model="model"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.prefix" #prefix>
            <slot name="prefix" />
        </template>
        <template v-if="$slots.header" #header>
            <slot name="header" />
        </template>
        <template v-if="$slots.footer" #footer>
            <slot name="footer" />
        </template>
        <template v-if="$slots.label" #label="p">
            <slot name="label" v-bind="p" />
        </template>
        <template v-if="$slots.tag" #tag>
            <slot name="tag" />
        </template>
    </ElSelect>
</template>

<script setup lang="ts">
    import type {Component} from "vue"
    import {ElSelect} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<any>()

    const props = withDefaults(defineProps<{
        placeholder?: string
        disabled?: boolean
        size?: "small" | "default" | "large"
        filterable?: boolean
        clearable?: boolean
        allowCreate?: boolean
        remote?: boolean
        remoteMethod?: (query: string) => void
        remoteShowSuffix?: boolean
        multiple?: boolean
        collapseTags?: boolean
        required?: boolean
        valueKey?: string
        placement?: string
        popperOffset?: number
        popperClass?: string
        showArrow?: boolean
        suffixIcon?: Component | string
    }>(), {
        placeholder: undefined,
        size: undefined,
        clearable: undefined,
        remoteMethod: undefined,
        valueKey: undefined,
        placement: undefined,
        popperOffset: undefined,
        popperClass: undefined,
        suffixIcon: undefined,
    })

    const emit = defineEmits<{
        change: [value: any]
    }>()

    defineSlots<{
        default?(): unknown
        prefix?(): unknown
        header?(): unknown
        footer?(): unknown
        label?(props: { value: any; label: string }): any
        tag?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/select';
    @use 'element-plus/theme-chalk/src/select-dropdown';

    .kel-select {
         --kel-disabled-text-color: var(--ks-text-inactive);

         &.fit-text .kel-select__input {
             width: fit-content !important;
         }

         &:not(.kel-select--small), &:not(.kel-select--large) {
             font-size: var(--ks-font-size-base);
         }

        .kel-select__wrapper {
            .kel-tag.kel-tag--default.kel-tag--light {
                --kel-tag-text-color: var(--ks-text-primary);
                --kel-tag-bg-color: var(--ks-bg-tag);
            }

            &.is-hovering:not(.is-focused) {
                box-shadow: 0 0 0 1px var(--ks-border-focus) inset;
            }
        }

        .kel-select__caret {
            color: var(--kel-input-icon-color, var(--kel-text-color-placeholder));
        }

         .kel-select__wrapper {
             background-color: var(--ks-bg-input);

             &.is-disabled {
                 html.dark & {
                     background-color: var(--ks-border-default);
                 }

                 .kel-select__suffix {
                     .kel-select__caret {
                         color: var(--ks-text-inactive);
                     }
                 }
             }
         }
     }

    .kel-select__popper {
        // icon for selection of items in multiple choices
        .kel-select-dropdown.is-multiple .kel-select-dropdown__item.is-selected::after{
            display: none;
        }

        .kel-select-dropdown__item {
            border-radius: var(--kel-border-radius-base);
            margin: 0 0.6rem 1px;

            &.is-selected {
                background-color: var(--ks-bg-hover-elevated);
                color: var(--ks-text-primary);
                font-weight: normal;
            }

            &.is-hovering {
                background-color: var(--ks-bg-elevated);
            }
        }
    }

    .kel-icon.kel-select__caret.kel-select__icon {
        font-size: var(--ks-font-size-md);
    }

</style>
