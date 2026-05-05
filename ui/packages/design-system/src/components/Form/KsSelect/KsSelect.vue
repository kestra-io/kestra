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
         --kel-disabled-text-color: var(--ks-content-inactive);

         &.fit-text .kel-select__input {
             width: fit-content !important;
         }

         &:not(.kel-select--small), &:not(.kel-select--large) {
             font-size: var(--ks-font-size-base);
         }

        .kel-select__caret {
            color: var(--kel-input-icon-color, var(--kel-text-color-placeholder));
        }

         .kel-select__wrapper {
             background-color: var(--ks-background-input);

             &.is-disabled {
                 html.dark & {
                     background-color: var(--ks-border-primary);
                 }

                 .kel-select__suffix {
                     .kel-select__caret {
                         color: var(--ks-content-inactive);
                     }
                 }
             }
         }
     }

    .kel-select__popper {
        // icon for selection of items in multiple choices
        .kel-select-dropdown.is-multiple .kel-select-dropdown__item.is-selected::after{
            background-color: var(--ks-select-active-icon);
            -webkit-mask: no-repeat url(data:image/svg+xml,%3Csvg%20width%3D%2214%22%20height%3D%2211%22%20viewBox%3D%220%200%2014%2011%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%3Cpath%20d%3D%22M5.00035%2010.6134L0.860352%206.47342L2.74702%204.58675L5.00035%206.84675L11.587%200.253418L13.4737%202.14008L5.00035%2010.6134Z%22%20fill%3D%22%23BBBBFF%22%2F%3E%3C%2Fsvg%3E);
            mask: no-repeat url(data:image/svg+xml,%3Csvg%20width%3D%2214%22%20height%3D%2211%22%20viewBox%3D%220%200%2014%2011%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%3Cpath%20d%3D%22M5.00035%2010.6134L0.860352%206.47342L2.74702%204.58675L5.00035%206.84675L11.587%200.253418L13.4737%202.14008L5.00035%2010.6134Z%22%20fill%3D%22%23BBBBFF%22%2F%3E%3C%2Fsvg%3E);
            -webkit-mask-size: 100% 100%;
            mask-size: 100% 100%;
            right: 1rem;
        }

        .kel-select-dropdown__item {
            border-radius: var(--kel-border-radius-base);
            margin: 0 0.6rem 1px;

            &.is-selected {
                background-color: var(--ks-select-active);
                color: var(--ks-content-primary);
            }

            &.is-hovering {
                background-color: var(--ks-select-hover);
            }
        }
    }

    .kel-icon.kel-select__caret.kel-select__icon {
        font-size: var(--ks-font-size-md);
    }

</style>
