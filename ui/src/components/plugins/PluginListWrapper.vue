<template>
    <div class="plugin-list-wrapper">
        <div v-if="!pluginsData" class="loading-container">
            <KsSkeleton animated :rows="8" />
        </div>
        <PluginList
            v-else
            :plugins="pluginsData ?? []"
            :key="useMiscStore().theme"
        />
    </div>
</template>

<script setup lang="ts">
    import {onMounted, computed} from "vue"
    import {useMiscStore} from "override/stores/misc"
    import {usePluginsStore} from "../../stores/plugins"
    import PluginList from "./PluginList.vue"

    const pluginsStore = usePluginsStore()

    const pluginsData = computed(() => pluginsStore.plugins)

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
</style>
