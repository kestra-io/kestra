<template>
    <span class="asset-tools" :class="{compact}" data-test="asset-tools">
        <template v-for="tool in tools" :key="tool">
            <KsTooltip v-if="compact" :content="tool" placement="top">
                <span class="asset-tool" data-test="asset-tool">
                    <TaskIcon
                        v-if="iconGroup(tool)"
                        :cls="iconGroup(tool)"
                        :icons="pluginsStore.groupIcons"
                        onlyIcon
                        class="asset-tool-icon"
                    />
                    <KsTag v-else size="small">{{ tool }}</KsTag>
                </span>
            </KsTooltip>
            <KsTag
                v-else
                size="small"
                :closable="removable"
                class="asset-tool"
                data-test="asset-tool"
                @close="emit('remove', tool)"
            >
                <span class="asset-tool-label">
                    <TaskIcon
                        v-if="iconGroup(tool)"
                        :cls="iconGroup(tool)"
                        :icons="pluginsStore.groupIcons"
                        onlyIcon
                        class="asset-tool-icon"
                    />
                    {{ tool }}
                </span>
            </KsTag>
        </template>
    </span>
</template>

<script setup lang="ts">
    import {onMounted} from "vue"
    import TaskIcon from "../plugins/TaskIcon.vue"
    import {usePluginsStore} from "../../stores/plugins"
    import {toolIconGroup} from "./assetTools"

    defineProps<{
        tools: string[];
        compact?: boolean;
        removable?: boolean;
    }>()

    const emit = defineEmits<{
        remove: [tool: string];
    }>()

    const pluginsStore = usePluginsStore()

    const iconGroup = (tool: string) => toolIconGroup(tool, pluginsStore.groupIcons)

    // Without the icons each tool still shows as its name, so a failed load changes nothing worth reporting.
    onMounted(() => pluginsStore.ensureGroupIcons().catch(() => undefined))
</script>

<style scoped>
    .asset-tools {
        display: inline-flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--ks-spacing-1);
    }

    .asset-tools.compact {
        flex-wrap: nowrap;
    }

    .asset-tool,
    .asset-tool-label {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
    }

    .asset-tool-icon {
        width: var(--ks-spacing-4);
        height: var(--ks-spacing-4);
    }
</style>
