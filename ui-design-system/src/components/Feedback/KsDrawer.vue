<script setup lang="ts">
    import {ElDrawer, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        modelValue?: boolean
        destroyOnClose?: boolean
        lockScroll?: boolean
        closeOnClickModal?: boolean
        closeOnPressEscape?: boolean
        showClose?: boolean
        size?: string
        appendToBody?: boolean
    }>(), {
        lockScroll: undefined,
        closeOnClickModal: undefined,
        closeOnPressEscape: undefined,
        showClose: undefined,
    })

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        "update:modelValue": [value: boolean]
        "before-close": [done: () => void]
    }>()

    defineSlots<{
        default?(): unknown
        header?(): unknown
        footer?(): unknown
    }>()
</script>

<template>
    <el-drawer
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @update:model-value="emit('update:modelValue', $event)"
        @before-close="emit('before-close', $event)"
    >
        <template v-if="$slots.default" #default><slot /></template>
        <template v-if="$slots.header" #header><slot name="header" /></template>
        <template v-if="$slots.footer" #footer><slot name="footer" /></template>
    </el-drawer>
</template>

<style lang="scss">
    @use 'element-plus/theme-chalk/src/mixins/mixins' as *;

    .kel-drawer {
        &.ltr,
        &.rtl {
            width: 70%;
            @include res(xs) {
                width: 95%;
            }

            @include res(md) {
                width: 70%;
            }

            @include res(lg) {
                width: 35%;
                min-width: 800px;
            }

            &.sm {
                min-width: auto;

                @include res(xs) {
                    width: 95%;
                }

                @include res(sm) {
                    width: 50%;
                }

                @include res(lg) {
                    width: 30%;
                }
            }
        }

        &.ttb,
        &.btt {
            height: 70%;
            @include res(xs) {
                height: 95%;
            }

            @include res(lg) {
                height: 50%;
            }

            @include res(lg) {
                height: 35%;
                min-height: 600px;
            }

            &.sm {
                height: 30%;
                min-width: auto;

            }
        }

        &.full-screen {
            width: 99% !important;
        }

        .kel-drawer__header {
            padding: 1rem;
            margin-bottom: 0;
            background-color: var(--ks-gray-300);
            border-bottom: 1px solid var(--ks-border-primary);
            color: var(--ks-content-primary);
            font-weight: bold;
            font-size: var(--font-size-lg);

            html.dark & {
                background-color: var(--ks-gray-100);
            }
        }
    }

</style>