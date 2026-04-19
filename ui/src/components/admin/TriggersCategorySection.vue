<template>
    <section class="category-section">
        <header class="category-header">
            <div class="category-heading">
                <span :class="['category-badge', `category-badge--${category}`]">
                    {{ title }}
                </span>
                <p class="category-description">
                    {{ description }}
                </p>
            </div>
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

        <div v-if="canCollapse" class="see-more-row">
            <el-button link type="primary" @click="expanded = !expanded">
                {{ expanded
                    ? $t("triggers.add.see_less")
                    : $t("triggers.add.see_more", {count: triggers.length - DEFAULT_VISIBLE_ROWS * COLUMNS})
                }}
            </el-button>
        </div>
    </section>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";

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

    const COLUMNS = 4;
    const DEFAULT_VISIBLE_ROWS = 2;
    const DEFAULT_VISIBLE_COUNT = COLUMNS * DEFAULT_VISIBLE_ROWS;

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
        margin-bottom: 2.5rem;
    }

    .category-header {
        margin-bottom: 1rem;
    }

    .category-heading {
        display: flex;
        align-items: center;
        gap: 1rem;
        flex-wrap: wrap;
    }

    .category-badge {
        padding: .25rem .75rem;
        border-radius: 4px;
        font-size: .85rem;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: .05em;
        background: var(--bs-gray-200);
        color: var(--bs-gray-800);
    }

    .category-description {
        margin: 0;
        color: var(--bs-gray-600);
        font-size: .9rem;
    }

    .card-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
        gap: .75rem;
    }

    .empty-row {
        padding: 1rem;
        color: var(--bs-gray-600);
        text-align: center;
    }

    .see-more-row {
        margin-top: .75rem;
        text-align: center;
    }
</style>
