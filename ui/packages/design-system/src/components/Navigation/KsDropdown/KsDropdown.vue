<template>
    <div
        v-if="splitButton"
        class="kel-dropdown"
        :class="{'is-disabled': disabled}"
        v-bind="rootAttrs"
    >
        <ElButtonGroup>
            <ElButton
                v-bind="buttonProps"
                :size="size"
                :type="type"
                :disabled="disabled || mainButtonDisabled"
                @click="emit('click', $event)"
            >
                <slot />
            </ElButton>
            <ElButton
                ref="caret"
                v-bind="{...buttonProps, id: triggerId, 'aria-label': $t('ks_dropdown.toggle')}"
                class="kel-dropdown__caret-button"
                :size="size"
                :type="type"
                :disabled="disabled"
            >
                <ElIcon class="kel-dropdown__icon"><ChevronDown /></ElIcon>
            </ElButton>
        </ElButtonGroup>
        <ElDropdown
            ref="dropdown"
            :persistent="false"
            :popperOptions="POPPER_OPTIONS"
            v-bind="menuAttrs"
            :id="triggerId"
            :size="size"
            :disabled="disabled"
            :virtualRef="caret?.ref"
            virtualTriggering
        >
            <template v-if="$slots.dropdown" #dropdown>
                <slot name="dropdown" />
            </template>
        </ElDropdown>
    </div>
    <ElDropdown
        v-else
        ref="dropdown"
        :persistent="false"
        :popperOptions="POPPER_OPTIONS"
        v-bind="$attrs"
        :size="size"
        :disabled="disabled"
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
    import {camelize, computed, ref, useAttrs, useId, type ExtractPublicPropTypes, type HTMLAttributes} from "vue"
    import {
        ElButton, ElButtonGroup, ElDropdown, ElIcon, dropdownProps,
        type ButtonInstance, type ButtonProps, type ButtonType, type DropdownInstance,
    } from "element-plus"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import type {NativeAttributes} from "../../../utils/nativeAttributes"

    defineOptions({inheritAttrs: false})

    interface KsDropdownProps {
        splitButton?: boolean
        disabled?: boolean
        /** Disables the label half of a split button only; the caret and its menu stay usable. */
        mainButtonDisabled?: boolean
        type?: ButtonType
        size?: "" | "default" | "small" | "large"
        buttonProps?: Partial<ButtonProps>
    }

    defineProps<KsDropdownProps & /* @vue-ignore */ (NativeAttributes<Omit<ExtractPublicPropTypes<typeof dropdownProps>, keyof KsDropdownProps> & HTMLAttributes> & {
        onCommand?: (command: never) => void
        onVisibleChange?: (visible: boolean) => void
    })>()

    const emit = defineEmits<{
        click: [event: MouseEvent]
    }>()

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

    // Element Plus binds one `disabled` to both halves of its split button, so the pair is rendered here
    // with the same markup and the caret handed to ElDropdown as a virtual trigger.
    const triggerId = useId()
    const caret = ref<ButtonInstance>()
    const attrs = useAttrs()
    const isMenuAttr = (name: string) => camelize(name) in dropdownProps || name === "onCommand" || name === "onVisibleChange"
    const rootAttrs = computed(() => Object.fromEntries(Object.entries(attrs).filter(([name]) => !isMenuAttr(name))))
    const menuAttrs = computed(() => Object.fromEntries(Object.entries(attrs).filter(([name]) => isMenuAttr(name))))

    // Forward the underlying ElDropdown open/close controls so callers can dismiss the menu
    // programmatically — needed when the dropdown holds custom content (not KsDropdownItems,
    // which auto-close on click).
    const dropdown = ref<DropdownInstance>()
    defineExpose({
        handleOpen: () => dropdown.value?.handleOpen(),
        handleClose: () => dropdown.value?.handleClose(),
    })
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/dropdown';
</style>

<style>
    .kel-dropdown {
        /* Element Plus draws the split-button divider as the border colour at half opacity, */
        /* which vanishes against every Kestra button fill; use the strong border token instead */

        & .kel-dropdown__caret-button.kel-button::before {
            background: var(--ks-border-strong);
            opacity: 1;
        }

        /* a primary button is filled, so use the lighter button-group divide colour instead */

        & .kel-dropdown__caret-button.kel-button--primary:not(.is-disabled)::before {
            background: var(--kel-button-divide-border-color);
        }
    }

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

        & .m-dropdown-menu {
            display: flex;
            flex-direction: column;
            width: 20rem;
            padding: 0;
        }

        & .kel-dropdown-menu {
            background: transparent;
            border: 0;
            box-shadow: none;
            padding: var(--ks-spacing-1);

            & .kel-dropdown-menu__item + .kel-dropdown-menu__item {
                margin-top: var(--ks-spacing-1);
            }
        }

        & .kel-dropdown-menu__item {
            border-radius: var(--ks-radius-xs);
            display: flex;
            gap: var(--ks-spacing-2);

            & i {
                margin-right: 0;
            }

            &:not(.is-disabled) i {
                color: var(--ks-icon-muted);
            }

            &:not(.is-disabled):hover,
            &:not(.is-disabled):focus {
                background-color: var(--ks-bg-hover-elevated);
                outline: none;
                box-shadow: none;
            }

            &.is-danger:not(.is-disabled):hover,
            &.is-danger:not(.is-disabled):focus {
                background-color: var(--ks-dropdown-danger-bg, var(--ks-bg-error));
                color: var(--ks-dropdown-danger-color, var(--ks-text-error));

                & i {
                    color: var(--ks-icon-error);
                }
            }

            & > a,
            & > button {
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
