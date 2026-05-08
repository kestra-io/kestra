<template>
    <ElButton
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @click="emit('click', $event)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.loading" #loading>
            <slot name="loading" />
        </template>
        <template v-if="$slots.icon" #icon>
            <slot name="icon" />
        </template>
    </ElButton>
</template>

<script setup lang="ts">
    import type {Component} from "vue"

    import {ElButton} from "element-plus"

    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        type?: "default" | "primary" | "success" | "warning" | "info" | "danger" | "text" | ""
        size?: "small" | "default" | "large" | ""
        disabled?: boolean
        icon?: string | Component
        nativeType?: "button" | "submit" | "reset"
        loading?: boolean
        plain?: boolean
        text?: boolean
        link?: boolean
        bg?: boolean
        autofocus?: boolean
        round?: boolean
        circle?: boolean
        color?: string
        tag?: string | Component
    }>()

    const emit = defineEmits<{
        click: [evt: MouseEvent]
    }>()

    defineSlots<{
        default?(): unknown
        loading?(): unknown
        icon?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/button';
    @use '../../../assets/styles/color-palette' as palette;

    //.kel-button {
    //    &:not(.kel-button--primary):not(.kel-button--success):not(.kel-button--warning):not(.kel-button--danger):not(.kel-button--error):not(.kel-button--info):not(.kel-button--playground), &--default {
    //        --kel-button-hover-text-color: var(--ks-text-primary);
    //        --kel-button-hover-border-color: var(--ks-border-default);
    //        --kel-button-bg-color: var(--ks-btn-secondary-bg-default);
    //        --kel-button-hover-bg-color: var(--ks-btn-secondary-bg-hover);
    //        --kel-button-active-bg-color: var(--ks-btn-secondary-bg-active);
    //    }
    //
    //    &.kel-button--primary {
    //        --kel-button-text-color: var(--ks-btn-primary-text);
    //        --kel-button-hover-text-color: var(--ks-btn-primary-text);
    //        --kel-button-bg-color: var(--ks-btn-primary-bg-default);
    //        --kel-button-border-color: var(--ks-btn-primary-bg-default);
    //        --kel-button-hover-bg-color: var(--ks-btn-primary-bg-hover);
    //        --kel-button-active-bg-color: var(--ks-btn-primary-bg-active);
    //        --kel-button-disabled-text-color: var(--ks-text-inactive);
    //        --kel-button-disabled-bg-color: var(--ks-btn-primary-bg-inactive);
    //        --kel-button-disabled-border-color: var(--ks-btn-primary-bg-inactive);
    //    }
    //
    //    &.kel-button--playground {
    //        #{--kel-button-disabled-text-color}: #{palette.$base-blue-50};
    //        #{--kel-button-text-color}: var(--ks-btn-primary-text);
    //        #{--kel-button-hover-text-color}: var(--ks-btn-primary-text);
    //        #{--kel-button-bg-color}: var(--ks-btn-primary-bg-default);
    //        #{--kel-button-hover-bg-color}: #{palette.$base-blue-400};
    //        #{--kel-button-active-bg-color}: #{palette.$base-blue-600};
    //        #{--kel-button-active-border-color}: #{palette.$base-blue-700};
    //        #{--kel-button-outline-color}: #{palette.$base-blue-700};
    //    }
    //
    //    .kel-input-group--append & [class*=kel-icon] + span {
    //        position: relative;
    //        top: -3px;
    //    }
    //
    //    [class*=kel-icon] + span:empty {
    //        margin-left: 0;
    //    }
    //
    //    &.kel-button--large {
    //        font-size: var(--ks-font-size-base);
    //        line-height: var(--ks-font-size-base);
    //    }
    //
    //    &.wh-15 {
    //        padding: 0;
    //        border: 0;
    //        width: 1.5rem;
    //        height: 1.5rem;
    //
    //        * {
    //            width: 1.5rem;
    //            height: 1.5rem;
    //        }
    //    }
    //}
</style>
