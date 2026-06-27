<template>
    <ElSelect v-model="model" v-bind="({...filteredProps(), ...$attrs} as any)" :suffixIcon="resolvedSuffixIcon" :class="{'kel-select--fit': fit}" @change="emit('change', $event)">
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
        <template v-if="$slots.tag" #tag="tagScope">
            <slot name="tag" v-bind="tagScope" />
        </template>
    </ElSelect>
</template>

<script setup lang="ts">
    import {type Component, computed, h, markRaw} from "vue"
    import {ElSelect} from "element-plus"
    import Loading from "vue-material-design-icons/Loading.vue"
    import KsIcon from "../../Basic/KsIcon.vue"
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
        loading?: boolean
        fit?: boolean
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
        loading: undefined,
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

    const filteredProps = useFilteredProps(props, ["fit", "suffixIcon", "loading"])

    // `loading` is intentionally NOT forwarded to ElSelect: ElSelect v-shows its option
    // list on `!loading`, so forwarding would hide still-valid options while they
    // recompute. We only surface a spinning suffix icon, leaving the dropdown usable.
    const LoadingSpinner = markRaw({
        render: () => h(KsIcon, {class: "is-loading"}, () => h(Loading)),
    }) as Component

    const resolvedSuffixIcon = computed<Component | string | undefined>(
        () => props.loading ? LoadingSpinner : props.suffixIcon,
    )
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/select';
    @use 'element-plus/theme-chalk/src/select-dropdown';

    @keyframes kel-select-loading-rotate {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
    }

    .kel-select {
        --kel-disabled-text-color: var(--ks-text-inactive);

        &.fit-text .kel-select__input {
            width: fit-content !important;
        }

        &.kel-select--fit {
            width: fit-content;

            .kel-select__wrapper {
                width: fit-content;
            }

            .kel-select__placeholder {
                position: static;
                transform: none;
                top: auto;
            }

            .kel-select__input-wrapper {
                position: absolute;
            }

            &:focus-within:has(input:not([readonly])) {
                .kel-select__placeholder {
                    position: absolute;
                }

                .kel-select__input-wrapper {
                    position: relative;
                }

                .kel-select__input {
                    width: fit-content;
                    min-width: 120px;
                }
            }
        }


        &:not(.kel-select--small),
        &:not(.kel-select--large) {
            font-size: var(--ks-font-size-xs);
        }

        .kel-select__placeholder.is-transparent {
            color: var(--ks-text-inactive);
            font-size: var(--ks-font-size-xs);
            font-weight: var(--ks-font-weight-regular);
        }

        .kel-select__wrapper {
            .kel-tag.kel-tag--default.kel-tag--light {
                --kel-tag-text-color: var(--ks-text-primary);
                --kel-tag-bg-color: var(--ks-bg-tag);
            }

            &.is-focused {
                box-shadow: 0 0 0 2px var(--ks-border-focus) inset;
            }

            &.is-hovering:not(.is-focused) {
                box-shadow: 0 0 0 1px var(--ks-border-focus) inset;
            }
        }

        .kel-select__caret {
            color: var(--kel-input-icon-color, var(--kel-text-color-placeholder));
        }

        .kel-icon.is-loading svg {
            animation: kel-select-loading-rotate 2s linear infinite;
        }

        .kel-select__wrapper {
            background-color: var(--ks-bg-input);
            min-height: 30px;
            padding: 4px 8px 4px 10px;
            font-size: var(--ks-font-size-xs);
            box-shadow: inset 0 0 0 1px var(--ks-border-strong), 0 1px 2px var(--ks-shadow-element);

            &:hover {
                background-color: var(--ks-bg-hover);
            }

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
        --kel-popper-border-radius: var(--ks-radius-base);

        background: var(--ks-bg-elevated);
        border: 1px solid var(--ks-border-strong);
        box-shadow: 0px 8px 24px 0px var(--ks-shadow-elevated);

        .kel-select-dropdown {
            background: transparent;
            border: none;
            box-shadow: none;
        }

        .kel-select-dropdown__list {
            padding: var(--ks-spacing-1);

            .kel-select-dropdown__item + .kel-select-dropdown__item {
                margin-top: var(--ks-spacing-1);
            }
        }

        .kel-select-dropdown__empty {
            padding: var(--ks-spacing-3) var(--ks-spacing-4);
        }

        .kel-select-dropdown__item {
            border-radius: var(--ks-radius-xs);
            position: relative;
            font-size: var(--ks-font-size-xs);
            height: auto;

            &.is-selected {
                background-color: transparent;
                color: var(--ks-text-primary);
                font-weight: normal;
            }

            &.is-hovering {
                background-color: var(--ks-bg-hover-elevated);
            }
        }

        .kel-select-dropdown .kel-select-dropdown__item.is-selected::after {
            content: "";
            position: absolute;
            right: 12px;
            top: 50%;
            transform: translateY(-50%);
            width: 14px;
            height: 14px;
            background-color: var(--ks-icon-active);
            mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M9,20.42L2.79,14.21L5.62,11.38L9,14.77L18.88,4.88L21.71,7.71L9,20.42Z'/%3E%3C/svg%3E") no-repeat center / contain;
            -webkit-mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M9,20.42L2.79,14.21L5.62,11.38L9,14.77L18.88,4.88L21.71,7.71L9,20.42Z'/%3E%3C/svg%3E") no-repeat center / contain;
        }
    }

    .kel-icon.kel-select__caret.kel-select__icon {
        font-size: var(--ks-font-size-md);
    }
</style>