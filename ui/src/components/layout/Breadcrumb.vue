<template>
    <div class="breadcrumb">
        <template v-for="item in visibleItems" :key="item.label">
            <el-dropdown v-if="item.ellipsis" trigger="click" :showArrow="false" size="large">
                <button class="ellipsis-btn">
                    ...
                </button>
                <template #dropdown>
                    <el-dropdown-menu>
                        <el-dropdown-item
                            v-for="(collapsed, i) in collapsedItems"
                            :key="i"
                            :disabled="collapsed.disabled"
                        >
                            <RouterLink v-if="collapsed.link && !collapsed.disabled" :to="collapsed.link" class="breadcrumb-collapse-link">
                                {{ collapsed.label }}
                            </RouterLink>
                            <span v-else>{{ collapsed.label }}</span>
                        </el-dropdown-item>
                    </el-dropdown-menu>
                </template>
            </el-dropdown>

            <RouterLink v-else-if="item.link && !item.disabled" class="item" :to="item.link">
                {{ item.label }}
            </RouterLink>

            <span v-else class="item">{{ item.label }}</span>
            <span class="separator">/</span>
        </template>

        <h1 class="item item--last">
            <slot name="title">
                {{ title }}
            </slot>
        </h1>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {RouterLink} from "vue-router"
    import type {BreadcrumbItem} from "./breadcrumbTypes"

    const {items, title} = defineProps<{
        items: BreadcrumbItem[];
        title: string;
    }>()

    const COLLAPSE_THRESHOLD = 4

    type VisibleItem = BreadcrumbItem & {ellipsis?: boolean};

    const shouldCollapse = computed(() => items.length >= COLLAPSE_THRESHOLD)

    const visibleItems = computed<VisibleItem[]>(() =>
        shouldCollapse.value
            ? [items[0], {label: "...", ellipsis: true}, items[items.length - 1]]
            : items,
    )

    const collapsedItems = computed<BreadcrumbItem[]>(() =>
        shouldCollapse.value ? items.slice(1, items.length - 1) : [],
    )
</script>

<style scoped lang="scss">
    .breadcrumb {
        display: flex;
        align-items: center;
        gap: 4px;
        align-self: stretch;

        .separator {
            font-size: var(--font-size-sm);
            color: var(--ks-content-tertiary);
            user-select: none;
        }

        .item {
            font-size: var(--font-size-sm);
            color: var(--ks-content-tertiary);
            text-decoration: none;
            white-space: nowrap;

            &--last {
                font-size: var(--font-size-base);
                font-weight: 700;
                color: var(--ks-content-primary);
                margin: 0;
            }
        }

        a.item:hover {
            color: var(--ks-content-primary);
        }

        .ellipsis-btn {
            font-size: var(--font-size-sm);
            color: var(--ks-content-primary);
            background: none;
            border: none;
            padding: 0;
            cursor: pointer;

            &:hover {
                opacity: 0.8;
            }
        }
    }

    :global(.breadcrumb-collapse-link) {
        display: block;
        color: inherit;
        text-decoration: none;
    }
</style>
