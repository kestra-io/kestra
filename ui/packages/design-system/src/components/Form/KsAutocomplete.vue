<template>
    <ElAutocomplete
        v-model="model"
        v-bind="({...filteredProps(), ...$attrs} as AutocompleteProps)"
        @select="emit('select', $event as T)"
    >
        <template v-if="$slots.default" #default="p">
            <slot :item="p.item as T" />
        </template>
        <template v-if="$slots.prepend" #prepend>
            <slot name="prepend" />
        </template>
        <template v-if="$slots.suffix" #suffix>
            <slot name="suffix" />
        </template>
    </ElAutocomplete>
</template>

<script setup lang="ts" generic="T">
    import type {HTMLAttributes} from "vue"
    import {ElAutocomplete, type AutocompleteProps} from "element-plus"
    import type {NativeAttributes} from "../../utils/nativeAttributes"

    import {usePassedProps} from "../../utils/filteredProps"

    type KsAutocompleteProps = Omit<AutocompleteProps, "modelValue" | "fetchSuggestions"> & {
        fetchSuggestions?: (query: string, callback: (results: T[]) => void) => void
    }

    defineOptions({inheritAttrs: false})

    const model = defineModel<string>()

    const props = defineProps<KsAutocompleteProps & /* @vue-ignore */ Omit<NativeAttributes<HTMLAttributes>, keyof KsAutocompleteProps>>()

    const emit = defineEmits<{
        select: [item: T]
    }>()

    defineSlots<{
        default?: (scope: {item: T}) => unknown
        prepend?(): unknown
        suffix?(): unknown
    }>()

    const filteredProps = usePassedProps(props)
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/autocomplete';
</style>

<style>
    .kel-autocomplete {
        & .kel-input {
            height: 100%;
            --kel-input-bg-color: var(--ks-bg-base);
        }

        & .kel-input__suffix-inner {
            gap: .5rem;

            & > span:not(.material-design-icon) {
                font-size: var(--ks-font-size-xs);
                line-height: 1.25rem;
            }
        }
    }

    @media (pointer: coarse) {
        .kel-autocomplete .kel-input {
            --kel-input-height: 2.75rem;
        }

        .kel-autocomplete .kel-input__inner {
            font-size: 1rem;
        }
    }

    .kel-autocomplete-suggestion {
        & .kel-autocomplete-suggestion__wrap {
            max-height: 40vh;
            padding: 10px 12px 10px 10px;
        }

        & li {
            --kel-fill-color-light: var(--ks-bg-hover-elevated);
            padding: 0 1rem;
            border-radius: 5px;

            &.highlighted {
                margin-bottom: 3px;
            }

            & a {
                color: var(--ks-text-primary);
                justify-content: space-between;
            }
        }
    }
</style>
