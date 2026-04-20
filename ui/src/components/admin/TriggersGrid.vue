<template>
    <div class="triggers-grid">
        <div class="toolbar">
            <div class="search-wrapper">
                <SearchField
                    :router="false"
                    placeholder="triggers.add.search_placeholder"
                    @search="searchQuery = $event"
                />
            </div>
            <el-radio-group v-model="activeCategoryFilter" class="filter-group">
                <el-radio-button
                    v-for="option in filterOptions"
                    :key="option.value"
                    :value="option.value"
                    :label="option.value"
                >
                    {{ option.label }}
                </el-radio-button>
            </el-radio-group>
        </div>

        <div v-if="loading" class="state-empty">
            <el-skeleton :rows="3" animated />
        </div>

        <div v-else-if="!hasAnyVisibleTrigger" class="state-empty">
            <h4>{{ $t("triggers.add.empty.title") }}</h4>
            <p>{{ $t("triggers.add.empty.hint") }}</p>
        </div>

        <template v-else>
            <template v-for="section in SECTIONS" :key="section.key">
                <TriggersCategorySection
                    v-if="showCategory(section.key)"
                    v-bind="section.props"
                    :triggers="groupedTriggers[section.key]"
                    :expandAll="section.expandAll"
                    @add="openConfigureModal"
                />
            </template>
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

    import SearchField from "../layout/SearchField.vue";
    import TriggersCategorySection from "./TriggersCategorySection.vue";
    import TriggerConfigureModal from "./TriggerConfigureModal.vue";

    import {usePluginsStore, type TriggerPluginDto} from "../../stores/plugins";
    import {MCP_TOOL_TYPE} from "./triggerCatalog";

    type CategoryFilter = "all" | "core" | "realtime" | "app";
    type TriggerGroup = Exclude<CategoryFilter, "all">;

    const CATEGORY_FILTER_VALUES: CategoryFilter[] = ["all", "core", "realtime", "app"];

    const {t} = useI18n({useScope: "global"});
    const pluginsStore = usePluginsStore();

    const loading = ref(true);
    const searchQuery = ref("");
    const activeCategoryFilter = ref<CategoryFilter>("all");
    const allTriggers = ref<TriggerPluginDto[]>([]);
    const selectedTrigger = ref<TriggerPluginDto | null>(null);
    const configureModalVisible = ref(false);

    const SECTIONS = computed(() => [
        {
            key: "core" as TriggerGroup,
            expandAll: true,
            props: {
                title: t("triggers.add.category.core.title"),
                description: t("triggers.add.category.core.description"),
            }
        },
        {
            key: "realtime" as TriggerGroup,
            props: {
                title: t("triggers.add.category.realtime.title"),
                description: t("triggers.add.category.realtime.description"),
            }
        },
        {
            key: "app" as TriggerGroup,
            props: {
                title: t("triggers.add.category.app.title"),
                description: t("triggers.add.category.app.description"),
            }
        }
    ]);

    const filterOptions = computed(() => CATEGORY_FILTER_VALUES.map(value => ({
        value,
        label: t(`triggers.add.filter.${value}`)
    })));

    function filterBySearch(triggers: TriggerPluginDto[]) {
        const q = searchQuery.value.trim().toLowerCase();
        if (!q) return triggers;

        return triggers.filter(tr =>
            tr.name.toLowerCase().includes(q) ||
            tr.type.toLowerCase().includes(q) ||
            (tr.description ?? "").toLowerCase().includes(q)
        );
    }

    const groupedTriggers = computed(() => {
        const filtered = filterBySearch(allTriggers.value);

        return {
            core: filtered
                .filter(tr => tr.group === "core")
                .sort((a, b) => {
                    if (a.type === MCP_TOOL_TYPE) return -1;
                    if (b.type === MCP_TOOL_TYPE) return 1;
                    return a.name.localeCompare(b.name);
                }),
            realtime: filtered.filter(tr => tr.group === "realtime"),
            app: filtered.filter(tr => tr.group === "app")
        };
    });

    function showCategory(group: TriggerGroup): boolean {
        return activeCategoryFilter.value === "all" || activeCategoryFilter.value === group;
    }

    const hasAnyVisibleTrigger = computed(() =>
        Object.values(groupedTriggers.value).some(triggers => triggers.length > 0)
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
    .triggers-grid {
        display: flex;
        flex-direction: column;
        gap: 1.125rem;
    }

    .toolbar {
        display: flex;
        gap: 0.75rem;
        align-items: center;
        flex-wrap: wrap;
    }

    .search-wrapper {
        position: relative;
        flex: 1 1 17.5rem;
        max-width: 32.5rem;
    }

    .state-empty {
        padding: 3rem 1rem;
        text-align: center;

        h4 {
            margin-bottom: 0.5rem;
        }

        p {
            color: var(--ks-content-secondary);
            margin: 0;
        }
    }
</style>
