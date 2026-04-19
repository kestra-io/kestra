<template>
    <section class="category-section">
        <header class="category-header">
            <span class="category-pill">{{ title }}</span>
            <span class="category-description">{{ description }}</span>
        </header>

        <div v-if="triggers.length === 0" class="empty-row">
            {{ $t("triggers.add.category.empty") }}
        </div>

        <div v-else class="card-grid">
            <TriggerCatalogCard
                v-for="trigger in visibleTriggers"
                :key="trigger.type"
                :trigger="trigger"
                @add="$emit('add', trigger)"
            />
        </div>

        <button v-if="canCollapse" type="button" class="see-more-button" @click="expanded = !expanded">
            <ChevronUp v-if="expanded" class="chevron" />
            <ChevronDown v-else class="chevron" />
            <span>
                {{ expanded
                    ? $t("triggers.add.see_less")
                    : $t("triggers.add.see_more", {count: triggers.length - DEFAULT_VISIBLE_COUNT})
                }}
            </span>
        </button>
    </section>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue";
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue";

    import TriggerCatalogCard from "./TriggerCatalogCard.vue";
    import type {TriggerPluginDto} from "../../stores/plugins";

    const props = defineProps<{
        category: "core" | "realtime" | "app";
        title: string;
        description: string;
        triggers: TriggerPluginDto[];
        expandAll?: boolean;
    }>();

    defineEmits<{
        (e: "add", trigger: TriggerPluginDto): void;
    }>();

    const DEFAULT_VISIBLE_COUNT = 8;

    const expanded = ref(false);

    const canCollapse = computed(() =>
        !props.expandAll && props.triggers.length > DEFAULT_VISIBLE_COUNT
    );

    const visibleTriggers = computed(() => {
        if (props.expandAll || expanded.value || !canCollapse.value) {
            return props.triggers;
        }
        return props.triggers.slice(0, DEFAULT_VISIBLE_COUNT);
    });
</script>

<style scoped lang="scss">
    .category-section {
        display: flex;
        flex-direction: column;
        gap: 12px;
    }

    .category-header {
        display: flex;
        align-items: center;
        gap: 12px;
        flex-wrap: wrap;
    }

    .category-pill {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 4px 10px;
        border-radius: 999px;
        border: 1px solid var(--ks-border-primary, var(--bs-border-color));
        background: var(--ks-background-card, var(--bs-body-bg));
        font-size: 12px;
        font-weight: 600;
        color: var(--ks-content-primary, var(--bs-body-color));
    }

    .category-description {
        font-size: 13px;
        color: var(--ks-content-tertiary, var(--bs-gray-600));
    }

    .empty-row {
        padding: 24px 16px;
        color: var(--ks-content-tertiary, var(--bs-gray-600));
        font-size: 13px;
    }

    .card-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
        gap: 12px;
    }

    .see-more-button {
        align-self: flex-start;
        background: transparent;
        border: none;
        cursor: pointer;
        padding: 6px 8px;
        margin-left: -8px;
        display: inline-flex;
        align-items: center;
        gap: 6px;
        color: var(--ks-content-secondary, var(--bs-gray-600));
        font-size: 13px;
        font-weight: 500;

        &:hover {
            color: var(--ks-content-primary, var(--bs-body-color));
        }

        .chevron {
            display: inline-flex;
            font-size: 14px;
        }
    }
</style>
