<template>
    <div>
        <teleport to="body">
            <div v-if="isOpen" class="search-overlay" :style="{zIndex: topLayer}" @click="closeSearch">
                <div class="search-modal" role="dialog" aria-modal="true" @click.stop>
                    <div class="search-container" :aria-label="$t('jump to...')">
                        <KsSearch
                            ref="searchInput"
                            v-model="query"
                            :placeholder="$t('jump to...')"
                            clearable
                            autofocus
                            @keydown.esc="closeSearch"
                            @keydown="onInputKeydown"
                        >
                            <template v-if="scopePrefix" #prefix>
                                <Magnify />
                                <span class="scope-prefix">{{ scopePrefix }}</span>
                            </template>
                            <template v-if="!query" #suffix>
                                <span class="d-none d-sm-block">
                                    <kbd>ESC</kbd> {{ $t("to close") }}
                                </span>
                            </template>
                        </KsSearch>

                        <div class="results" role="listbox">
                            <KsScrollbar v-if="hasResultRows" class="results-scroll">
                                <ul id="global-search-listbox" class="results-list">
                                    <template v-for="(item, index) in results" :key="itemKey(item, index)">
                                        <li v-if="headingFor(index)" class="result-heading" role="presentation">
                                            <span>{{ headingLabel(item) }}</span>
                                            <span class="result-count">{{ groupCount(item) }}</span>
                                        </li>
                                        <li
                                            :id="`global-search-option-${index}`"
                                            class="result-item"
                                            :class="{active: index === activeIndex}"
                                            role="option"
                                            :aria-selected="index === activeIndex"
                                            @mouseenter="activeIndex = index"
                                        >
                                            <div class="result-row d-flex gap-2">
                                                <component
                                                    :is="item.kind === 'link' ? 'router-link' : 'button'"
                                                    v-bind="item.kind === 'link' ? {to: item.href} : {type: 'button'}"
                                                    class="result-link d-flex gap-2"
                                                    @click="onItemClick(item)"
                                                >
                                                    <div class="result-title d-flex gap-2 nav-item-title">
                                                        <component v-if="item.icon?.element" :is="{...item.icon.element}" class="align-middle" />
                                                        <span v-if="item.parentTitle" class="result-parent">
                                                            <template v-for="(part, partIndex) in highlightMatch(item.parentTitle, query)" :key="partIndex">
                                                                <mark v-if="part.match" class="result-match">{{ part.text }}</mark>
                                                                <template v-else>{{ part.text }}</template>
                                                            </template>
                                                        </span>
                                                        <span v-if="item.parentTitle" class="result-separator">/</span>
                                                        <span class="result-leaf">
                                                            <template v-for="(part, partIndex) in highlightMatch(item.title, query)" :key="partIndex">
                                                                <mark v-if="part.match" class="result-match">{{ part.text }}</mark>
                                                                <template v-else>{{ part.text }}</template>
                                                            </template>
                                                        </span>
                                                        <span v-if="item.namespace" class="result-namespace">
                                                            <template v-for="(part, partIndex) in highlightMatch(item.namespace, query)" :key="partIndex">
                                                                <mark v-if="part.match" class="result-match">{{ part.text }}</mark>
                                                                <template v-else>{{ part.text }}</template>
                                                            </template>
                                                        </span>
                                                    </div>
                                                    <span
                                                        v-if="index === activeIndex && item.executionsHref"
                                                        class="result-hint d-none d-sm-flex align-items-center"
                                                    >
                                                        <kbd>↵</kbd> {{ $t("global_search.open_flow") }}
                                                    </span>
                                                    <span
                                                        v-else-if="index === activeIndex"
                                                        class="result-hint d-none d-sm-flex align-items-center"
                                                    >
                                                        <span>{{ $t("jump to") }}</span>
                                                    </span>
                                                </component>
                                                <KsButton
                                                    v-if="index === activeIndex && item.executionsHref"
                                                    link
                                                    size="small"
                                                    class="result-action d-none d-sm-inline-flex"
                                                    @click.stop="openExecutions(item)"
                                                >
                                                    <kbd>→</kbd> {{ $t("executions") }}
                                                </KsButton>
                                            </div>
                                        </li>
                                    </template>
                                    <li v-if="showEntityHints && entitiesLoading" class="result-status" role="status">
                                        {{ $t("loading") }}
                                    </li>
                                    <li v-else-if="showEntityHints && entitiesError" class="result-status" role="status">
                                        {{ $t("global_search.search_failed") }}
                                    </li>
                                </ul>
                            </KsScrollbar>
                            <div v-else class="empty">
                                {{ $t("no_results_found") }}
                            </div>
                        </div>
                        <div v-if="showEntityHints" class="search-footer">
                            <span class="search-footer-keys">
                                <kbd>↑</kbd><kbd>↓</kbd> {{ $t("global_search.move") }}
                                <kbd>↵</kbd> {{ $t("open") }}
                                <kbd>→</kbd> {{ $t("global_search.flow_executions") }}
                            </span>
                            <span>{{ $t("global_search.sources") }}</span>
                        </div>
                    </div>
                </div>
            </div>
        </teleport>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, onUnmounted, nextTick, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useDebounceFn} from "@vueuse/core"
    import {useTopLayer, KsSearch} from "@kestra-io/design-system"
    import {useRoute, useRouter} from "vue-router"
    import {useLeftMenu} from "override/components/useLeftMenu"
    import type {MenuItem} from "override/components/useLeftMenu"
    import {useAuthStore} from "override/stores/auth"
    import * as FlowsAPI from "@kestra-io/kestra-sdk/flows"
    import * as NamespaceAPI from "@kestra-io/kestra-sdk/namespaces"
    import action from "../../models/action"
    import resource from "../../models/resource"
    import {isFlowTabAllowed} from "../flows/flowTabs"
    import Magnify from "vue-material-design-icons/Magnify.vue"
    import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue"
    import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue"
    import {
        flowOrNamespaceFilter,
        highlightMatch,
        namespaceFilter,
        paletteEntities,
        PALETTE_ENTITY_LIMIT,
        type PaletteDestination,
        type PaletteEntity,
    } from "./globalSearchEntities"

    const route = useRoute()
    const router = useRouter()
    const authStore = useAuthStore()
    const {t} = useI18n()
    const {menu} = useLeftMenu()

    const ENTITY_ICON = {
        flow: FileDocumentOutline,
        namespace: FolderOpenOutline,
    }

    type SearchItem = {
        kind: "link" | "scope";
        destination?: PaletteDestination | "menu";
        title: MenuItem["title"];
        parentTitle?: MenuItem["title"];
        namespace?: string;
        href?: NonNullable<MenuItem["href"]>;
        executionsHref?: NonNullable<MenuItem["href"]>;
        icon?: MenuItem["icon"];
        children?: MenuItem[];
        depth: number;
        order: number;
    };

    type ScopeNode = {
        title: string;
        items: MenuItem[];
    };

    const query = ref("")
    const entities = ref<PaletteEntity[]>([])
    const entityTotals = ref({flow: 0, namespace: 0})
    const entitiesLoading = ref(false)
    const entitiesError = ref(false)
    let searchTicket = 0
    const isOpen = ref(false)
    const topLayer = useTopLayer()
    const searchInput = ref<InstanceType<typeof KsSearch> | null>(null)
    const activeIndex = ref(0)
    const activeKey = ref<string | null>(null)
    const scopeStack = ref<ScopeNode[]>([])

    const scopePrefix = computed(() => {
        if (scopeStack.value.length === 0) {
            return ""
        }

        return `${scopeStack.value.map(s => s.title).join(" / ")} /`
    })

    const buildEntries = (items: MenuItem[], ancestors: string[], depth: number, startOrder: {value: number}): SearchItem[] => {
        const entries: SearchItem[] = []

        for (const item of items) {
            if (item.hidden) {
                continue
            }

            const hasChildren = Boolean(item.child && item.child.length > 0)
            const parentTitle = ancestors.length > 0 ? ancestors.join(" / ") : undefined
            const icon = item.icon

            // Always include a "scope" entry for any item that has children (even if it has no href),
            // so sections like "Blueprints" can be selected and scoped into.
            if (hasChildren) {
                entries.push({
                    kind: "scope",
                    title: item.title,
                    parentTitle,
                    href: item.href,
                    icon,
                    children: item.child,
                    depth,
                    order: startOrder.value++,
                })
            } else if (item.href) {
                entries.push({
                    kind: "link",
                    title: item.title,
                    parentTitle,
                    href: item.href,
                    icon,
                    depth,
                    order: startOrder.value++,
                })
            }

            // Include descendants for search (hierarchy preserved via parentTitle/depth).
            if (hasChildren) {
                entries.push(...buildEntries(item.child!, [...ancestors, item.title], depth + 1, startOrder))
            }
        }

        return entries
    }

    const navItems = computed(() => {
        if (!isOpen.value) {
            return []
        }

        const root = scopeStack.value.length > 0 ? scopeStack.value[scopeStack.value.length - 1].items : menu.value
        const order = {value: 0}
        // When scoped, we treat the scope root as depth 0 for ordering.
        return buildEntries(root, [], 0, order)
    })

    const menuResults = computed(() => {
        const q = query.value.trim().toLowerCase()
        const matches = (item: SearchItem) => {
            const haystack = [item.parentTitle, item.title].filter(Boolean).join(" ").toLowerCase()
            return haystack.includes(q)
        }

        const filtered = q ? navItems.value.filter(matches) : navItems.value

        // Prefer items closest to the current root (depth 0 first), while preserving menu order.
        return [...filtered].sort((a, b) => (a.depth - b.depth) || (a.order - b.order))
    })

    const entityResults = computed((): SearchItem[] => entities.value.map(entity => ({
        kind: "link",
        destination: entity.destination,
        title: entity.title,
        namespace: entity.namespace,
        href: entity.href,
        executionsHref: entity.executionsHref,
        icon: {element: ENTITY_ICON[entity.destination]},
        depth: 0,
        order: 0,
    })))

    const showEntityHints = computed(() => query.value.trim().length > 0 && scopeStack.value.length === 0)

    const results = computed(() => {
        const menu = showEntityHints.value
            ? menuResults.value.map(item => ({...item, destination: "menu" as const}))
            : menuResults.value
        return showEntityHints.value ? [...entityResults.value, ...menu] : menu
    })

    const hasResultRows = computed(() =>
        results.value.length > 0 || (showEntityHints.value && (entitiesLoading.value || entitiesError.value)),
    )

    const headingFor = (index: number): boolean => {
        if (!showEntityHints.value) {
            return false
        }
        const item = results.value[index]
        return item?.destination !== results.value[index - 1]?.destination
    }

    const headingLabel = (item: SearchItem): string => {
        if (item.destination === "flow") return t("flows")
        if (item.destination === "namespace") return t("namespaces")
        return t("global_search.menu")
    }

    const groupCount = (item: SearchItem): number => {
        if (item.destination === "flow" || item.destination === "namespace") {
            return entityTotals.value[item.destination]
        }
        return results.value.filter(candidate => candidate.destination === item.destination).length
    }

    const openExecutions = (item: SearchItem) => {
        if (!item.executionsHref) {
            return
        }
        router.push(item.executionsHref)
        closeSearch()
    }

    const loadEntities = useDebounceFn(async (q: string, ticket: number) => {
        if (!isOpen.value || ticket !== searchTicket) {
            return
        }
        const tenant = route.params.tenant
        const tenantId = Array.isArray(tenant) ? tenant[0] : tenant
        const user = authStore.user
        const canListFlows = !!user?.hasAnyActionOnAnyNamespace(resource.FLOW, action.LIST)
        const canListNamespaces = !!user?.hasAnyActionOnAnyNamespace(resource.NAMESPACE, action.LIST)
        const [flowsOutcome, namespacesOutcome] = await Promise.allSettled([
            canListFlows
                ? FlowsAPI.searchFlows({
                    page: 1,
                    size: PALETTE_ENTITY_LIMIT,
                    filters: [flowOrNamespaceFilter(q)],
                })
                : Promise.resolve(undefined),
            canListNamespaces
                ? NamespaceAPI.searchNamespaces({
                    page: 1,
                    size: PALETTE_ENTITY_LIMIT,
                    filters: [namespaceFilter(q)],
                })
                : Promise.resolve(undefined),
        ])
        if (!isOpen.value || ticket !== searchTicket) {
            return
        }
        const flows = flowsOutcome.status === "fulfilled" ? flowsOutcome.value : undefined
        const namespaces = namespacesOutcome.status === "fulfilled" ? namespacesOutcome.value : undefined
        entitiesError.value = (canListFlows && flowsOutcome.status === "rejected")
            || (canListNamespaces && namespacesOutcome.status === "rejected")
        entityTotals.value = {
            flow: flows?.total ?? 0,
            namespace: namespaces?.total ?? 0,
        }
        entities.value = paletteEntities(
            flows?.results ?? [],
            namespaces?.results ?? [],
            tenantId || undefined,
            namespace => isFlowTabAllowed("executions", {user, namespace}),
        )
        entitiesLoading.value = false
    }, 200)

    const isEditorTarget = (target: EventTarget | null) => {
        const el = target as HTMLElement | null
        return Boolean(el?.closest?.(".monaco-editor"))
    }

    const keyDown = (e: KeyboardEvent) => {
        if ((e.ctrlKey || e.metaKey) && !e.shiftKey && !e.altKey && e.key.toLowerCase() === "k") {
            if (isEditorTarget(e.target)) {
                return
            }
            e.preventDefault()
            openSearch()
            return
        }

        if (e.key === "Escape" && isOpen.value) {
            e.preventDefault()
            closeSearch()
        }
    }

    const openSearch = () => {
        isOpen.value = true
        activeIndex.value = 0
        activeKey.value = null
        nextTick(() => {
            searchInput.value?.focus?.()
        })
    }

    const closeSearch = () => {
        searchTicket++
        isOpen.value = false
        query.value = ""
        entities.value = []
        entityTotals.value = {flow: 0, namespace: 0}
        entitiesLoading.value = false
        entitiesError.value = false
        activeIndex.value = 0
        activeKey.value = null
        scopeStack.value = []
    }

    const itemKey = (item: SearchItem, index: number): string => {
        if (item.destination === "flow") {
            return `flow:${item.namespace ?? ""}:${item.title}`
        }
        if (item.destination === "namespace") {
            return `namespace:${item.title}`
        }

        const href = item.href
        if (typeof href === "string") {
            return href
        }
        if (typeof href === "object" && href !== null) {
            if ("path" in href && typeof href.path === "string") {
                return href.path
            }
            if ("name" in href && href.name != null) {
                const params = "params" in href && href.params && typeof href.params === "object" ? href.params : undefined
                const namespace = params && "namespace" in params ? String(params.namespace ?? "") : ""
                const id = params && "id" in params ? String(params.id ?? "") : ""
                return `name:${String(href.name)}:${namespace}:${id || item.title}`
            }
        }

        return `${item.kind}:${item.destination ?? "menu"}:${item.namespace ?? item.parentTitle ?? ""}:${item.title}:${index}`
    }

    const enterScope = (item: SearchItem) => {
        if (!item.children || item.children.length === 0) {
            return
        }

        scopeStack.value = [...scopeStack.value, {title: item.title, items: item.children}]
        query.value = ""
        activeIndex.value = 0
        activeKey.value = null
        nextTick(() => searchInput.value?.focus?.())
    }

    const onItemClick = (item: SearchItem) => {
        if (item.kind === "scope") {
            enterScope(item)
            return
        }

        closeSearch()
    }

    const scrollActiveOptionIntoView = () => {
        nextTick(() => {
            const el = document.getElementById(`global-search-option-${activeIndex.value}`)
            el?.scrollIntoView({block: "nearest"})
        })
    }

    const onInputKeydown = (e: KeyboardEvent) => {
        if (e.key === "Backspace" && query.value.length === 0 && scopeStack.value.length > 0) {
            e.preventDefault()
            scopeStack.value = scopeStack.value.slice(0, -1)
            activeIndex.value = 0
            return
        }

        if (results.value.length === 0) {
            return
        }

        if (e.key === "Tab") {
            const activeItem = results.value[activeIndex.value]
            if (activeItem?.kind === "scope") {
                e.preventDefault()
                enterScope(activeItem)
                return
            }

            e.preventDefault()
            const maxIndex = results.value.length
            activeIndex.value = (activeIndex.value + (e.shiftKey ? -1 : 1) + maxIndex) % maxIndex
        } else if (e.key === "ArrowDown") {
            e.preventDefault()
            activeIndex.value = Math.min(activeIndex.value + 1, results.value.length - 1)
        } else if (e.key === "ArrowUp") {
            e.preventDefault()
            activeIndex.value = Math.max(activeIndex.value - 1, 0)
        } else if (e.key === "ArrowRight") {
            const item = results.value[activeIndex.value]
            if (item?.executionsHref && isCaretAtEnd(e)) {
                e.preventDefault()
                openExecutions(item)
            }
        } else if (e.key === "Enter") {
            e.preventDefault()
            const item = results.value[activeIndex.value]
            if (item) {
                if (item.kind === "scope") {
                    enterScope(item)
                } else if (item.href) {
                    router.push(item.href)
                    closeSearch()
                }
            }
        }
    }

    const isCaretAtEnd = (e: KeyboardEvent): boolean => {
        const target = e.target
        if (!(target instanceof HTMLInputElement) && !(target instanceof HTMLTextAreaElement)) {
            return false
        }
        return target.selectionStart === target.value.length && target.selectionEnd === target.value.length
    }

    onMounted(() => {
        window.addEventListener("keydown", keyDown)
    })

    onUnmounted(() => {
        window.removeEventListener("keydown", keyDown)
    })

    const resetEntities = () => {
        entities.value = []
        entityTotals.value = {flow: 0, namespace: 0}
        entitiesLoading.value = false
        entitiesError.value = false
    }

    watch(query, () => {
        activeIndex.value = 0
        activeKey.value = null
        const q = query.value.trim()
        const ticket = ++searchTicket
        if (!isOpen.value || !q || scopeStack.value.length > 0) {
            resetEntities()
            return
        }
        const user = authStore.user
        if (!user?.hasAnyActionOnAnyNamespace(resource.FLOW, action.LIST)
            && !user?.hasAnyActionOnAnyNamespace(resource.NAMESPACE, action.LIST)) {
            resetEntities()
            return
        }
        entities.value = []
        entityTotals.value = {flow: 0, namespace: 0}
        entitiesError.value = false
        entitiesLoading.value = true
        loadEntities(q, ticket)
    })

    watch(results, () => {
        if (!isOpen.value) {
            return
        }

        if (activeKey.value) {
            const found = results.value.findIndex((item, index) => itemKey(item, index) === activeKey.value)
            if (found >= 0) {
                activeIndex.value = found
                return
            }
        }

        activeIndex.value = Math.min(activeIndex.value, Math.max(results.value.length - 1, 0))
    })

    watch(activeIndex, () => {
        if (!isOpen.value) {
            return
        }

        const item = results.value[activeIndex.value]
        activeKey.value = item ? itemKey(item, activeIndex.value) : null
        scrollActiveOptionIntoView()
    })
