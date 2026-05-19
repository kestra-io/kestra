<template>
    <div class="ks-breadcrumb">
        <template v-if="showLeading">
            <RouterLink :to="leadingTo" class="ks-breadcrumb__leading" aria-label="Home">
                <img :src="monogram" class="ks-breadcrumb__monogram" alt="" />
            </RouterLink>
            <span class="ks-breadcrumb__separator">/</span>
        </template>

        <template v-for="(item, index) in visibleItems" :key="item.label">
            <div class="ks-breadcrumb__item">
                <KsDropdown
                    v-if="item.ellipsis"
                    trigger="click"
                    :showArrow="false"
                    size="large"
                >
                    <button class="ks-breadcrumb__ellipsis" type="button">...</button>
                    <template #dropdown>
                        <KsDropdownMenu>
                            <KsDropdownItem
                                v-for="(collapsed, i) in collapsedItems"
                                :key="i"
                                :disabled="collapsed.disabled"
                            >
                                <component
                                    :is="resolveItem(collapsed).tag"
                                    v-bind="resolveItem(collapsed).attrs"
                                >
                                    {{ collapsed.label }}
                                </component>
                            </KsDropdownItem>
                        </KsDropdownMenu>
                    </template>
                </KsDropdown>

                <component
                    v-else
                    :is="resolveItem(item).tag"
                    v-bind="resolveItem(item).attrs"
                    class="ks-breadcrumb__link"
                >
                    <component :is="mainIcon" v-if="index === 0 && mainIcon" class="ks-breadcrumb__icon" />
                    {{ item.label }}
                </component>
            </div>
            <span class="ks-breadcrumb__separator">/</span>
        </template>

        <h1 v-if="hasTitle" class="ks-breadcrumb__current">
            <component :is="mainIcon" v-if="titleHasIcon" class="ks-breadcrumb__icon" />
            <slot name="title">{{ title }}</slot>
        </h1>
    </div>
</template>

<script setup lang="ts">
    import {computed, type Component} from "vue"
    import {RouterLink} from "vue-router"
    import KsDropdown from "../KsDropdown/KsDropdown.vue"
    import KsDropdownMenu from "../KsDropdown/KsDropdownMenu.vue"
    import KsDropdownItem from "../KsDropdown/KsDropdownItem.vue"
    import type {KsBreadcrumbItem} from "./types"
    import monogram from "../../../assets/images/kestra-monogram.svg"

    type RouterLinkTo = InstanceType<typeof RouterLink>["$props"]["to"]

    const {items = [], title = "", mainIcon, showLeading = false, leadingTo = "/"} = defineProps<{
        items?: KsBreadcrumbItem[]
        title?: string
        mainIcon?: Component
        showLeading?: boolean
        leadingTo?: RouterLinkTo
    }>()

    const slots = defineSlots<{
        title?(): unknown
    }>()

    const COLLAPSE_THRESHOLD = 4

    type VisibleItem = KsBreadcrumbItem & {ellipsis?: boolean}

    const shouldCollapse = computed(() => items.length >= COLLAPSE_THRESHOLD)

    const visibleItems = computed<VisibleItem[]>(() =>
        shouldCollapse.value
            ? [items[0], {label: "...", ellipsis: true}, items[items.length - 1]]
            : items,
    )

    const collapsedItems = computed<KsBreadcrumbItem[]>(() =>
        shouldCollapse.value ? items.slice(1, items.length - 1) : [],
    )

    const hasTitle = computed(() => Boolean(slots.title) || title.length > 0)
    const titleHasIcon = computed(() => !visibleItems.value.length && Boolean(mainIcon))

    type Resolved = {tag: typeof RouterLink | "a" | "span"; attrs: Record<string, unknown>}

    function resolveItem(item: KsBreadcrumbItem): Resolved {
        if (item.disabled) return {tag: "span", attrs: {}}
        if (item.link) return {tag: RouterLink, attrs: {to: item.link}}
        if (item.onClick) return {
            tag: "a",
            attrs: {href: "#", onClick: (e: Event) => { e.preventDefault(); item.onClick?.() }},
        }
        return {tag: "span", attrs: {}}
    }

</script>

<style scoped lang="scss">
    .ks-breadcrumb {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        align-self: stretch;

        &__leading {
            display: inline-flex;
            align-items: center;
            padding: var(--ks-spacing-1) var(--ks-spacing-2);
        }

        &__monogram {
            width: var(--ks-icon-size-xl);
            height: var(--ks-icon-size-xl);
        }

        &__separator {
            font-size: var(--ks-font-size-sm);
            font-weight: var(--ks-font-weight-semibold);
            color: var(--ks-border-strong);
            user-select: none;
        }

        &__item {
            display: inline-flex;
            align-items: center;
            gap: var(--ks-spacing-2);
            padding: var(--ks-spacing-1) var(--ks-spacing-2);
            border-radius: var(--ks-radius-sm);
            color: var(--ks-text-secondary);
            transition: background-color 0.15s ease, color 0.15s ease;

            &:has(a, button):hover {
                background-color: var(--ks-bg-hover);
                color: var(--ks-text-primary);

                .ks-breadcrumb__icon {
                    color: var(--ks-icon-active);
                }
            }
        }

        &__link {
            display: inline-flex;
            align-items: center;
            gap: var(--ks-spacing-2);
            font-size: var(--ks-font-size-sm);
            font-weight: var(--ks-font-weight-regular);
            color: inherit;
            text-decoration: none;
            white-space: nowrap;
        }

        &__icon {
            display: inline-flex;
            align-items: center;
            font-size: var(--ks-font-size-lg);
            color: var(--ks-text-primary);

            :deep(svg) {
                stroke-width: 1.5;
            }
        }

        &__current {
            display: inline-flex;
            align-items: center;
            gap: var(--ks-spacing-2);
            margin: 0;
            padding: var(--ks-spacing-1) var(--ks-spacing-2);
            font-size: var(--ks-font-size-sm);
            font-weight: var(--ks-font-weight-semibold);
            color: var(--ks-text-primary);
            white-space: nowrap;

            .ks-breadcrumb__icon {
                color: var(--ks-text-primary);
            }
        }

        &__ellipsis {
            font-size: var(--ks-font-size-sm);
            color: var(--ks-text-primary);
            background: none;
            border: 0;
            padding: 0;
            cursor: pointer;

            &:hover {
                opacity: 0.8;
            }
        }
    }
</style>
