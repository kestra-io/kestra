<template>
    <section class="category-section">
        <header class="category-header">
            <span class="category-pill">{{ title }}</span>
            <span class="category-description">{{ description }}</span>
        </header>

        <div v-if="triggers.length === 0" class="empty-row">
            {{ $t("triggers_add_category_empty") }}
        </div>

        <div v-else class="card-grid">
            <TriggerCatalogCard
                v-for="trigger in visibleTriggers"
                :key="trigger.type"
                :trigger="trigger"
                @add="$emit('add', trigger)"
            />
        </div>

        <KsButton
            v-if="canCollapse"
            type="primary"
            link
            class="see-more-button"
            :icon="expanded ? ChevronUp : ChevronDown"
            @click="expanded = !expanded"
        >
            {{ expanded
                ? $t("triggers_add_see_less")
                : $t("triggers_add_see_more", {count: triggers.length - DEFAULT_VISIBLE_COUNT})
            }}
        </KsButton>
    </section>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"

    import TriggerCatalogCard from "./TriggerCatalogCard.vue"
    import type {TriggerPluginDto} from "../../../stores/plugins"

    const props = defineProps<{
        title: string;
        description: string;
        triggers: TriggerPluginDto[];
        expandAll?: boolean;
    }>()

    defineEmits<{
        add: [trigger: TriggerPluginDto];
    }>()

    const DEFAULT_VISIBLE_COUNT = 8

    const expanded = ref(false)

    const canCollapse = computed(() =>
        !props.expandAll && props.triggers.length > DEFAULT_VISIBLE_COUNT,
    )

    const visibleTriggers = computed(() =>
        canCollapse.value && !expanded.value
            ? props.triggers.slice(0, DEFAULT_VISIBLE_COUNT)
            : props.triggers,
    )
</script>

<style scoped lang="scss">
    .category-section {
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
    }

    .category-header {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        flex-wrap: wrap;
    }

    .category-pill {
        display: inline-flex;
        align-items: center;
        gap: 0.375rem;
        padding: 0.25rem 0.625rem;
        border-radius: 62.5rem;
        border: 1px solid var(--ks-border-primary);
        background: var(--ks-background-card);
        font-size: 0.75rem;
        font-weight: 600;
        color: var(--ks-content-primary);
    }

    .category-description {
        font-size: 0.8125rem;
        color: var(--ks-content-tertiary);
    }

    .empty-row {
        padding: 1.5rem 1rem;
        color: var(--ks-content-tertiary);
        font-size: 0.8125rem;
    }

    .card-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
        gap: 0.75rem;
    }

    .see-more-button {
        align-self: flex-start;
        margin-left: -0.5rem;
        font-size: 0.8125rem;
        font-weight: 500;
        color: var(--ks-content-primary);

        &:hover {
            color: var(--ks-content-tertiary);
        }
    }
</style>
