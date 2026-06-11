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
            <div class="category-tags">
                <KsCheckTag
                    v-for="value in FILTER_VALUES"
                    :key="value"
                    pill
                    :checked="activeFilter === value"
                    @change="activeFilter = value"
                >
                    <template v-if="GROUP_ICONS[value]" #icon>
                        <component :is="GROUP_ICONS[value]" :size="16" class="group-icon" />
                    </template>
                    {{ $t(`triggers_add_filter_${value}`) }}
                </KsCheckTag>
            </div>
            <div class="sort-by">
                <span class="sort-label">{{ $t("pluginPage.sortBy") }}</span>
                <KsSelect v-model="sortBy" size="small" class="sort-select">
                    <KsOption
                        v-for="option in SORT_OPTIONS"
                        :key="option.value"
                        :value="option.value"
                        :label="$t(option.labelKey)"
                    />
                </KsSelect>
            </div>
        </div>

        <div v-if="loading" class="state-loading">
            <KsSkeleton :rows="3" animated />
        </div>

        <KsTableEmpty
            v-else-if="!hasAnyVisibleTrigger"
            class="triggers-empty"
            :title="$t('triggers_add_empty_title')"
        />

        <div v-else class="card-grid">
            <TriggerCard
                v-for="trigger in visibleTriggers"
                :key="trigger.type"
                :trigger="trigger"
                @add="openConfigureModal"
            />
        </div>

        <AddTriggerModal
            v-if="selectedTrigger"
            v-model:visible="configureModalVisible"
            :trigger="selectedTrigger"
            @cancel="configureModalVisible = false"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, markRaw, onMounted, ref, type Component} from "vue"

    import AvTimer from "vue-material-design-icons/AvTimer.vue"
    import BriefcaseOutline from "vue-material-design-icons/BriefcaseOutline.vue"
    import LayersTripleOutline from "vue-material-design-icons/LayersTripleOutline.vue"

    import SearchField from "../../layout/SearchField.vue"
    import TriggerCard from "./TriggerCard.vue"
    import AddTriggerModal from "./AddTriggerModal.vue"

    import {usePluginsStore, type TriggerPluginDto} from "../../../stores/plugins"
    import {triggerDisplayName} from "./triggerCatalog"

    const TRIGGER_GROUPS = ["core", "realtime", "app"] as const
    const FILTER_VALUES = ["all", ...TRIGGER_GROUPS] as const
    const SORT_OPTIONS = [
        {value: "nameAsc", labelKey: "pluginPage.sort.nameAsc"},
        {value: "nameDesc", labelKey: "pluginPage.sort.nameDesc"},
    ] as const

    type FilterValue = typeof FILTER_VALUES[number];
    type SortKey = typeof SORT_OPTIONS[number]["value"];

    const GROUP_ICONS: Partial<Record<FilterValue, Component>> = markRaw({
        core: BriefcaseOutline,
        realtime: AvTimer,
        app: LayersTripleOutline,
    })

    const nameAsc = (a: TriggerPluginDto, b: TriggerPluginDto) =>
        triggerDisplayName(a).localeCompare(triggerDisplayName(b))

    const COMPARATORS: Record<SortKey, (a: TriggerPluginDto, b: TriggerPluginDto) => number> = {
        nameAsc,
        nameDesc: (a, b) => nameAsc(b, a),
    }

    const pluginsStore = usePluginsStore()

    const loading = ref(true)
    const searchQuery = ref("")
    const activeFilter = ref<FilterValue>("all")
    const sortBy = ref<SortKey>("nameAsc")
    const allTriggers = ref<TriggerPluginDto[]>([])
    const selectedTrigger = ref<TriggerPluginDto | null>(null)
    const configureModalVisible = ref(false)

    const visibleTriggers = computed(() => {
        const q = searchQuery.value.trim().toLowerCase()
        const matchesSearch = (tr: TriggerPluginDto) =>
            !q ||
            tr.name.toLowerCase().includes(q) ||
            tr.type.toLowerCase().includes(q) ||
            (tr.description ?? "").toLowerCase().includes(q)

        return allTriggers.value
            .filter(tr => (activeFilter.value === "all" || tr.group === activeFilter.value) && matchesSearch(tr))
            .sort(COMPARATORS[sortBy.value] ?? nameAsc)
    })

    const hasAnyVisibleTrigger = computed(() => visibleTriggers.value.length > 0)

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

        .search-wrapper {
            flex: 1 1 17.5rem;
            max-width: 32.5rem;
        }

        .category-tags {
            display: flex;
            gap: var(--ks-spacing-2);

            .group-icon {
                color: var(--ks-icon-active);
            }
        }

        .sort-by {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);
            margin-left: auto;
            flex: 0 0 auto;

            .sort-label {
                color: var(--ks-text-secondary);
                font-size: var(--ks-font-size-xs);
                white-space: nowrap;
            }

            .sort-select {
                width: 8.75rem;
            }
        }
    }

    .state-loading {
        padding: 3rem 1rem;
    }

    .triggers-empty {
        min-height: 60vh;
    }

    .card-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(21.875rem, 1fr));
        gap: 1rem;
    }
</style>
