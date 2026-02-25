<template>
    <el-select
        class="fit-text"
        v-model="modelValue"
        :multiple
        collapseTags
        :disabled="readOnly"
        :clearable="clearable"
        :allowCreate="taggable"
        :loading="isLoading"
        filterable
        remote
        remoteShowSuffix
        :remoteMethod="onSearch"
        :placeholder="placeholder ?? $t('namespaces')"
        :suffixIcon="readOnly ? Lock : undefined"
    >
        <template #tag>
            <el-tag
                v-for="(value, index) in validValues"
                :key="index"
                class="namespace-tag"
                closable
                @close="
                    modelValue = (modelValue as string[]).filter(
                        (v) => v !== value,
                    )
                "
            >
                <FolderOpenOutline class="tag-icon" />
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
    import {computed, onMounted, ref, watch} from "vue";
    import {useRoute} from "vue-router";
    import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue";
    import Lock from "vue-material-design-icons/Lock.vue";
    import {DASHBOARD_TYPE, useDashboardStore} from "../../../stores/dashboard.ts";

    const props = withDefaults(
        defineProps<{
            multiple?: boolean;
            readOnly?: boolean;
            clearable?: boolean;
            taggable?: boolean;
            placeholder?: string | undefined;
            dashboardType: DASHBOARD_TYPE;
        }>(),
        {
            multiple: false,
            clearable: true,
            placeholder: undefined
        }
    );

    defineOptions({
        inheritAttrs: false
    });

    const modelValue = defineModel<string | string[]>();

    const dashboardStore = useDashboardStore();
    const route = useRoute();

    const validValues = computed(() => [modelValue.value].flat().filter(Boolean));

    const dashboards = ref<{ id: string; title: string; isDefault: boolean }[]>([]);
    const isLoading = ref(false);
    const search = ref("");

    const options = computed(() => {
        const query = search.value.trim().toLowerCase();
        if (!query) {
            return dashboards.value.map((dashboard) => ({
                id: dashboard.id,
                label: dashboard.title
            }));
        }

        return dashboards.value
            .filter((dashboard) => dashboard.title.toLowerCase().includes(query))
            .map((dashboard) => ({
                id: dashboard.id,
                label: dashboard.title
            }));
    });

    const onSearch = (value: string) => {
        search.value = value;
    };

    const fetchDashboards = async () => {
        isLoading.value = true;
        try {
            dashboards.value = await dashboardStore.list({}, route);
        } finally {
            isLoading.value = false;
        }
    };

    const ensureDefaultSelection = async () => {
        const isEmpty =
            modelValue.value === undefined ||
            (Array.isArray(modelValue.value)
                ? modelValue.value.length === 0
                : modelValue.value === "");

        if (!isEmpty || dashboards.value.length === 0) {
            return;
        }

        const defaultId = await dashboardStore.getDefaultDashboard(props.dashboardType) ?? "default";

        const defaultDashboard =
            dashboards.value.find((dashboard) => dashboard.id === defaultId) ??
            dashboards.value[0];
        if (!defaultDashboard) {
            return;
        }

        if (Array.isArray(modelValue.value)) {
            modelValue.value = [defaultDashboard.id];
        } else {
            modelValue.value = defaultDashboard.id;
        }
    };

    onMounted(async () => {
        await fetchDashboards();
        await ensureDefaultSelection();
    });

    watch(
        () => route.params.tenant,
        async () => {
            await fetchDashboards();
            await ensureDefaultSelection();
        }
    );
</script>

<style scoped lang="scss">
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
