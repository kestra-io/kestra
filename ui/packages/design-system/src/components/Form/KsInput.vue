<template>
    <ElInput
        v-model="model"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        :class="reserveClearSpace ? 'ks-input--reserve-clear' : undefined"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.prepend" #prepend>
            <slot name="prepend" />
        </template>
        <template v-if="$slots.suffix" #suffix>
            <slot name="suffix" />
        </template>
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElInput>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {ElInput} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<string | number>()

    const props = defineProps<{
        type?: string
        placeholder?: string
        disabled?: boolean
        showPassword?: boolean
        suffixIcon?: any
        clearable?: boolean
        size?: "large" | "default" | "small"
        name?: string
        id?: string
        required?: boolean
        rows?: number
    }>()

    const emit = defineEmits<{
        change: [value: string | number]
    }>()

    const slots = defineSlots<{
        prepend?(): unknown
        suffix?(): unknown
        default?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)

    const reserveClearSpace = computed(() =>
        Boolean(props.clearable) && !props.showPassword && !props.suffixIcon && !slots.suffix,
    )
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/input';

    .kel-textarea, .kel-input {
        --kel-input-border-color: var(--ks-border-default);
        --kel-input-hover-border-color: var(--ks-border-strong);
        --kel-input-bg-color: var(--ks-bg-input);
    }

    .kel-input {
        width: 100%;
        &.kel-input--small {
            .kel-input__wrapper {
                border-radius: var(--ks-radius-sm);
            }
        }

        .kel-input-group__append, .kel-input-group__prepend {
            color: var(--ks-text-dim);
        }

        &.ks-input--reserve-clear {
            .kel-input__inner {
                padding-inline-end: var(--ks-spacing-5);
            }

            .kel-input__suffix {
                position: absolute;
                inset-block: 0;
                inset-inline-end: var(--ks-spacing-3);
            }
        }
    }
</style>
