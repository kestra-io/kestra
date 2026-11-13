<template>
    <ElCheckbox
        v-model="model"
        v-bind="({...filteredProps(), ...$attrs} as CheckboxProps)"
        @change="emit('change', $event as T)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElCheckbox>
</template>

<script setup lang="ts" generic="T extends CheckboxValueType">
    import type {NativeAttributes} from "../../../utils/nativeAttributes"
    import type {HTMLAttributes} from "vue"
    import {ElCheckbox, type CheckboxProps, type CheckboxValueType} from "element-plus"

    import {usePassedProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<T>()

    type KsCheckboxProps = Omit<CheckboxProps, "modelValue">

    const props = defineProps<KsCheckboxProps & /* @vue-ignore */ Omit<NativeAttributes<HTMLAttributes>, keyof KsCheckboxProps>>()

    const emit = defineEmits<{
        change: [value: T]
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const filteredProps = usePassedProps(props)
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/checkbox';
</style>

<style>
    .kel-checkbox {
        --kel-checkbox-text-color: var(--ks-text-primary);
        --kel-checkbox-checked-text-color: var(--ks-text-primary);
        --kel-checkbox-font-size: var(--ks-font-size-base);
        --kel-checkbox-font-weight: var(--ks-font-weight-regular);
        --kel-checkbox-input-width: 1rem;
        --kel-checkbox-input-height: 1rem;
        --kel-checkbox-border-radius: var(--ks-radius-xs);
        --kel-checkbox-input-border: 1px solid var(--ks-border-strong);
        --kel-checkbox-bg-color: transparent;
    }
</style>
