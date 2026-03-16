<script setup lang="ts">
    import {ElDialog, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        modelValue?: boolean
        title?: string
        destroyOnClose?: boolean
        lockScroll?: boolean
        closeOnClickModal?: boolean
        closeOnPressEscape?: boolean
        showClose?: boolean
        appendToBody?: boolean
        width?: string | number
        top?: string
    }>(), {
        lockScroll: undefined,
        closeOnClickModal: undefined,
        closeOnPressEscape: undefined,
        showClose: undefined,
    })

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        "update:modelValue": [value: boolean]
        close: []
    }>()

    defineSlots<{
        default?(): unknown
        header?(): unknown
        footer?(): unknown
    }>()
</script>

<template>
    <el-dialog
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @update:model-value="emit('update:modelValue', $event)"
        @close="emit('close')"
    >
        <template v-if="$slots.default" #default><slot /></template>
        <template v-if="$slots.header" #header><slot name="header" /></template>
        <template v-if="$slots.footer" #footer><slot name="footer" /></template>
    </el-dialog>
</template>

<style lang="scss">
    .kel-dialog {
        --kel-dialog-border-radius: var(--kel-border-radius-round);
        background-color: var(--ks-background-card);

        &.custom-dialog {
            background-color: var(--ks-background-panel);

            .kel-dialog__header {
                background: var(--ks-background-panel);
                margin-bottom: 0;

                .kel-dialog__headerbtn {
                    svg {
                        color: var(--ks-content-secondary);
                    }
                }
            }
            .kel-dialog__title {
                font-size: var(--kel-font-size-base);
                font-weight: 700;
            }
        }

        .kel-dialog__header {
            padding: 1rem;
            margin: -1rem -1rem 1rem;
            border-top-right-radius: var(--kel-border-radius-round);
            border-top-left-radius: var(--kel-border-radius-round);
            background: var(--ks-dialog-header);
            font-size: var(--font-size-lg);

            .kel-dialog__headerbtn {
                height: 62px;
                width: 62px;

                .kel-dialog__close {

                    color: var(--ks-dialog-headerbtn) !important;


                    &:hover {
                        color: var(--ks-dialog-headerbtn-hover) !important;
                    }
                }
            }
        }

        .kel-dialog__title {
            color: var(--ks-content-primary);
        }

        .bottom-buttons {
            margin-top: 36px;
            display: flex;

            > * {
                flex: 1;

                * {
                    margin: 0;
                }
            }

            .left-align {
                &, & div {
                    gap: 1rem;
                    display: flex;
                    flex-direction: row
                }
            }

            .right-align {
                &, & div {
                    gap: 1rem;
                    display: flex;
                    flex-direction: row-reverse;
                }
            }
        }
    }

</style>