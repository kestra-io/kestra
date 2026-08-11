<template>
    <KsSelect
        :class="{'fit-text': !fit && !multiple}"
        v-model="modelValue"
        :multiple
        :singleLineTags="multiple"

        :disabled="readOnly"
        :clearable="clearable"
        :allowCreate="taggable"
        filterable
        :fit="fit"
        :placeholder="placeholder ?? $t('namespaces')"
        :suffixIcon="suffixIcon"
    >
        <template #tag>
            <KsTag
                v-for="value in visibleTags"
                :key="value"
                closable
                type="info"
                @close="modelValue = (modelValue as string[]).filter(v => v !== value)"
            >
                <FolderOpenOutline />
                <span class="tag-label" :title="value">{{ value }}</span>
            </KsTag>
            <KsTooltip v-if="hiddenTags.length > 0" placement="top">
                <template #content>
                    <div v-for="value in hiddenTags" :key="value">{{ value }}</div>
                </template>
                <KsTag class="tag-counter">
                    +{{ hiddenTags.length }}
                </KsTag>
            </KsTooltip>
        </template>
        <KsOption
            v-for="item in options"
            :key="item.id"
            :label="item.label"
            :value="item.id"
        />
    </KsSelect>
</template>

<script setup lang="ts">
    import {computed, onMounted} from "vue"
    import {useNamespacesStore} from "override/stores/namespaces"
    import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue"
    import Lock from "vue-material-design-icons/Lock.vue"
    import {defaultNamespace} from "../../../composables/useNamespaces"

    const props = withDefaults(defineProps<{
        multiple?: boolean,
        readOnly?: boolean,
        clearable?: boolean,
        taggable?: boolean
        placeholder?: string | undefined
        fit?: boolean
        autoDefault?: boolean
        maxVisibleTags?: number
    }>(), {
        multiple: false,
        clearable: true,
        placeholder: undefined,
        autoDefault: true,
        maxVisibleTags: 3,
    })

    const suffixIcon = computed(() => props.readOnly ? Lock : undefined)

    defineOptions({
        inheritAttrs: false,
    })

    const modelValue = defineModel<string | string[]>()

    const namespacesStore = useNamespacesStore()

    const validValues = computed(() =>
        [modelValue.value].flat().filter(Boolean) as string[],
    )

    const visibleTags = computed(() => validValues.value.slice(0, props.maxVisibleTags))

    const hiddenTags = computed(() => validValues.value.slice(props.maxVisibleTags))

    const options = computed(() => {
        return namespacesStore.autocomplete === undefined ? [] : namespacesStore.autocomplete
            .map((value: any) => {
                return {id: value, label: value}
            })
    })

    onMounted(() => {
        const ids = [modelValue.value].flat().filter(Boolean) as string[]
        namespacesStore.loadAutocomplete({ids})

        if (props.autoDefault && (modelValue.value === undefined || modelValue.value.length === 0)) {
            const defaultNamespaceVal = defaultNamespace()
            if (Array.isArray(modelValue.value)) {
                if (defaultNamespaceVal != null) {
                    modelValue.value = [defaultNamespaceVal]
                }
            } else {
                modelValue.value = defaultNamespaceVal ?? modelValue.value
            }
        }
    })
</script>

<style scoped lang="scss">
    .tag-label {
        max-width: 12.5rem;
        min-width: 5ch;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .tag-counter {
        flex-shrink: 0;
    }
</style>
