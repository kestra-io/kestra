<template>
    <KsForm class="other-panel" labelPosition="top" @submit.prevent>
        <KsFormItem :label="$t('recipe.other.search_label')">
            <KsInput
                v-model="searchQuery"
                :placeholder="$t('recipe.other.search_placeholder')"
                clearable
                data-test="recipe-trigger-search"
            />
        </KsFormItem>

        <KsAlert
            v-if="loadError"
            type="error"
            :closable="false"
            data-test="recipe-trigger-list-error"
        >
            {{ $t("recipe.other.load_error") }}
        </KsAlert>

        <div v-else-if="loading" class="trigger-list">
            <KsSkeleton v-for="i in 4" :key="i" height="3rem" />
        </div>

        <KsEmpty
            v-else-if="filteredTriggers.length === 0"
            :title="$t('recipe.other.no_results')"
        />

        <div v-else class="trigger-list" role="radiogroup" :aria-label="$t('recipe.other.search_label')" data-test="recipe-trigger-list">
            <SelectableTile
                v-for="trigger in filteredTriggers"
                :key="trigger.type"
                role="radio"
                :selected="recipe.otherTriggerType === trigger.type"
                :ariaLabel="trigger.name"
                @select="setOtherTriggerType(trigger.type)"
            >
                <TaskIcon :cls="trigger.type" :icons="pluginIcons" class="trigger-icon" />
                <div class="trigger-info">
                    <span class="trigger-name">{{ trigger.name }}</span>
                    <span v-if="trigger.description" class="trigger-desc">{{ trigger.description }}</span>
                </div>
                <KsIcon v-if="recipe.otherTriggerType === trigger.type" class="check-icon">
                    <Check />
                </KsIcon>
            </SelectableTile>
        </div>
    </KsForm>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref} from "vue"
    import SelectableTile from "../SelectableTile.vue"
    import TaskIcon from "../../../plugins/TaskIcon.vue"
    import {usePluginsStore} from "../../../../stores/plugins"
    import type {TriggerPluginDto} from "../../../../stores/plugins"
    import type {PluginIconMap} from "../../../../utils/pluginUtils"
    import type {RecipeState} from "../../../../composables/useFlowRecipe"
    import Check from "vue-material-design-icons/Check.vue"

    defineProps<{
        recipe: RecipeState
        setOtherTriggerType: (type: string) => void
    }>()

    const pluginsStore = usePluginsStore()
    const searchQuery = ref("")
    const triggers = ref<TriggerPluginDto[]>([])
    const loading = ref(true)
    const loadError = ref(false)
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
        } catch {
            loadError.value = true
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

    .trigger-icon {
        flex-shrink: 0;
        width: var(--ks-spacing-5);
        height: var(--ks-spacing-5);
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
