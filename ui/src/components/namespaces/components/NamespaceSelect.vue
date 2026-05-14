<template>
    <KsSelect
        class="fit-text"
        v-model="modelValue"
        :multiple
        collapseTags
        :disabled="readOnly"
        :clearable="clearable"
        :allowCreate="taggable"
        filterable
        :placeholder="placeholder ?? $t('namespaces')"
        :suffixIcon="suffixIcon"
    >
        <template #tag>
            <KsTag
                v-for="(value, index) in validValues"
                :key="index"
                class="namespace-tag"
                closable
                @close="modelValue = (modelValue as string[]).filter(v => v !== value)"
            >
                <FolderOpenOutline class="tag-icon" />
                {{ value }}
            </KsTag>
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
    }>(), {
        multiple: false,
        clearable: true,
        placeholder: undefined,
    })

    const suffixIcon = computed(() => props.readOnly ? Lock : undefined)

    defineOptions({
        inheritAttrs: false,
    })

    const modelValue = defineModel<string | string[]>()

    const namespacesStore = useNamespacesStore()

    const validValues = computed(() =>
        [modelValue.value].flat().filter(Boolean),
    )

    const options = computed(() => {
        return namespacesStore.autocomplete === undefined ? [] : namespacesStore.autocomplete
            .map((value: any) => {
                return {id: value, label: value}
            })
    })

    onMounted(() => {
        namespacesStore.loadAutocomplete({ids: modelValue.value as string[] ?? []})

        if (modelValue.value === undefined || modelValue.value.length === 0) {
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
    .namespace-tag {
        background-color: var(--ks-log-background-debug) !important;
        color: var(--ks-log-content-debug);
        border: 1px solid var(--ks-log-border-debug);
        padding: 0 6px;

        :deep(.kel-tag__content) {
            display: flex;
            align-items: center;
            gap: 4px;
        }

        :deep(.kel-tag__close) {
            color: var(--ks-log-content-debug);

            &:hover {
                background-color: transparent;
            }
        }
    }
</style>
