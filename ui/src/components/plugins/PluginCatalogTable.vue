<template>
    <div class="plugin-catalog-table" data-test="plugin-catalog-table">
        <KsDataTable
            v-model:currentPage="currentPage"
            v-model:pageSize="pageSize"
            :data="pageRows"
            :total="rows.length"
            rowKey="name"
            :noDataText="$t('pluginPage.empty.title')"
        >
            <KsTableColumn :label="$t('pluginPage.view.plugin')" minWidth="200">
                <template #default="{row}">
                    <div class="plugin-catalog-table-name" data-test="plugin-catalog-table-name">
                        <router-link :to="row.route">
                            {{ row.title }}
                        </router-link>
                        <span class="plugin-catalog-table-artifact">{{ row.name }}</span>
                    </div>
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
    import {extractPluginElements, getPluginReleaseUrl, type Plugin} from "../../utils/pluginUtils"
    import {usePluginsEnrichmentStore} from "../../stores/pluginsEnrichment"

    const props = defineProps<{
        plugins: Plugin[]
    }>()

    const route = useRoute()
    const enrichmentStore = usePluginsEnrichmentStore()

    const currentPage = ref(1)
    const pageSize = ref(25)

    const rows = computed(() => props.plugins.map(plugin => {
        const elementClasses = Object.values(extractPluginElements(plugin)).flat()
        const repositoryReleaseUrl = getPluginReleaseUrl(plugin.group)
        // EE repositories are private, so their release notes come from the public API, one version at a time.
        const versionsCls = repositoryReleaseUrl ? undefined : elementClasses[0]
        return {
            name: plugin.name,
            title: enrichmentStore.getEnrichment(plugin)?.title ?? plugin.title.capitalize(),
            route: {name: "plugins/group", params: {...route.params, name: plugin.name}},
            taskCount: elementClasses.length,
            blueprintCount: enrichmentStore.getEnrichment(plugin)?.blueprintCount ?? 0,
            versionsCls,
            releaseNotesUrl: repositoryReleaseUrl ?? enrichmentStore.getVersions(versionsCls)[0]?.releaseNotesUrl ?? undefined,
        }
    }))

    const pageRows = computed(() => rows.value.slice((currentPage.value - 1) * pageSize.value, currentPage.value * pageSize.value))

    watch(() => pageRows.value.map(row => row.versionsCls).filter(Boolean).join(), () => {
        for (const row of pageRows.value) {
            if (row.versionsCls) enrichmentStore.fetchVersions(row.versionsCls)
        }
    }, {immediate: true})

    watch(() => rows.value.map(row => row.name).join(), () => {
        currentPage.value = 1
    })
</script>

<style scoped>
    .plugin-catalog-table {
        padding: 0 var(--ks-spacing-6) var(--ks-spacing-6);
    }

    .plugin-catalog-table-name {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
    }

    .plugin-catalog-table-artifact {
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-secondary);
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
