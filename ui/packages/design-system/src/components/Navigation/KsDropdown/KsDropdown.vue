<template>
    <ElDropdown
        :persistent="false"
        :popperOptions="POPPER_OPTIONS"
        v-bind="$attrs"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.dropdown" #dropdown>
            <slot name="dropdown" />
        </template>
    </ElDropdown>
</template>

<script setup lang="ts">
    import {ElDropdown} from "element-plus"

    defineOptions({inheritAttrs: false})

    const POPPER_OPTIONS = {
        modifiers: [
            {name: "flip", options: {rootBoundary: "viewport", padding: 8}},
            {name: "preventOverflow", options: {rootBoundary: "viewport", padding: 8}},
        ],
    }

    defineSlots<{
        default?(): unknown
        dropdown?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/dropdown';

    .kel-dropdown__popper {
        --kel-popper-border-radius: var(--ks-radius-base);
        --kel-dropdown-menuItem-hover-fill: var(--ks-bg-hover-elevated);
        --kel-dropdown-menuItem-hover-color: var(--ks-text-primary);

        background: var(--ks-bg-elevated);
        border: 1px solid var(--ks-border-strong);
        box-shadow: 0 8px 24px 0 var(--ks-shadow-elevated);
        font-size: var(--ks-font-size-xs);

        &.separator-m-0 .kel-dropdown-menu__item--divided {
            margin: 0;
        }

        .m-dropdown-menu {
            display: flex;
            flex-direction: column;
            width: 20rem;
            padding: 0;
        }

        .kel-dropdown-menu {
            background: transparent;
            border: 0;
            box-shadow: none;
            padding: var(--ks-spacing-1);

            .kel-dropdown-menu__item + .kel-dropdown-menu__item {
                margin-top: var(--ks-spacing-1);
            }
        }

        .kel-dropdown-menu__item {
            border-radius: var(--ks-radius-xs);
            display: flex;
            gap: var(--ks-spacing-2);

            i {
                margin-right: 0;
            }

            &:not(.is-disabled):hover,
            &:not(.is-disabled):focus {
                background-color: var(--ks-bg-hover-elevated);
                outline: none;
                box-shadow: none;
            }

            > a,
            > button {
                color: inherit;
                text-decoration: none;
                background: transparent;
                border: 0;
                padding: 0;
                font: inherit;
                cursor: pointer;
            }
        }
    }
</style>
