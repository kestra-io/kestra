<template>
    <ElInput
        ref="elInputRef"
        v-bind="({...filteredProps(), ...$attrs} as InputProps)"
        :modelValue="modelValue"
        :class="{'ks-input--reserve-clear': reserveClearSpace, 'ks-input--borderless': borderless}"
        @update:modelValue="emit('update:modelValue', $event)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.prepend" #prepend>
            <slot name="prepend" />
        </template>
        <template v-if="$slots.prefix" #prefix>
            <slot name="prefix" />
        </template>
        <template v-if="$slots.suffix" #suffix>
            <slot name="suffix" />
        </template>
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElInput>
</template>

<script lang="ts">
    import type {InputProps} from "element-plus"

    export interface KsInputProps extends Omit<InputProps, "modelValue"> {
        modelValue?: string | number
        required?: boolean
        /** Drops the border, background and side padding, for a search field that sits flush in a list row. */
        borderless?: boolean
    }
</script>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {ElInput} from "element-plus"
    import {usePassedProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const elInputRef = ref<InstanceType<typeof ElInput>>()

    defineExpose({
        focus: () => elInputRef.value?.focus(),
        blur: () => elInputRef.value?.blur(),
        select: () => elInputRef.value?.select(),
    })

    const props = defineProps<KsInputProps>()

    // ElInput emits strings only, whatever type the bound value has.
    const emit = defineEmits<{
        "update:modelValue": [value: string]
        change: [value: string]
    }>()

    const slots = defineSlots<{
        prepend?(): unknown
        prefix?(): unknown
        suffix?(): unknown
        default?(): unknown
    }>()

    const filteredProps = usePassedProps(props, ["borderless", "modelValue"])

    const reserveClearSpace = computed(() =>
        Boolean(props.clearable) && !props.showPassword && !props.suffixIcon && !slots.suffix,
    )
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/input';
</style>

<style>
    .kel-textarea, .kel-input {
        --kel-input-border-color: var(--ks-border-strong);
        --kel-input-hover-border-color: var(--ks-border-strong);
        --kel-input-bg-color: var(--ks-bg-input);
    }

    .kel-input__inner, .kel-textarea__inner {
        font-variant-ligatures: none;
    }

    @media (pointer: coarse) {
        .kel-input,
        .kel-input.kel-input--small,
        .kel-input.kel-input--large {
            --kel-input-height: 2.75rem;
        }

        .kel-input__inner,
        .kel-textarea__inner {
            font-size: 1rem;
        }

        .kel-textarea__inner {
            min-height: 2.75rem;
        }
    }

    .kel-textarea__inner {
        /* A full-width textarea has nothing to gain from horizontal resizing, and both it and a */
        /* long unbroken word were producing a horizontal scrollbar under the field. */
        resize: vertical;
        overflow-x: hidden;
        overflow-wrap: break-word;
    }

    .kel-input {
        width: 100%;

        &.kel-input--small {
            & .kel-input__wrapper {
                border-radius: var(--ks-radius-sm);
            }
        }

        & .kel-input-group__append, & .kel-input-group__prepend {
            color: var(--ks-text-dim);
        }

        &.ks-input--borderless {
            --kel-input-bg-color: transparent;
            --kel-input-border-color: transparent;
            --kel-input-hover-border-color: transparent;
            --kel-input-focus-border-color: transparent;

            & .kel-input__wrapper {
                padding-inline: 0;
            }
        }

        &.ks-input--reserve-clear {
            & .kel-input__inner {
                padding-inline-end: var(--ks-spacing-5);
            }

            & .kel-input__suffix {
                position: absolute;
                inset-block: 0;
                inset-inline-end: var(--ks-spacing-3);
            }
        }
    }
</style>
