<template>
    <ElInput
        v-model="model"
        class="ks-search"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event)"
    >
        <template #prefix>
            <slot name="prefix">
                <Magnify class="ks-search__icon" />
            </slot>
        </template>
        <template v-if="$slots.suffix" #suffix>
            <slot name="suffix" />
        </template>
    </ElInput>
</template>

<script setup lang="ts">
    import {ElInput} from "element-plus"
    import Magnify from "vue-material-design-icons/Magnify.vue"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<string>()

    const props = defineProps<{
        placeholder?: string
        disabled?: boolean
        clearable?: boolean
        readonly?: boolean
        name?: string
        id?: string
    }>()

    const emit = defineEmits<{
        change: [value: string | number]
    }>()

    defineSlots<{
        prefix?(): unknown
        suffix?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    .ks-search {
        width: 100%;

        .kel-input__wrapper {
            gap: 8px;
            border-radius: 8px;
            background-color: var(--ks-bg-input);
            border: 1px solid var(--ks-border-subtle);
            box-shadow: 0px 1px 4px 0px var(--ks-shadow-element);
            transition: border-color 0.2s ease;

            &:hover {
                border: 1px solid var(--ks-border-subtle);
                box-shadow: 0px 1px 4px 0px var(--ks-shadow-element);
            }

            &.is-focus,
            &.is-focus:hover {
                border-color: var(--ks-border-focus);
                box-shadow: 0px 1px 4px 0px var(--ks-shadow-element);
            }
        }

        .kel-input__prefix,
        .kel-input__suffix {
            display: inline-flex;
            align-items: center;
        }

        .kel-input__inner {
            font-size: var(--ks-font-size-sm);

            &::placeholder {
                font-size: var(--ks-font-size-xs);
                font-weight: 400;
                color: var(--ks-text-secondary);
            }
        }

        .ks-search__icon {
            display: inline-flex;
            color: var(--ks-icon-default);
            font-size: var(--ks-font-size-sm);
        }
    }
</style>
