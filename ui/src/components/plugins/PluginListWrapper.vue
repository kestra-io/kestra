<template>
    <div class="plugin-list-wrapper">
        <div v-if="isLoading || !pluginsData" class="loading-container">
            <KsSkeleton animated :rows="8" />
        </div>
        <template v-else>
            <div v-if="!pluginsStore.editorPlugin && showFlowDoc" class="flow-doc">
                <div class="flow-doc-header">
                    <span class="flow-doc-title">{{ $t("flow_description") }}</span>
                </div>
                <KsMarkdown v-if="flowDescription" :content="flowDescription" class="flow-doc-content" />
                <p v-else class="flow-doc-empty">{{ $t("flow_no_description") }}</p>
            </div>
            <PluginList
                :plugins="pluginsData ?? []"
                :key="useMiscStore().theme"
            />
        </template>
    </div>
</template>

<script setup lang="ts">
    import {onMounted, ref, computed} from "vue"
    import {useMiscStore} from "override/stores/misc"
    import {usePluginsStore} from "../../stores/plugins"
    import {useFlowStore} from "../../stores/flow"
    import {KsMarkdown} from "@kestra-io/design-system"
    import PluginList from "./PluginList.vue"

    const isLoading = ref(false)
    const pluginsStore = usePluginsStore()
    const flowStore = useFlowStore()

    const pluginsData = computed(() => pluginsStore.plugins)

    const flowDescription = computed(() => flowStore.flowParsed?.description as string | undefined)
    const showFlowDoc = computed(() => flowStore.flowParsed !== undefined)

    onMounted(async () => {
        if (!pluginsData.value?.length) {
            await pluginsStore.listWithSubgroup({includeDeprecated: false})
        }
    })
</script>

<style scoped lang="scss">
    .plugin-list-wrapper {
        height: 100%;
        display: flex;
        flex-direction: column;
        background-color: var(--ks-bg-surface);
    }

    .loading-container {
        padding: var(--ks-spacing-4);
    }

    .flow-doc {
        border-bottom: 1px solid var(--ks-border-default);
        padding: var(--ks-spacing-4);
        background-color: var(--ks-bg-surface);
        flex-shrink: 0;
    }

    .flow-doc-header {
        margin-bottom: var(--ks-spacing-3);
    }

    .flow-doc-title {
        font-size: var(--ks-font-size-sm);
        font-weight: 600;
        color: var(--ks-text-primary);
    }

    .flow-doc-content {
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-secondary);
    }

    .flow-doc-empty {
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-muted);
        margin: 0;
        font-style: italic;
    }
</style>
