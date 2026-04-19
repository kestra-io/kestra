<template>
    <section class="triggers-add container">
        <div class="toolbar">
            <el-input
                v-model="searchQuery"
                :placeholder="$t('triggers.add.search_placeholder')"
                :prefix-icon="Magnify"
                clearable
                class="search-input"
            />
            <el-radio-group v-model="activeCategoryFilter" class="category-filter">
                <el-radio-button label="all">
                    {{ $t("triggers.add.filter.all") }}
                </el-radio-button>
                <el-radio-button label="core">
                    {{ $t("triggers.add.filter.core") }}
                </el-radio-button>
                <el-radio-button label="realtime">
                    {{ $t("triggers.add.filter.realtime") }}
                </el-radio-button>
                <el-radio-button label="app">
                    {{ $t("triggers.add.filter.app") }}
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
            <TriggersCategorySection
                v-if="activeCategoryFilter === 'all' || activeCategoryFilter === 'core'"
                category="core"
                :title="$t('triggers.add.category.core.title')"
                :description="$t('triggers.add.category.core.description')"
                :triggers="coreTriggers"
                :expand-all="true"
                @add="openConfigureModal"
            />
            <TriggersCategorySection
                v-if="activeCategoryFilter === 'all' || activeCategoryFilter === 'realtime'"
                category="realtime"
                :title="$t('triggers.add.category.realtime.title')"
                :description="$t('triggers.add.category.realtime.description')"
                :triggers="realtimeTriggers"
                @add="openConfigureModal"
            />
            <TriggersCategorySection
                v-if="activeCategoryFilter === 'all' || activeCategoryFilter === 'app'"
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
    </section>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref} from "vue";
    import Magnify from "vue-material-design-icons/Magnify.vue";

    import {usePluginsStore, type TriggerPluginDto} from "../../stores/plugins";
    import TriggersCategorySection from "./TriggersCategorySection.vue";
    import TriggerConfigureModal from "./TriggerConfigureModal.vue";

    type CategoryFilter = "all" | "core" | "realtime" | "app";

    const pluginsStore = usePluginsStore();

    const loading = ref(true);
    const searchQuery = ref("");
    const activeCategoryFilter = ref<CategoryFilter>("all");
    const allTriggers = ref<TriggerPluginDto[]>([]);
    const selectedTrigger = ref<TriggerPluginDto | null>(null);
    const configureModalVisible = ref(false);

    const MCP_TOOL_TYPE = "io.kestra.core.models.triggers.McpTool";

    function filterBySearch(triggers: TriggerPluginDto[]) {
        const q = searchQuery.value.trim().toLowerCase();
        if (!q) return triggers;
        return triggers.filter(t =>
            t.name.toLowerCase().includes(q) ||
            t.type.toLowerCase().includes(q) ||
            (t.description ?? "").toLowerCase().includes(q)
        );
    }

    const coreTriggers = computed(() => {
        const list = filterBySearch(allTriggers.value.filter(t => t.group === "core"));
        // Pin MCP Tool trigger to the top when it is available.
        return [...list].sort((a, b) => {
            if (a.type === MCP_TOOL_TYPE) return -1;
            if (b.type === MCP_TOOL_TYPE) return 1;
            return a.name.localeCompare(b.name);
        });
    });

    const realtimeTriggers = computed(() =>
        filterBySearch(allTriggers.value.filter(t => t.group === "realtime"))
    );

    const appTriggers = computed(() =>
        filterBySearch(allTriggers.value.filter(t => t.group === "app"))
    );

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
        padding: 1.5rem;
    }

    .toolbar {
        display: flex;
        gap: 1rem;
        margin-bottom: 1.5rem;
        align-items: center;
        flex-wrap: wrap;
    }

    .search-input {
        max-width: 420px;
        flex: 1 1 280px;
    }

    .category-filter {
        flex-shrink: 0;
    }

    .state-empty {
        padding: 2rem;
        text-align: center;

        h4 {
            margin-bottom: .5rem;
        }
    }
</style>
