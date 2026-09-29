<template>
    <KsPopover
        v-if="rows.length"
        v-model:visible="visible"
        trigger="hover"
        placement="bottom-start"
        width="auto"
        :showArrow="false"
        :showAfter="120"
        :hideAfter="300"
        popperClass="ks-breadcrumb-popper"
    >
        <template #reference>
            <span class="segment" :class="{open: visible}" data-testid="breadcrumb-segment">
                <slot />
                <button
                    v-if="chevron"
                    type="button"
                    class="trigger"
                    data-testid="breadcrumb-menu-trigger"
                    :aria-label="ariaLabel"
                    :aria-expanded="visible"
                >
                    <ChevronDown class="chevron" />
                </button>
            </span>
        </template>
        <KsBreadcrumbColumn :entries="rows" :heading="heading" @select="visible = false" />
    </KsPopover>
    <slot v-else />
</template>

<script setup lang="ts">
    import {computed, ref, shallowRef, watch} from "vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import KsPopover from "../../Feedback/KsPopover.vue"
    import KsBreadcrumbColumn from "./KsBreadcrumbColumn.vue"
    import type {KsBreadcrumbItem, KsBreadcrumbLoader} from "./types"

    const {entries, load, heading, ariaLabel, chevron = true} = defineProps<{
        entries?: KsBreadcrumbItem[]
        load?: KsBreadcrumbLoader
        heading?: string
        ariaLabel: string
        chevron?: boolean
    }>()

    const visible = ref(false)
    const loaded = shallowRef<KsBreadcrumbItem[]>([])
    const rows = computed(() => entries ?? loaded.value)

    let latestLoad = 0

    // Fetched as soon as the level is known rather than on open, so a level with nothing to offer shows no chevron at all.
    watch(() => load, async (loader) => {
        const current = ++latestLoad
        loaded.value = []
        if (!loader) return

        const items = await loader().catch(() => [])
        if (current === latestLoad) loaded.value = items
    }, {immediate: true})
</script>

<style lang="scss">
    // The popper is the first column's card; the design system already gives it the border and background.
    .ks-breadcrumb-popper.kel-popover.kel-popper {
        min-width: 0;
        padding: 0;
        border-radius: var(--ks-radius-base);
        box-shadow: 0 8px 24px 0 var(--ks-shadow-elevated);
    }
</style>

<style scoped lang="scss">
    .segment {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-2);
    }

    .trigger {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        padding: 0 var(--ks-spacing-1);
        border: 0;
        border-radius: var(--ks-radius-sm);
        background: none;
        font: inherit;
        line-height: 1;
        color: var(--ks-icon-muted);
        cursor: pointer;

        &:hover,
        .segment.open & {
            background: var(--ks-bg-active);
            color: var(--ks-text-primary);
        }
    }

    .chevron {
        font-size: var(--ks-icon-size-sm);
    }
</style>
