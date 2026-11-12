<template>
    <div class="plugin-catalog-table" data-test="plugin-catalog-table">
        <KsDataTable
            v-model:currentPage="currentPage"
            v-model:pageSize="pageSize"
            :data="pageRows"
            :total="plugins.length"
            rowKey="name"
            :noDataText="$t('pluginPage.empty.title')"
        >
            <KsTableColumn :label="$t('pluginPage.view.plugin')" minWidth="200">
                <template #default="{row}">
                    <PluginCatalogTableName :title="row.title" :name="row.name" :group="row.group" :route="row.route" />
                </template>
            </KsTableColumn>

            <KsTableColumn :label="$t('tasks')">
                <template #default="{row}">
                    <span data-test="plugin-catalog-table-tasks">{{ row.taskCount }}</span>
                </template>
            </KsTableColumn>

            <KsTableColumn :label="$t('blueprints.title')">
                <template #default="{row}">
                    <span data-test="plugin-catalog-table-blueprints">{{ row.blueprintCount }}</span>
                </template>
            </KsTableColumn>

            <KsTableColumn :label="$t('pluginPage.overview.releaseNotes')">
                <template #default="{row}">
                    <a v-if="row.releaseNotesUrl" :href="row.releaseNotesUrl" target="_blank" rel="noopener noreferrer" class="plugin-catalog-table-link" data-test="plugin-catalog-table-release-notes">
                        {{ $t("pluginPage.overview.releaseNotes") }}
                        <OpenInNew :size="14" />
                    </a>
                </template>
            </KsTableColumn>
        </KsDataTable>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useRoute} from "vue-router"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import {getPluginReleaseUrl, pluginDisplayTitle, pluginElementClasses, type Plugin} from "../../utils/pluginUtils"
    import {usePluginsEnrichmentStore} from "../../stores/pluginsEnrichment"
    import PluginCatalogTableName from "./PluginCatalogTableName.vue"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        plugins: Plugin[]
    }>()

    const route = useRoute()
    const enrichmentStore = usePluginsEnrichmentStore()
    const currentPage = ref(1)
    const pageSize = ref(25)

    const pageRows = computed(() => props.plugins
        .slice((currentPage.value - 1) * pageSize.value, currentPage.value * pageSize.value)
        .map(plugin => {
            const enrichment = enrichmentStore.getEnrichment(plugin)
            return {
                name: plugin.name,
                group: plugin.group,
                title: pluginDisplayTitle(plugin, enrichment),
                route: {name: "plugins/group", params: {...route.params, name: plugin.name}},
                taskCount: pluginElementClasses(plugin).length,
                blueprintCount: enrichment?.blueprintCount ?? 0,
                releaseNotesUrl: getPluginReleaseUrl(plugin.group) ?? enrichment?.releaseNotesUrl,
            }
        }))

    watch(() => props.plugins.map(plugin => plugin.name).sort().join(), () => {
        currentPage.value = 1
    })
</script>

<style scoped>
    .plugin-catalog-table {
        padding: 0 var(--ks-spacing-6) var(--ks-spacing-6);
    }

    .plugin-catalog-table-link {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-link);
        text-decoration: none;
    }

    .plugin-catalog-table-link:hover {
        text-decoration: underline;
    }
</style>
