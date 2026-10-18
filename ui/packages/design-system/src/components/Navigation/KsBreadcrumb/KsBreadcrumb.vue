<template>
    <div class="ks-breadcrumb">
        <template v-if="showLeading">
            <RouterLink :to="leadingTo" class="ks-breadcrumb__leading" aria-label="Home">
                <img :src="monogram" class="ks-breadcrumb__monogram" alt="" />
            </RouterLink>
            <span class="ks-breadcrumb__separator">/</span>
        </template>

        <template v-for="(item, index) in visibleItems" :key="`${index}-${item.label}`">
            <div class="ks-breadcrumb__item">
                <KsBreadcrumbMenu
                    v-if="item.ellipsis"
                    :entries="collapsedItems"
                    :chevron="false"
                    :ariaLabel="$t('breadcrumb_hidden')"
                >
                    <button type="button" class="ks-breadcrumb__link ks-breadcrumb__ellipsis" :aria-label="$t('breadcrumb_hidden')">...</button>
                </KsBreadcrumbMenu>
                <KsBreadcrumbMenu
                    v-else
                    :load="item.siblings ?? item.children"
                    :heading="$t('breadcrumb_in', {label: scopeOf(item)})"
                    :ariaLabel="$t('breadcrumb_siblings', {label: item.label})"
                >
                    <component
                        :is="resolveItem(item).tag"
                        v-bind="resolveItem(item).attrs"
                        class="ks-breadcrumb__link"
                    >
                        <component :is="mainIcon" v-if="index === 0 && mainIcon" class="ks-breadcrumb__icon" />
                        {{ item.label }}
                    </component>
                </KsBreadcrumbMenu>
            </div>
            <span class="ks-breadcrumb__separator">/</span>
        </template>

        <h1 v-if="hasTitle" class="ks-breadcrumb__current">
            <KsBreadcrumbMenu
                :load="titleSiblings"
                :heading="$t('breadcrumb_in', {label: scopeOf()})"
                :ariaLabel="$t('breadcrumb_siblings', {label: title})"
            >
                <component :is="mainIcon" v-if="titleHasIcon" class="ks-breadcrumb__icon" />
                <slot name="title">{{ title }}</slot>
            </KsBreadcrumbMenu>
        </h1>
    </div>
</template>

<script setup lang="ts">
    import {computed, type Component} from "vue"
    import {RouterLink} from "vue-router"
    import KsBreadcrumbMenu from "./KsBreadcrumbMenu.vue"
    import {resolveItem} from "./resolveItem"
    import type {KsBreadcrumbItem, KsBreadcrumbLoader} from "./types"
    import monogram from "../../../assets/images/kestra-monogram.svg"

    type RouterLinkTo = InstanceType<typeof RouterLink>["$props"]["to"]

    const {items = [], title = "", mainIcon, showLeading = false, leadingTo = "/", titleSiblings} = defineProps<{
        items?: KsBreadcrumbItem[]
        title?: string
        mainIcon?: Component
        showLeading?: boolean
        leadingTo?: RouterLinkTo
        titleSiblings?: KsBreadcrumbLoader
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

    // A siblings menu lists what sits under the previous item (the last one for the title), a children menu
    // what sits under the item itself.
    function scopeOf(item?: KsBreadcrumbItem): string {
        const owner = !item ? items[items.length - 1] : item.siblings ? items[items.indexOf(item) - 1] : item
        return owner?.scope ?? owner?.label ?? item?.label ?? title
    }
</script>

<style scoped lang="scss">
    .ks-breadcrumb {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        align-self: stretch;
        font-size: var(--ks-font-size-sm);

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
            font-weight: var(--ks-font-weight-regular);
            color: inherit;
            text-decoration: none;
            white-space: nowrap;
        }

        &__ellipsis {
            font-size: inherit;
            background: none;
            border: 0;
            padding: 0;
            cursor: pointer;
        }

        &__icon {
            display: inline-flex;
            align-items: center;
            font-size: var(--ks-font-size-lg);

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
            font-size: inherit;
            line-height: 1.5;
            font-weight: var(--ks-font-weight-semibold);
            color: var(--ks-text-primary);
            white-space: nowrap;

            .ks-breadcrumb__icon {
                color: var(--ks-text-primary);
            }
        }
    }
</style>
