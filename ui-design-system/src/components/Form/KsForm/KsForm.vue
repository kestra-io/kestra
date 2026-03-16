<script setup lang="ts">
    import {ref} from "vue"
    import {ElForm, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        model?: object
        rules?: object
        disabled?: boolean
        size?: "large" | "default" | "small"
        labelPosition?: "left" | "right" | "top"
        labelWidth?: string | number
        statusIcon?: boolean
    }>()

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        submit: [evt: Event]
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const formRef = ref<InstanceType<typeof ElForm>>()

    defineExpose({
        validate: (...args: Parameters<NonNullable<InstanceType<typeof ElForm>>["validate"]>) => formRef.value?.validate(...args),
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        resetFields: (...args: any[]) => formRef.value?.resetFields(...args),
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        clearValidate: (...args: any[]) => formRef.value?.clearValidate(...args),
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        validateField: (...args: any[]) => formRef.value?.validateField(...args),
        scrollToField: (prop: string) => formRef.value?.scrollToField(prop),
    })
</script>

<template>
    <el-form ref="formRef" v-bind="({...filteredProps(), ...$attrs} as any)" @submit="emit('submit', $event)">
        <template v-if="$slots.default" #default><slot /></template>
    </el-form>
</template>

<style lang="scss">
    @use 'element-plus/theme-chalk/src/mixins/mixins' as *;

    form.ks-horizontal {
        .kel-form-item {
            @include res(xs) {
                display: block;
            }

            @include res(sm) {
                --label-size: 16.6666666667%;
                &.small {
                    --label-size: calc(16.6666666667% / 2);
                }

                .kel-form-item__label {
                    max-width: var(--label-size);
                    flex: 0 0 var(--label-size);
                    text-align: right;
                }

                .kel-form-item__content {
                    align-items: flex-start;
                    max-width: calc(100% - var(--label-size));
                    flex: 0 0 calc(100% - var(--label-size));
                }
            }
        }

        .submit {
            text-align: right;

            .kel-form-item__content {
                justify-content: end;
                max-width: unset;
                flex: 1;
            }
        }
    }
</style>