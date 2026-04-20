<template>
    <div class="triggers-add">
        <div class="toolbar">
            <div class="search-wrapper">
                <Magnify class="search-icon" />
                <input
                    v-model="searchQuery"
                    type="text"
                    class="search-input"
                    :placeholder="$t('triggers.add.search_placeholder')"
                >
            </div>
            <div class="filter-group">
                <button
                    v-for="option in filterOptions"
                    :key="option.value"
                    type="button"
                    class="filter-button"
                    :class="{active: activeCategoryFilter === option.value}"
                    @click="activeCategoryFilter = option.value"
                >
                    {{ option.label }}
                </button>
            </div>
        </div>

        <div v-if="loading" class="state-empty">
            <el-skeleton :rows="3" animated />
        </div>

        <div v-else-if="!hasAnyVisibleTrigger" class="state-empty">
            <h4>{{ $t("triggers.add.empty.title") }}</h4>
            <p>{{ $t("triggers.add.empty.hint") }}</p>
        </div>

        <template v-else>
            <TriggersCategorySection
                v-if="showCategory('core')"
                category="core"
                :title="$t('triggers.add.category.core.title')"
                :description="$t('triggers.add.category.core.description')"
                :triggers="coreTriggers"
                :expandAll="true"
                @add="openConfigureModal"
            />
            <TriggersCategorySection
                v-if="showCategory('realtime')"
                category="realtime"
                :title="$t('triggers.add.category.realtime.title')"
                :description="$t('triggers.add.category.realtime.description')"
                :triggers="realtimeTriggers"
                @add="openConfigureModal"
            />
            <TriggersCategorySection
                v-if="showCategory('app')"
                category="app"
                :title="$t('triggers.add.category.app.title')"
                :description="$t('triggers.add.category.app.description')"
                :triggers="appTriggers"
                @add="openConfigureModal"
            />
        </template>

        <TriggerConfigureModal
            v-if="selectedTrigger"
            v-model:visible="configureModalVisible"
            :trigger="selectedTrigger"
            @cancel="configureModalVisible = false"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref} from "vue";
    import {useI18n} from "vue-i18n";
    import Magnify from "vue-material-design-icons/Magnify.vue";

    import {usePluginsStore, type TriggerPluginDto} from "../../stores/plugins";
    import TriggersCategorySection from "./TriggersCategorySection.vue";
    import TriggerConfigureModal from "./TriggerConfigureModal.vue";

    type CategoryFilter = "all" | "core" | "realtime" | "app";

    const {t} = useI18n({useScope: "global"});
    const pluginsStore = usePluginsStore();

    const loading = ref(true);
    const searchQuery = ref("");
    const activeCategoryFilter = ref<CategoryFilter>("all");
    const allTriggers = ref<TriggerPluginDto[]>([]);
    const selectedTrigger = ref<TriggerPluginDto | null>(null);
    const configureModalVisible = ref(false);

    const MCP_TOOL_TYPE = "io.kestra.core.models.triggers.McpTool";

    const filterOptions = computed<{value: CategoryFilter; label: string}[]>(() => [
        {value: "all", label: t("triggers.add.filter.all")},
        {value: "core", label: t("triggers.add.filter.core")},
        {value: "realtime", label: t("triggers.add.filter.realtime")},
        {value: "app", label: t("triggers.add.filter.app")},
    ]);

    function filterBySearch(triggers: TriggerPluginDto[]) {
        const q = searchQuery.value.trim().toLowerCase();
        if (!q) return triggers;
        return triggers.filter(tr =>
            tr.name.toLowerCase().includes(q) ||
            tr.type.toLowerCase().includes(q) ||
            (tr.description ?? "").toLowerCase().includes(q)
        );
    }

    const coreTriggers = computed(() => {
        const list = filterBySearch(allTriggers.value.filter(tr => tr.group === "core"));
        return [...list].sort((a, b) => {
            if (a.type === MCP_TOOL_TYPE) return -1;
            if (b.type === MCP_TOOL_TYPE) return 1;
            return a.name.localeCompare(b.name);
        });
    });

    const realtimeTriggers = computed(() =>
        filterBySearch(allTriggers.value.filter(tr => tr.group === "realtime"))
    );

    const appTriggers = computed(() =>
        filterBySearch(allTriggers.value.filter(tr => tr.group === "app"))
    );

    function showCategory(group: "core" | "realtime" | "app"): boolean {
        return activeCategoryFilter.value === "all" || activeCategoryFilter.value === group;
    }

    const hasAnyVisibleTrigger = computed(() =>
        coreTriggers.value.length > 0 ||
        realtimeTriggers.value.length > 0 ||
        appTriggers.value.length > 0
    );

    function openConfigureModal(trigger: TriggerPluginDto) {
        selectedTrigger.value = trigger;
        configureModalVisible.value = true;
    }

    onMounted(async () => {
        try {
            const [triggers] = await Promise.all([
                pluginsStore.listTriggers({}),
                pluginsStore.fetchIcons(),
            ]);
            allTriggers.value = triggers;
        } finally {
            loading.value = false;
        }
    });
</script>

<style scoped lang="scss">
    .triggers-add {
        display: flex;
        flex-direction: column;
        gap: 18px;
    }

    .toolbar {
        display: flex;
        gap: 12px;
        align-items: center;
        flex-wrap: wrap;
    }

    .search-wrapper {
        position: relative;
        flex: 1 1 280px;
        max-width: 520px;
    }

    .search-icon {
        position: absolute;
        left: 0.75rem;
        top: 50%;
        transform: translateY(-50%);
        color: var(--ks-content-tertiary, #888);
        pointer-events: none;
        font-size: 1rem;
    }

    .search-input {
        width: 100%;
        padding: 0.5rem 0.75rem 0.5rem 2.25rem;
        background: var(--ks-background-input, var(--bs-body-bg));
        border: 1px solid var(--ks-border-primary, var(--bs-border-color));
        border-radius: 6px;
        color: var(--ks-content-primary, var(--bs-body-color));
        font-size: 0.875rem;

        &:focus {
            outline: none;
            border-color: var(--el-color-primary);
            box-shadow: 0 0 0 2px rgba(124, 58, 237, 0.15);
        }

        &::placeholder {
            color: var(--ks-content-tertiary, #888);
        }
    }

    .filter-group {
        display: inline-flex;
        border: 1px solid var(--ks-border-primary, var(--bs-border-color));
        border-radius: 6px;
        overflow: hidden;
    }

    .filter-button {
        padding: 0.5rem 1rem;
        background: transparent;
        border: none;
        color: var(--ks-content-secondary, var(--bs-gray-600));
        font-size: 0.875rem;
        font-weight: 500;
        cursor: pointer;
        transition: background-color 0.12s ease, color 0.12s ease;

        & + .filter-button {
            border-left: 1px solid var(--ks-border-primary, var(--bs-border-color));
        }

        &:hover {
            color: var(--ks-content-primary, var(--bs-body-color));
        }

        &.active {
            background: var(--el-color-primary);
            color: #fff;
        }
    }

    .state-empty {
        padding: 3rem 1rem;
        text-align: center;

        h4 {
            margin-bottom: 0.5rem;
        }

        p {
            color: var(--ks-content-secondary, var(--bs-gray-600));
            margin: 0;
        }
    }
</style>
