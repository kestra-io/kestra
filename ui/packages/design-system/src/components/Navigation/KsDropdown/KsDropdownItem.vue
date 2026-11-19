<template>
    <ElDropdownItem
        v-bind="({...filteredProps(), ...$attrs} as ExtractPublicPropTypes<typeof dropdownItemProps>)"
        :class="{'is-danger': danger}"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElDropdownItem>
</template>

<script setup lang="ts">
    import type {NativeAttributes} from "../../../utils/nativeAttributes"
    import {ElDropdownItem, dropdownItemProps} from "element-plus"
    import type {Component, ExtractPublicPropTypes, HTMLAttributes} from "vue"
    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    export interface KsDropdownItemProps {
        command?: string | number | object
        disabled?: boolean
        divided?: boolean
        icon?: string | Component
        danger?: boolean
    }

    const props = defineProps<KsDropdownItemProps & /* @vue-ignore */ Omit<NativeAttributes<ExtractPublicPropTypes<typeof dropdownItemProps> & HTMLAttributes>, keyof KsDropdownItemProps>>()

    const filteredProps = useFilteredProps(props, ["danger"])

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/dropdown-item';
</style>

<style>
</style>
