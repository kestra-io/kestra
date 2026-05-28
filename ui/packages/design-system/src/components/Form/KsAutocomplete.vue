<template>
    <ElAutocomplete
        v-model="model"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @select="emit('select', $event)"
    >
        <template v-if="$slots.default" #default="p">
            <slot v-bind="p" />
        </template>
        <template v-if="$slots.prepend" #prepend>
            <slot name="prepend" />
        </template>
        <template v-if="$slots.suffix" #suffix>
            <slot name="suffix" />
        </template>
    </ElAutocomplete>
</template>

<script setup lang="ts">
    import {ElAutocomplete} from "element-plus"

    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<any>()

    const props = withDefaults(defineProps<{
        placeholder?: string
        disabled?: boolean
        clearable?: boolean
        fetchSuggestions?: (query: string, callback: (results: any[]) => void) => void
        triggerOnFocus?: boolean
        valueKey?: string
    }>(), {
        placeholder: undefined,
        fetchSuggestions: undefined,
        triggerOnFocus: undefined,
        valueKey: undefined,
    })

    const emit = defineEmits<{
        select: [item: any]
    }>()

    defineSlots<{
        default?: (scope: {item: any}) => unknown
        prepend?(): unknown
        suffix?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/autocomplete';

    .kel-autocomplete {
        .kel-input {
            height: 100%;
            --kel-input-bg-color: var(--ks-bg-base);
        }

        .kel-input__suffix-inner {
            gap: .5rem;

            > span:not(.material-design-icon) {
                font-size: var(--ks-font-size-xs);
                line-height: 1.25rem;
            }
        }
    }
    .kel-autocomplete-suggestion {
        .kel-autocomplete-suggestion__wrap {
            max-height: 40vh;
            padding: 10px 12px 10px 10px;
        }

        li {
            --kel-fill-color-light: var(--ks-bg-hover-elevated);
            padding: 0 1rem;
            border-radius: 5px;

            &.highlighted {
                margin-bottom: 3px;
            }

            a {
                color: var(--ks-text-primary);
                justify-content: space-between;
            }
        }
    }
</style>
