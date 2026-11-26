<template>
    <div class="column">
        <div class="list">
            <div v-if="heading" class="heading">{{ heading }}</div>
            <template v-for="(entry, index) in rows" :key="index">
                <div v-if="entry.divided && index > 0" class="divider" role="separator" data-testid="breadcrumb-divider" />
                <component
                    :is="resolveItem(entry).tag"
                    v-bind="resolveItem(entry).attrs"
                    class="entry"
                    :class="{open: entry === opened, current: entry.current}"
                    data-testid="breadcrumb-entry"
                    :title="entry.tooltip"
                    @mouseenter="revealSoon(entry, $event)"
                    @mouseleave="cancelReveal"
                    @focus="reveal(entry, $event.currentTarget as HTMLElement)"
                    @click="emit('select', entry)"
                >
                    <component :is="entry.icon" v-if="entry.icon" class="icon" />
                    <span class="label">{{ entry.label }}</span>
                    <ChevronRight v-if="entry.children" class="arrow" />
                </component>
            </template>
            <div v-if="loading" class="loading" data-testid="breadcrumb-loading">{{ $t("breadcrumb_loading") }}</div>
            <div v-else-if="rows.length === 0" class="empty">{{ $t("breadcrumb_empty") }}</div>
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
    import {computed, onBeforeUnmount, ref, shallowRef, watch} from "vue"
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

    // No mask while loading: a mask over an empty list flashed a scrollbar, so a slow loader gets a line of text.
    // Shallow refs, since an item may carry an icon component that must stay raw.
    const loading = ref(false)
    const loaded = shallowRef<KsBreadcrumbItem[]>([])
    const opened = shallowRef<KsBreadcrumbItem>()
    const rowTop = ref(0)
    const rows = computed(() => entries ?? loaded.value)

    let latestLoad = 0
    let revealTimer: ReturnType<typeof setTimeout> | undefined

    watch(() => load, async (loader) => {
        const current = ++latestLoad
        cancelReveal()
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
    function reveal(entry: KsBreadcrumbItem, row: HTMLElement) {
        rowTop.value = row.offsetTop - (row.parentElement?.scrollTop ?? 0)
        opened.value = entry.children ? entry : undefined
    }

    // A row the pointer only crosses opens nothing, so sweeping down a list does not run every row's loader.
    function revealSoon(entry: KsBreadcrumbItem, event: Event) {
        const row = event.currentTarget as HTMLElement
        cancelReveal()
        revealTimer = setTimeout(() => reveal(entry, row), 150)
    }

    function cancelReveal() {
        clearTimeout(revealTimer)
    }

    onBeforeUnmount(cancelReveal)
</script>

<style scoped>
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
        scrollbar-width: thin;
        scrollbar-color: transparent transparent;

        &:hover {
            scrollbar-color: var(--ks-border-subtle) transparent;
        }
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

    .divider {
        height: 1px;
        margin: var(--ks-spacing-1) 0;
        background: var(--ks-border-default);
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

    .empty,
    .loading {
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        color: var(--ks-text-dim);
    }

    .loading {
        opacity: 0;
        animation: appear 0.1s 0.3s forwards;
    }

    @keyframes appear {
        to {
            opacity: 1;
        }
    }

    .flyout {
        position: absolute;
        top: calc(var(--row-top, 0px) - var(--ks-spacing-1) - 1px);
        left: calc(100% + var(--ks-spacing-1));
    }
</style>
