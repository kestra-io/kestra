<template>
    <el-select
        class="fit-text"
        v-model="modelValue"
        :multiple
        collapse-tags
        :disabled="readOnly"
        :clearable="clearable"
        :allow-create="taggable"
        filterable
        remote
        remote-show-suffix
        :remote-method="onSearch"
        :placeholder="t('namespaces')"
        :suffix-icon="readOnly ? Lock : undefined"
    >
        <template #tag>
            <el-tag
                v-for="(value, index) in validValues"
                :key="index"
                class="namespace-tag"
                closable
                @close="modelValue = (modelValue as string[]).filter(v => v !== value)"
            >
                <dots-square class="tag-icon" />
                {{ value }}
            </el-tag>
        </template>
        <el-option
            v-for="item in options"
            :key="item.id"
            :label="item.label"
            :value="item.id"
        />
    </el-select>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {useNamespacesStore} from "override/stores/namespaces"
    import DotsSquare from "vue-material-design-icons/DotsSquare.vue"
    import Lock from "vue-material-design-icons/Lock.vue";
    import {defaultNamespace} from "../../../composables/useNamespaces";

    const {t} = useI18n();

    const props = withDefaults(defineProps<{
        multiple?: boolean,
        readOnly?: boolean,
        clearable?: boolean,
        taggable?: boolean,
        // When set, authorize namespace discovery through this resource permission (e.g. KVSTORE)
        // instead of NAMESPACE, so users entitled to the resource can list their namespaces.
        resource?: string
    }>(), {
        multiple: false,
        clearable: true,
        resource: undefined
    });

    const modelValue = defineModel<string | string[]>();

    const namespacesStore = useNamespacesStore();

    const validValues = computed(() =>
        [modelValue.value].flat().filter(Boolean)
    )

    // In resource mode we discover namespaces through the resource-scoped ids endpoint (no
    // NAMESPACE permission required) and filter client-side; otherwise we use namespace autocomplete.
    const boundNamespaces = ref<string[]>([]);
    const query = ref("");

    const options = computed(() => {
        if (props.resource) {
            return boundNamespaces.value
                .filter(value => !query.value || value.toLowerCase().includes(query.value.toLowerCase()))
                .map(value => ({id: value, label: value}));
        }
        return namespacesStore.autocomplete === undefined ? [] : namespacesStore.autocomplete
            .map((value: any) => {
                return {id: value, label: value}
            })
    })

    const onSearch = (search: string) => {
        if (props.resource) {
            query.value = search;
            return;
        }
        namespacesStore.loadAutocomplete({
            q: search,
            ids: modelValue.value as string[] ?? [],
        })
    }

    onMounted(async () => {
        if (props.resource) {
            boundNamespaces.value = await namespacesStore.namespacesWithBinding({resource: props.resource});
        }
        if (modelValue.value === undefined || modelValue.value.length === 0) {
            const defaultNamespaceVal = defaultNamespace();
            if (Array.isArray(modelValue.value)) {
                if (defaultNamespaceVal != null) {
                    modelValue.value = [defaultNamespaceVal];
                }
            } else {
                modelValue.value = defaultNamespaceVal ?? modelValue.value;
            }
        }
    })
</script>

<style lang="scss" scoped>
    .namespace-tag {
        background-color: var(--ks-log-background-debug) !important;
        color: var(--ks-log-content-debug);
        border: 1px solid var(--ks-log-border-debug);
        padding: 0 6px;

        :deep(.el-tag__content) {
            display: flex;
            align-items: center;
            gap: 4px;
        }

        :deep(.el-tag__close) {
            color: var(--ks-log-content-debug);

            &:hover {
                background-color: transparent;
            }
        }
    }
</style>
