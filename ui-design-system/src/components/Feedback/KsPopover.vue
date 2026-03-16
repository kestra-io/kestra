<script setup lang="ts">
    import {ElPopover, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        visible?: boolean
        placement?: string
        trigger?: "click" | "hover" | "focus" | "contextmenu"
        width?: number | string
        popperClass?: string
        showArrow?: boolean
        disabled?: boolean
    }>()

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        "update:visible": [value: boolean]
        hide: []
    }>()

    defineSlots<{
        default?(): unknown
        reference?(): unknown
    }>()
</script>

<template>
    <el-popover
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @update:visible="emit('update:visible', $event)"
        @hide="emit('hide')"
    >
        <template v-if="$slots.default" #default><slot /></template>
        <template v-if="$slots.reference" #reference><slot name="reference" /></template>
    </el-popover>
</template>

<style lang="scss">
    .kel-popper {
        border-radius: var(--kel-border-radius-round);

        &.hide-arrow .kel-popper__arrow {
            display: none;
        }

        &.kel-picker__popper {
            border-radius: var(--kel-popper-border-radius);
        }

        &.is-light {
            border: 1px solid var(--ks-border-primary);
            background: var(--ks-dropdown-background);
            box-shadow: rgba(0, 0, 0, 0.09) 0 3px 12px;

            .kel-popper__arrow::before {
                border: 1px solid var(--ks-border-primary);
                background-color: var(--ks-dropdown-background);
            }
        }

        &.is-dark {
            color: var(--ks-gray-100);

            background: var(--ks-gray-900);
            border: 1px solid var(--ks-border-primary);

            .kel-popper__arrow::before {
                border: 1px solid var(--ks-border-primary);
                background-color: var(--ks-gray-900);
            }

            html.dark & {
                color: var(--ks-gray-900);
                background: var(--ks-gray-100);

                .kel-popper__arrow::before {
                    background-color: var(--ks-gray-100);
                }
            }
        }

        .kel-popover__title {
            color: var(--ks-content-primary);
        }
    }
</style>