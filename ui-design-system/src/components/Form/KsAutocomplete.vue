<template>
    <ElAutocomplete
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @update:model-value="emit('update:modelValue', $event as string)"
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
    import {ElAutocomplete, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        modelValue?: string
        placeholder?: string
        disabled?: boolean
        clearable?: boolean
         
        fetchSuggestions?: (query: string, callback: (results: any[]) => void) => void
        triggerOnFocus?: boolean
        valueKey?: string
    }>(), {
        triggerOnFocus: undefined,
    })

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        "update:modelValue": [value: string]
         
        select: [item: any]
    }>()

    defineSlots<{
         
        default?: (scope: {item: any}) => unknown
        prepend?(): unknown
        suffix?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/autocomplete';

    .kel-autocomplete {
        .kel-input {
            height: 100%;
            --kel-input-bg-color: var(--ks-background-body);
        }

        .kel-input__suffix-inner {
            gap: .5rem;

            > span:not(.material-design-icon) {
                font-size: var(--kel-font-size-extra-small);
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
            // highlight of keyboard selection & element plus hover
            --kel-fill-color-light: var(--ks-select-hover);
            padding: 0 1rem;
            border-radius: 5px;

            &.highlighted {
                margin-bottom: 3px;
            }

            a {
                color: var(--ks-content-primary);
                justify-content: space-between;
            }
        }
    }
</style>