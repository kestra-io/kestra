<template>
    <div class="triggers-grid">
        <div class="toolbar">
            <div class="search-wrapper">
                <SearchField
                    :router="false"
                    placeholder="triggers_add_search_placeholder"
                    @search="searchQuery = $event"
                />
            </div>
            <KsRadioGroup v-model="activeCategoryFilter" class="filter-group">
                <KsRadioButton
                    v-for="value in FILTER_VALUES"
                    :key="value"
                    :value="value"
                    :label="value"
                >
                    {{ $t(`triggers_add_filter_${value}`) }}
                </KsRadioButton>
            </KsRadioGroup>
        </div>

        <div v-if="loading" class="state-empty">
            <KsSkeleton :rows="3" animated />
        </div>

        <div v-else-if="!hasAnyVisibleTrigger" class="state-empty">
            <h4>{{ $t("triggers_add_empty_title") }}</h4>
            <p>{{ $t("triggers_add_empty_hint") }}</p>
        </div>

        <template v-else>
            <TriggersCategorySection
                v-for="section in visibleSections"
                :key="section.key"
                :title="$t(`triggers_add_category_${section.key}_title`)"
                :description="$t(`triggers_add_category_${section.key}_description`)"
                :triggers="groupedTriggers[section.key]"
                :expandAll="section.expandAll"
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
    import {computed, onMounted, ref} from "vue"

    import SearchField from "../../layout/SearchField.vue"
    import TriggersCategorySection from "./TriggersCategorySection.vue"
    import TriggerConfigureModal from "./TriggerConfigureModal.vue"

    import {usePluginsStore, type TriggerPluginDto} from "../../../stores/plugins"
    import {MCP_TOOL_TYPE} from "./triggerCatalog"

    const TRIGGER_GROUPS = ["core", "realtime", "app"] as const
    const FILTER_VALUES = ["all", ...TRIGGER_GROUPS] as const

    type TriggerGroup = typeof TRIGGER_GROUPS[number];
    type CategoryFilter = typeof FILTER_VALUES[number];

    const SECTIONS: { key: TriggerGroup; expandAll?: boolean }[] = [
        {key: "core", expandAll: true},
        {key: "realtime"},
        {key: "app"},
    ]

    const pluginsStore = usePluginsStore()

    const loading = ref(true)
    const searchQuery = ref("")
    const activeCategoryFilter = ref<CategoryFilter>("all")
    const allTriggers = ref<TriggerPluginDto[]>([])
    const selectedTrigger = ref<TriggerPluginDto | null>(null)
    const configureModalVisible = ref(false)

    const groupedTriggers = computed(() => {
        const q = searchQuery.value.trim().toLowerCase()
        const matches = (tr: TriggerPluginDto) =>
            !q ||
            tr.name.toLowerCase().includes(q) ||
            tr.type.toLowerCase().includes(q) ||
            (tr.description ?? "").toLowerCase().includes(q)

        const inGroup = (group: TriggerGroup) =>
            allTriggers.value.filter(tr => tr.group === group && matches(tr))

        return {
            core: inGroup("core").sort((a, b) => {
                if (a.type === MCP_TOOL_TYPE) return -1
                if (b.type === MCP_TOOL_TYPE) return 1
                return a.name.localeCompare(b.name)
            }),
            realtime: inGroup("realtime"),
            app: inGroup("app"),
        }
    })

    const visibleSections = computed(() =>
        SECTIONS.filter(s => activeCategoryFilter.value === "all" || activeCategoryFilter.value === s.key),
    )

    const hasAnyVisibleTrigger = computed(() =>
        Object.values(groupedTriggers.value).some(triggers => triggers.length > 0),
    )

    function openConfigureModal(trigger: TriggerPluginDto) {
        selectedTrigger.value = trigger
        configureModalVisible.value = true
    }

    onMounted(async () => {
        try {
            const [triggers] = await Promise.all([
                pluginsStore.listTriggers(),
                pluginsStore.fetchIcons(),
            ])
            allTriggers.value = triggers
        } finally {
            loading.value = false
        }
    })
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
