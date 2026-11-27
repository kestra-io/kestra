<template>
    <div class="plugin-catalog-table-plugin">
        <div class="plugin-catalog-table-logo">
            <TaskIcon
                :cls="group"
                :icons
                :loadIcon="pluginsStore.loadIcon"
                variable="--ks-black"
                onlyIcon
            />
        </div>
        <div class="plugin-catalog-table-name" data-test="plugin-catalog-table-name">
            <router-link v-if="route" :to="route">
                {{ title }}
            </router-link>
            <span v-else>{{ title }}</span>
            <span class="plugin-catalog-table-artifact">{{ name }}</span>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import type {RouteLocationRaw} from "vue-router"
    import {usePluginsStore} from "../../stores/plugins"
    import TaskIcon from "./TaskIcon.vue"

    defineProps<{
        title: string
        name?: string
        group?: string
        route?: RouteLocationRaw
    }>()

    const pluginsStore = usePluginsStore()

    const icons = computed(() => ({
        ...pluginsStore.icons,
        ...pluginsStore.groupIcons,
    }))
</script>

<style scoped>
    .plugin-catalog-table-plugin {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
    }

    .plugin-catalog-table-logo {
        flex-shrink: 0;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 2rem;
        height: 2rem;
        padding: var(--ks-spacing-1);
        background-color: var(--ks-bg-plugin-icon);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
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
</style>
