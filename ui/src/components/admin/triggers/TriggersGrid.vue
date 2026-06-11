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
            <TriggerCatalogCard
                v-for="trigger in visibleTriggers"
                :key="trigger.type"
                :trigger="trigger"
                @add="openConfigureModal"
            />
        </div>

        <TriggerConfigureModal
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
    import TriggerCatalogCard from "./TriggerCatalogCard.vue"
    import TriggerConfigureModal from "./TriggerConfigureModal.vue"

    import {usePluginsStore, type TriggerPluginDto} from "../../../stores/plugins"

    const TRIGGER_GROUPS = ["core", "realtime", "app"] as const
    const FILTER_VALUES = ["all", ...TRIGGER_GROUPS] as const

    type FilterValue = typeof FILTER_VALUES[number];

    const GROUP_ICONS: Partial<Record<FilterValue, Component>> = markRaw({
        core: BriefcaseOutline,
        realtime: AvTimer,
        app: LayersTripleOutline,
    })

    const pluginsStore = usePluginsStore()

    const loading = ref(true)
    const searchQuery = ref("")
    const activeFilter = ref<FilterValue>("all")
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

        return allTriggers.value.filter(tr =>
            (activeFilter.value === "all" || tr.group === activeFilter.value) && matchesSearch(tr),
        )
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
