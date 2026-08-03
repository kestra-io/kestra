<template>
    <div class="plugin-list-wrapper">
        <PluginList
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
</style>
