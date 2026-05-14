<template>
    <div class="breadcrumb">
        <img :src="monogram" class="monogram" alt="Kestra" />
        <span class="separator">/</span>
        <KsBreadcrumb v-if="visibleItems.length" separator="/" class="breadcrumb-list">
            <KsBreadcrumbItem
                v-for="(item, index) in visibleItems"
                :key="item.label"
                :to="!item.ellipsis && item.link && !item.disabled ? (item.link as string | object) : undefined"
            >
                <KsDropdown v-if="item.ellipsis" trigger="click" :showArrow="false" size="large">
                    <button class="ellipsis-btn">
                        ...
                    </button>
                    <template #dropdown>
                        <KsDropdownMenu>
                            <KsDropdownItem
                                v-for="(collapsed, i) in collapsedItems"
                                :key="i"
                                :disabled="collapsed.disabled"
                            >
                                <RouterLink v-if="collapsed.link && !collapsed.disabled" :to="collapsed.link" class="breadcrumb-collapse-link">
                                    {{ collapsed.label }}
                                </RouterLink>
                                <span v-else>{{ collapsed.label }}</span>
                            </KsDropdownItem>
                        </KsDropdownMenu>
                    </template>
                </KsDropdown>

                <span v-else class="item" :class="{'item--with-icon': index === 0 && mainIcon}">
                    <component :is="mainIcon" v-if="index === 0 && mainIcon" class="main-icon" />
                    {{ item.label }}
                </span>
            </KsBreadcrumbItem>
        </KsBreadcrumb>
        <span v-if="visibleItems.length" class="separator">/</span>

        <h1 class="item item--last" :class="{'item--with-icon': visibleItems.length === 0 && mainIcon}">
            <component :is="mainIcon" v-if="visibleItems.length === 0 && mainIcon" class="main-icon" />
            <slot name="title">
                {{ title }}
            </slot>
        </h1>
    </div>
</template>

<script setup lang="ts">
    import {computed, type Component} from "vue"
    import {RouterLink} from "vue-router"
    import type {BreadcrumbItem} from "./breadcrumbTypes"
    import monogram from "../../assets/kestra-monogram.svg"

    const {items, title} = defineProps<{
        items: BreadcrumbItem[];
        title: string;
        mainIcon?: Component;
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
        gap: var(--ks-spacing-1);
        align-self: stretch;

        .monogram {
            width: var(--ks-icon-size-lg);
            height: var(--ks-icon-size-lg);
            flex-shrink: 0;
        }

        .separator {
            font-size: var(--ks-font-size-sm);
            color: var(--ks-text-dim);
            user-select: none;
            margin: 0 10px;
        }

        .breadcrumb-list {
            display: inline-flex;
            align-items: center;
        }

        .item {
            font-size: var(--ks-font-size-sm);
            font-weight: var(--ks-font-weight-medium);
            color: var(--ks-text-dim);
            text-decoration: none;
            white-space: nowrap;

            &--with-icon {
                display: inline-flex;
                align-items: center;
                gap: var(--ks-spacing-2);
            }

            &--last {
                font-size: var(--ks-font-size-base);
                font-weight: var(--ks-font-weight-semibold);
                color: var(--ks-text-primary);
                margin: 0;
            }
        }

        .main-icon {
            display: inline-flex;
            align-items: center;
            font-size: var(--ks-icon-size-base);
            color: var(--ks-text-primary);
        }

        .ellipsis-btn {
            font-size: var(--ks-font-size-sm);
            color: var(--ks-text-primary);
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
