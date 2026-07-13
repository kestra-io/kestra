<template>
    <div class="other-panel">
        <KsFormItem :label="$t('recipe.other.search_label')">
            <KsInput
                v-model="searchQuery"
                :placeholder="$t('recipe.other.search_placeholder')"
                clearable
                data-test="recipe-trigger-search"
            />
        </KsFormItem>

        <div v-if="loading" class="trigger-list">
            <KsSkeleton v-for="i in 4" :key="i" height="3rem" />
        </div>

        <KsEmpty
            v-else-if="filteredTriggers.length === 0"
            :title="$t('recipe.other.no_results')"
        />

        <div v-else class="trigger-list" data-test="recipe-trigger-list">
            <div
                v-for="trigger in filteredTriggers"
                :key="trigger.type"
                class="trigger-row"
                :class="{selected: recipe.otherTriggerType === trigger.type}"
                role="radio"
                :aria-checked="recipe.otherTriggerType === trigger.type"
                tabindex="0"
                @click="setOtherTriggerType(trigger.type)"
                @keydown.enter="setOtherTriggerType(trigger.type)"
                @keydown.space.prevent="setOtherTriggerType(trigger.type)"
            >
                <TaskIcon :cls="trigger.type" :icons="pluginIcons" class="trigger-icon" />
                <div class="trigger-info">
                    <span class="trigger-name">{{ trigger.name }}</span>
                    <span v-if="trigger.description" class="trigger-desc">{{ trigger.description }}</span>
                </div>
                <KsIcon v-if="recipe.otherTriggerType === trigger.type" class="check-icon">
                    <Check />
                </KsIcon>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref} from "vue"
    import TaskIcon from "../../../plugins/TaskIcon.vue"
    import {usePluginsStore} from "../../../../stores/plugins"
    import type {TriggerPluginDto} from "../../../../stores/plugins"
    import type {PluginIconMap} from "../../../../utils/pluginUtils"
    import type {RecipeState} from "../../../../composables/useFlowRecipe"
    import Check from "vue-material-design-icons/Check.vue"

    const props = defineProps<{
        recipe: RecipeState
        setOtherTriggerType: (type: string) => void
    }>()

    const pluginsStore = usePluginsStore()
    const searchQuery = ref("")
    const triggers = ref<TriggerPluginDto[]>([])
    const loading = ref(true)
    const pluginIcons = ref<PluginIconMap>({})

    const filteredTriggers = computed(() => {
        const q = searchQuery.value.toLowerCase()
        if (!q) return triggers.value
        return triggers.value.filter(
            t => t.name.toLowerCase().includes(q) || t.type.toLowerCase().includes(q),
        )
    })

    onMounted(async () => {
        try {
            const [triggerData, icons] = await Promise.all([
                pluginsStore.listTriggers(),
                pluginsStore.ensureGroupIcons(),
            ])
            triggers.value = triggerData.filter(t => !t.deprecated && !t.ee)
            pluginIcons.value = icons ?? {}
        } finally {
            loading.value = false
        }
    })
</script>

<style scoped lang="scss">
    .other-panel {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-3);
    }

    .trigger-list {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        max-height: 16rem;
        overflow-y: auto;
    }

    .trigger-row {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        cursor: pointer;
        transition: border-color 0.15s, background-color 0.15s;

        &:hover {
            border-color: var(--ks-border-strong);
            background-color: var(--ks-bg-hover);
        }

        &.selected {
            border-color: var(--ks-border-focus);
            background-color: var(--ks-bg-tag-active);
        }
    }

    .trigger-icon {
        flex-shrink: 0;
        width: 1.5rem;
        height: 1.5rem;
    }

    .trigger-info {
        flex: 1;
        min-width: 0;
    }

    .trigger-name {
        display: block;
        font-weight: var(--ks-font-weight-medium);
        font-size: var(--ks-font-size-sm);
    }

    .trigger-desc {
        display: block;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-secondary);
    }

    .check-icon {
        color: var(--ks-text-link);
        flex-shrink: 0;
    }
</style>
