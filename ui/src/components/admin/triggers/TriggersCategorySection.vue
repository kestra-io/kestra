<template>
    <section class="category-section">
        <div v-if="triggers.length === 0" class="empty-row">
            {{ $t("triggers_add_category_empty") }}
        </div>

        <div v-else class="card-grid">
            <TriggerCatalogCard
                v-for="trigger in triggers"
                :key="trigger.type"
                :trigger="trigger"
                @add="$emit('add', trigger)"
            />
        </div>
    </section>
</template>

<script setup lang="ts">
    import TriggerCatalogCard from "./TriggerCatalogCard.vue"
    import type {TriggerPluginDto} from "../../../stores/plugins"

    defineProps<{
        triggers: TriggerPluginDto[];
    }>()

    defineEmits<{
        add: [trigger: TriggerPluginDto];
    }>()
</script>

<style scoped lang="scss">
    .category-section {
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
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
</style>
