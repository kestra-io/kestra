<template>
    <div class="breadcrumb">
        <template v-for="(item, index) in visibleItems" :key="index">
            <span v-if="index > 0" class="separator">/</span>

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

            <RouterLink
                v-else-if="item.link && !item.disabled && !item.last"
                class="item"
                :to="item.link"
            >
                {{ item.label }}
            </RouterLink>

            <span v-else :class="['item', {'item--last': item.last}]">
                <template v-if="item.last">
                    <slot name="title">{{ item.label }}</slot>
                </template>
                <template v-else>{{ item.label }}</template>
            </span>
        </template>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {RouterLink} from "vue-router";
    import type {BreadcrumbItem} from "./breadcrumbTypes";

    const props = defineProps<{
        items: BreadcrumbItem[];
        title: string;
    }>();

    const allItems = computed<(BreadcrumbItem & {last: boolean; ellipsis: false})[]>(() => [
        ...props.items.map(item => ({...item, last: false, ellipsis: false as const})),
        {label: props.title, last: true, ellipsis: false as const},
    ]);

    const COLLAPSE_THRESHOLD = 5;
    const shouldCollapse = computed(() => allItems.value.length >= COLLAPSE_THRESHOLD);

    type VisibleItem = BreadcrumbItem & {last: boolean; ellipsis: boolean};

    const visibleItems = computed<VisibleItem[]>(() => {
        const items = allItems.value;
        if (!shouldCollapse.value) {
            return items;
        }
        return [
            items[0],
            {label: "...", last: false, ellipsis: true},
            items[items.length - 2],
            items[items.length - 1],
        ];
    });

    const collapsedItems = computed(() => {
        if (!shouldCollapse.value) return [];
        const items = allItems.value;
        return items.slice(1, items.length - 2);
    });
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
