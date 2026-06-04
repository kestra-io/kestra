<template>
    <ElDialog
        v-model="model"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @close="emit('close')"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.header" #header>
            <slot name="header" />
        </template>
        <template v-if="$slots.footer" #footer>
            <slot name="footer" />
        </template>
    </ElDialog>
</template>

<script setup lang="ts">
    import {ElDialog} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<boolean>()

    const props = withDefaults(defineProps<{
        title?: string
        destroyOnClose?: boolean
        lockScroll?: boolean
        closeOnClickModal?: boolean
        closeOnPressEscape?: boolean
        showClose?: boolean
        appendToBody?: boolean
        width?: string | number
        top?: string
        beforeClose?: (done: () => void) => void    
    }>(), {
        title: undefined,
        lockScroll: undefined,
        closeOnClickModal: undefined,
        closeOnPressEscape: undefined,
        showClose: undefined,
        width: undefined,
        top: undefined,
        beforeClose: undefined,
    })

    const emit = defineEmits<{
        close: []
    }>()

    defineSlots<{
        default?(): unknown
        header?(): unknown
        footer?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/dialog';

    .kel-dialog {
        --kel-dialog-bg-color: var(--ks-bg-elevated);
        border: 1px solid var(--ks-border-default);

        .kel-dialog__header {
            font-size: var(--ks-font-size-base);
            font-weight: bold;

            .kel-dialog__headerbtn {
                height: 62px;
                width: 62px;
            }

            .kel-dialog__close {
                color: var(--ks-icon-default);

                &:hover {
                    color: var(--ks-icon-hover) !important;
                }
            }
        }

        .kel-dialog__body {
            padding-bottom: var(--kel-dialog-padding-primary);
        }

        .kel-dialog__footer {
            border-top: 1px solid var(--ks-border-default);
            margin-left: calc(var(--kel-dialog-padding-primary) * -1);
            margin-bottom: calc(var(--kel-dialog-padding-primary) * -1);
            padding-bottom: var(--kel-dialog-padding-primary);
            padding-right: var(--kel-dialog-padding-primary);
            width: calc(100% + var(--kel-dialog-padding-primary) * 2);
            background-color: var(--ks-bg-base);
            border-radius: 0 0  var(--kel-dialog-border-radius) var(--kel-dialog-border-radius);
        }
    }
</style>
