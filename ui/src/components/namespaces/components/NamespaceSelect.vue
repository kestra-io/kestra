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
        :placeholder="$t('namespaces')"
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
    import {computed, onMounted} from "vue"
    import {useNamespacesStore} from "override/stores/namespaces"
    import DotsSquare from "vue-material-design-icons/DotsSquare.vue"
    import {storageKeys} from "../../../utils/constants";

    withDefaults(defineProps<{
        multiple?: boolean,
        readOnly?: boolean,
        clearable?: boolean,
        taggable?: boolean
    }>(), {
        multiple: false,
        clearable: true
    });

    const modelValue = defineModel<string | string[]>();

    const namespacesStore = useNamespacesStore();

    const validValues = computed(() =>
        [modelValue.value].flat().filter(Boolean)
    )

    const options = computed(() => {
        return namespacesStore.autocomplete === undefined ? [] : namespacesStore.autocomplete
            .map(value => {
                return {id: value, label: value}
            })
    })

    const onSearch = (search) => {
        namespacesStore.loadAutocomplete({
            q: search,
            ids: modelValue.value as string[] ?? [],
        })
    }

    onMounted(() => {
        if (modelValue.value === undefined || modelValue.value.length === 0) {
            const defaultNamespace = localStorage.getItem(storageKeys.DEFAULT_NAMESPACE);
            if (Array.isArray(modelValue.value)) {
                if (defaultNamespace != null) {
                    modelValue.value = [defaultNamespace];
                }
            } else {
                modelValue.value = defaultNamespace ?? modelValue.value;
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