</script>

<style scoped lang="scss">
    .search-overlay {
        position: fixed;
        top: 0;
        left: 0;
        width: 100vw;
        height: 100vh;
        background: var(--ks-bg-scrim);
        z-index: var(--ks-z-overlay);
        display: flex;
        justify-content: center;
        align-items: flex-start;
        padding-top: 15vh;
    }

    .search-modal {
        width: 600px;
        max-width: 90vw;

        .search-container {
            background: var(--ks-bg-surface);
            border: 1px solid var(--ks-border-default);
            border-radius: var(--kel-input-border-radius, var(--kel-border-radius-base));
            box-shadow: 0 8px 24px var(--ks-shadow-elevated);
            overflow: hidden;
            font-size: var(--ks-font-size-sm);
        }

        :deep(.kel-input) {
            font-size: var(--ks-font-size-sm);

            .kel-input__wrapper {
                padding: 8px 16px;
                border: 0;
                box-shadow: none;
                background: var(--ks-bg-surface);
                border-radius: var(--kel-input-border-radius, var(--kel-border-radius-base)) var(--kel-input-border-radius, var(--kel-border-radius-base)) 0 0;

                input {
                    color: var(--ks-text-primary);
                    background: transparent;
                }

                input::placeholder {
                    color: var(--ks-placeholder-color);
                    font-size: var(--ks-placeholder-font-size);
                    font-weight: var(--ks-placeholder-font-weight);
                }

                .close-button {
                    color: var(--ks-text-primary);
                    &:hover {
                        color: var(--ks-text-link);
                        background-color: var(--ks-border-default);
                    }
                }
            }

            .scope-prefix {
                margin-left: 0.5rem;
                margin-right: 0.25rem;
                color: var(--ks-text-secondary);
                white-space: nowrap;
            }
        }

        .results {
            background: var(--ks-bg-surface);
            border-top: 1px solid var(--ks-border-default);
        }

        .results-scroll {
            max-height: 40vh;
        }

        .results-list {
            margin: 0;
            padding: 8px;
            list-style: none;
            display: flex;
            flex-direction: column;
            gap: 6px;
        }

        .result-heading {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);
            padding: var(--ks-spacing-1) var(--ks-spacing-2);
            color: var(--ks-text-secondary);
            font-size: var(--ks-font-size-xs);
            text-transform: uppercase;
        }

        .result-count {
            color: var(--ks-text-dim);
        }

        .result-namespace {
            color: var(--ks-text-secondary);
            white-space: nowrap;
        }

        .result-match {
            background: transparent;
            color: var(--ks-text-link);
            font-weight: var(--ks-font-weight-semibold);
        }

        .result-row {
            align-items: center;
            width: 100%;
        }

        .result-action {
            flex: 0 0 auto;
            color: var(--ks-text-secondary);
            white-space: nowrap;
        }

        .result-status {
            padding: var(--ks-spacing-1) var(--ks-spacing-2);
            color: var(--ks-text-secondary);
            font-size: var(--ks-font-size-sm);
        }

        .search-footer {
            display: flex;
            justify-content: space-between;
            gap: var(--ks-spacing-3);
            padding: var(--ks-spacing-2) var(--ks-spacing-4);
            border-top: 1px solid var(--ks-border-default);
            color: var(--ks-text-secondary);
            font-size: var(--ks-font-size-xs);
        }

        .search-footer-keys {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);
        }

        .result-link {
            font-size: var(--ks-font-size-sm);
            padding: 6px 10px;
            border-radius: 6px;
            color: var(--ks-text-primary);
            text-decoration: none;
            align-items: center;
            transition: none;
            flex: 1 1 auto;
            min-width: 0;
            width: auto;
            border: 0;
            background: transparent;
            text-align: left;
            cursor: pointer;
            font: inherit;
        }

        .result-title {
            flex: 0 1 auto;
            min-width: 0;
        }

        .result-parent {
            color: var(--ks-text-secondary);
            white-space: nowrap;
        }

        .result-separator {
            color: var(--ks-text-dim);
        }

        .result-leaf {
            white-space: nowrap;
        }

        .result-item.active .result-row {
            background-color: var(--ks-btn-secondary-bg-hover);
            color: var(--ks-text-primary);
            border-radius: var(--ks-radius-base);
        }

        .result-hint {
            margin-left: auto;
            color: var(--ks-text-secondary);
            font-size: var(--ks-font-size-sm);
            white-space: nowrap;
            transition: none;
        }

        .empty {
            padding: 12px 16px;
            color: var(--ks-text-secondary);
            font-size: var(--ks-font-size-sm);
        }
    }
</style>
