<template>
    <div v-if="taskType" class="task-icon-box">
        <TaskIcon :cls="taskType" onlyIcon :loadIcon="pluginsStore.loadIcon" />
    </div>
</template>

<script setup lang="ts">
    import TaskIcon from "../../plugins/TaskIcon.vue"
    import {usePluginsStore} from "../../../stores/plugins"

    defineProps<{
        taskType?: string;
    }>()

    const pluginsStore = usePluginsStore()
</script>

<style scoped>
    .task-icon-box {
        box-sizing: content-box;
        flex-shrink: 0;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 1.5rem;
        height: 1.5rem;
        padding: var(--ks-spacing-1);
        border: var(--ks-border-width-thin) solid var(--ks-border-default);
        border-radius: var(--ks-radius-sm);
        background: var(--ks-white);
    }
</style>
