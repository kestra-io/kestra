<template>
    <ElDropdown
        :persistent="false"
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

    defineSlots<{
        default?(): unknown
        dropdown?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/dropdown';

    .kel-dropdown__popper {
        font-size: var(--ks-font-size-sm);
        --kel-dropdown-menuItem-hover-fill: var(--ks-dropdown-background-hover);
        --kel-dropdown-menuItem-hover-color: var(--ks-content-primary);

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
            padding: 0;
            background-color: var(--ks-dropdown-background);
            border-radius: 0.5rem;
        }

        // no longer require focus to get hover effect on dropdowns
        .kel-dropdown-menu__item {
            &:first-child {
                border-top-left-radius: calc(var(--kel-border-radius-base) * 2);
                border-top-right-radius: calc(var(--kel-border-radius-base) * 2);
            }
            &:last-child {
                border-bottom-left-radius: calc(var(--kel-border-radius-base) * 2);
                border-bottom-right-radius: calc(var(--kel-border-radius-base) * 2);
            }
            &:is(li) {
                display: flex;
                gap: .5rem;

                i {
                    margin-right: 0;
                }
            }

            &:not(.is-disabled):hover {
                background-color: var(--ks-dropdown-background-hover);
            }
        }
    }
</style>
