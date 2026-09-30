<template>
    <div class="column">
        <div class="list">
            <div v-if="heading" class="heading">{{ heading }}</div>
            <component
                :is="resolveItem(entry).tag"
                v-for="(entry, index) in rows"
                :key="index"
                v-bind="resolveItem(entry).attrs"
                class="entry"
                :class="{open: entry === opened, current: entry.current}"
                data-testid="breadcrumb-entry"
                :title="entry.tooltip"
                @mouseenter="reveal(entry, $event)"
                @focus="reveal(entry, $event)"
                @click="emit('select', entry)"
            >
                <component :is="entry.icon" v-if="entry.icon" class="icon" />
                <span class="label">{{ entry.label }}</span>
                <ChevronRight v-if="entry.children" class="arrow" />
            </component>
            <div v-if="!loading && rows.length === 0" class="empty">{{ $t("breadcrumb_empty") }}</div>
        </div>
        <KsBreadcrumbColumn
            v-if="opened?.children"
            :load="opened.children"
            class="flyout"
            :style="{'--row-top': `${rowTop}px`}"
            @select="emit('select', $event)"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, shallowRef, watch} from "vue"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"
    import {resolveItem} from "./resolveItem"
    import type {KsBreadcrumbItem, KsBreadcrumbLoader} from "./types"

    const {entries, load, heading} = defineProps<{
        entries?: KsBreadcrumbItem[]
        load?: KsBreadcrumbLoader
        heading?: string
    }>()

    const emit = defineEmits<{
        select: [entry: KsBreadcrumbItem]
    }>()

    // No mask while loading: a fly-out is expected to resolve within a tick, and a mask over an empty list
    // flashed a scrollbar. Shallow refs, since an item may carry an icon component that must stay raw.
    const loading = ref(false)
    const loaded = shallowRef<KsBreadcrumbItem[]>([])
    const opened = shallowRef<KsBreadcrumbItem>()
    const rowTop = ref(0)
    const rows = computed(() => entries ?? loaded.value)

    let latestLoad = 0

    watch(() => load, async (loader) => {
        const current = ++latestLoad
        opened.value = undefined
        loaded.value = []
        if (!loader) return

        loading.value = true
        try {
            const items = await loader()
            if (current === latestLoad) loaded.value = items
        } finally {
            if (current === latestLoad) loading.value = false
        }
    }, {immediate: true})

    // The fly-out opens level with its row, so reaching it is a straight move right that crosses no other row.
    function reveal(entry: KsBreadcrumbItem, event: Event) {
        const row = event.currentTarget as HTMLElement
        rowTop.value = row.offsetTop - (row.parentElement?.scrollTop ?? 0)
        opened.value = entry.children ? entry : undefined
    }
</script>

<style scoped lang="scss">
    .column {
        position: relative;
        font-size: var(--ks-font-size-sm);
    }

    .list {
        min-width: 12rem;
        max-width: 20rem;
        max-height: 50vh;
        overflow-y: auto;
        padding: var(--ks-spacing-1);
    }

    .flyout .list {
        background: var(--ks-bg-elevated);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        box-shadow: 0 8px 24px 0 var(--ks-shadow-elevated);
    }

    .heading {
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        font-size: var(--ks-font-size-xs);
        font-weight: var(--ks-font-weight-semibold);
        letter-spacing: 0.06em;
        text-transform: uppercase;
        color: var(--ks-text-dim);
    }

    .entry {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        border-radius: var(--ks-radius-xs);
        color: var(--ks-text-primary);
        text-decoration: none;
        white-space: nowrap;
        cursor: pointer;

        &:hover {
            background: var(--ks-bg-hover-elevated);
        }

        &.open {
            background: var(--ks-bg-active);
            color: var(--ks-text-link);
        }

        &.current {
            font-weight: var(--ks-font-weight-semibold);
        }
    }

    .icon,
    .arrow {
        display: inline-flex;
        color: var(--ks-icon-muted);
    }

    .label {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    .arrow {
        margin-left: var(--ks-spacing-2);
    }

    .empty {
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        color: var(--ks-text-dim);
    }

    .flyout {
        position: absolute;
        top: calc(var(--row-top, 0px) - var(--ks-spacing-1) - 1px);
        left: calc(100% + var(--ks-spacing-1));
    }
</style>
