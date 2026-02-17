<template>
    <div>
        <teleport to="body">
            <div v-if="isOpen" class="search-overlay" @click="closeSearch">
                <div class="search-modal" role="dialog" aria-modal="true" @click.stop>
                    <div class="search-container" :aria-label="$t('jump to...')">
                        <el-input
                            ref="searchInput"
                            v-model="query"
                            :placeholder="$t('jump to...')"
                            @keydown.esc="closeSearch"
                            @keydown="onInputKeydown"
                        >
                            <template #prefix>
                                <Magnify />
                                <span v-if="scopePrefix" class="scope-prefix">{{ scopePrefix }}</span>
                            </template>
                            <template #suffix>
                                <el-button
                                    v-if="query"
                                    class="close-button"
                                    text
                                    circle
                                    @click.stop="clearSearch"
                                >
                                    <Close />
                                </el-button>
                                <span v-else class="d-none d-sm-block">
                                    <kbd>ESC</kbd> to close
                                </span>
                            </template>
                        </el-input>

                        <div class="results" role="listbox">
                            <el-scrollbar v-if="results.length > 0" class="results-scroll">
                                <ul id="global-search-listbox" class="results-list">
                                    <li
                                        v-for="(item, index) in results"
                                        :key="itemKey(item, index)"
                                        :id="`global-search-option-${index}`"
                                        class="result-item"
                                        :class="{active: index === activeIndex}"
                                        role="option"
                                        :aria-selected="index === activeIndex"
                                        @mouseenter="activeIndex = index"
                                    >
                                        <component
                                            :is="item.kind === 'link' ? 'router-link' : 'button'"
                                            v-bind="item.kind === 'link' ? {to: item.href} : {type: 'button'}"
                                            class="result-link d-flex gap-2"
                                            @click="onItemClick(item)"
                                        >
                                            <div class="result-title d-flex gap-2 nav-item-title">
                                                <component v-if="item.icon?.element" :is="{...item.icon.element}" class="align-middle" />
                                                <span v-if="item.parentTitle" class="result-parent">
                                                    {{ item.parentTitle }}
                                                </span>
                                                <span v-if="item.parentTitle" class="result-separator">/</span>
                                                <span class="result-leaf">{{ item.title }}</span>
                                            </div>
                                            <span
                                                v-if="index === activeIndex"
                                                class="result-hint d-none d-sm-flex align-items-center"
                                            >
                                                <span>Jump to</span>
                                            </span>
                                        </component>
                                    </li>
                                </ul>
                            </el-scrollbar>
                            <div v-else class="empty">
                                {{ $t("no results") }}
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </teleport>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, onUnmounted, nextTick, watch} from "vue";
    import {useRouter} from "vue-router";
    import {useLeftMenu} from "override/components/useLeftMenu";
    import type {MenuItem} from "override/components/useLeftMenu";
    import Magnify from "vue-material-design-icons/Magnify.vue";
    import Close from "vue-material-design-icons/Close.vue";

    const router = useRouter();
    const {menu} = useLeftMenu()

    type SearchItem = {
        kind: "link" | "scope";
        title: MenuItem["title"];
        parentTitle?: MenuItem["title"];
        href?: NonNullable<MenuItem["href"]>;
        icon?: MenuItem["icon"];
        children?: MenuItem[];
        depth: number;
        order: number;
    };

    type ScopeNode = {
        title: string;
        items: MenuItem[];
    };

    const query = ref("");
    const isOpen = ref(false);
    const searchInput = ref<{ focus?: () => void } | null>(null);
    const activeIndex = ref(0);
    const scopeStack = ref<ScopeNode[]>([]);

    const scopePrefix = computed(() => {
        if (scopeStack.value.length === 0) {
            return "";
        }

        return `${scopeStack.value.map(s => s.title).join(" / ")} /`;
    });

    const buildEntries = (items: MenuItem[], ancestors: string[], depth: number, startOrder: {value: number}): SearchItem[] => {
        const entries: SearchItem[] = [];

        for (const item of items) {
            if (item.hidden) {
                continue;
            }

            const hasChildren = Boolean(item.child && item.child.length > 0);
            const parentTitle = ancestors.length > 0 ? ancestors.join(" / ") : undefined;
            const icon = item.icon;

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
                });
            } else if (item.href) {
                entries.push({
                    kind: "link",
                    title: item.title,
                    parentTitle,
                    href: item.href,
                    icon,
                    depth,
                    order: startOrder.value++,
                });
            }

            // Include descendants for search (hierarchy preserved via parentTitle/depth).
            if (hasChildren) {
                entries.push(...buildEntries(item.child!, [...ancestors, item.title], depth + 1, startOrder));
            }
        }

        return entries;
    }

    const navItems = computed(() => {
        if (!isOpen.value) {
            return [];
        }

        const root = scopeStack.value.length > 0 ? scopeStack.value[scopeStack.value.length - 1].items : menu.value;
        const order = {value: 0};
        // When scoped, we treat the scope root as depth 0 for ordering.
        return buildEntries(root, [], 0, order);
    });

    const results = computed(() => {
        const q = query.value.trim().toLowerCase();
        const matches = (item: SearchItem) => {
            const haystack = [item.parentTitle, item.title].filter(Boolean).join(" ").toLowerCase();
            return haystack.includes(q);
        };

        const filtered = q ? navItems.value.filter(matches) : navItems.value;

        // Prefer items closest to the current root (depth 0 first), while preserving menu order.
        return [...filtered].sort((a, b) => (a.depth - b.depth) || (a.order - b.order));
    });

    const keyDown = (e: KeyboardEvent) => {
        if ((e.ctrlKey || e.metaKey) && !e.shiftKey && e.key === "k") {
            e.preventDefault();
            openSearch();
        }
        if (e.key === "Escape" && isOpen.value) {
            e.preventDefault();
            closeSearch();
        }
    };

    const openSearch = () => {
        isOpen.value = true;
        activeIndex.value = 0;
        nextTick(() => {
            searchInput.value?.focus?.();
        });
    }

    const closeSearch = () => {
        isOpen.value = false;
        query.value = "";
        activeIndex.value = 0;
        scopeStack.value = [];
    }

    const clearSearch = () => {
        query.value = "";
        activeIndex.value = 0;
        nextTick(() => {
            searchInput.value?.focus?.();
        });
    }

    const itemKey = (item: SearchItem, index: number): string => {
        const href = item.href;
        if (typeof href === "string") {
            return href;
        }
        if (typeof href === "object" && href !== null) {
            if ("path" in href && typeof href.path === "string") {
                return href.path;
            }
            if ("name" in href && href.name != null) {
                return `name:${String(href.name)}`;
            }
        }

        return `${item.kind}:${item.parentTitle ?? ""}:${item.title}:${index}`;
    }

    const enterScope = (item: SearchItem) => {
        if (!item.children || item.children.length === 0) {
            return;
        }

        scopeStack.value = [...scopeStack.value, {title: item.title, items: item.children}];
        query.value = "";
        activeIndex.value = 0;
        nextTick(() => searchInput.value?.focus?.());
    }

    const onItemClick = (item: SearchItem) => {
        if (item.kind === "scope") {
            enterScope(item);
            return;
        }

        closeSearch();
    }

    const scrollActiveOptionIntoView = () => {
        nextTick(() => {
            const el = document.getElementById(`global-search-option-${activeIndex.value}`);
            el?.scrollIntoView({block: "nearest"});
        });
    }

    const onInputKeydown = (e: KeyboardEvent) => {
        if (e.key === "Backspace" && query.value.length === 0 && scopeStack.value.length > 0) {
            e.preventDefault();
            scopeStack.value = scopeStack.value.slice(0, -1);
            activeIndex.value = 0;
            return;
        }

        if (results.value.length === 0) {
            return;
        }

        if (e.key === "Tab") {
            const activeItem = results.value[activeIndex.value];
            if (activeItem?.kind === "scope") {
                e.preventDefault();
                enterScope(activeItem);
                return;
            }

            e.preventDefault();
            const maxIndex = results.value.length;
            activeIndex.value = (activeIndex.value + (e.shiftKey ? -1 : 1) + maxIndex) % maxIndex;
        } else if (e.key === "ArrowDown") {
            e.preventDefault();
            activeIndex.value = Math.min(activeIndex.value + 1, results.value.length - 1);
        } else if (e.key === "ArrowUp") {
            e.preventDefault();
            activeIndex.value = Math.max(activeIndex.value - 1, 0);
        } else if (e.key === "Enter") {
            e.preventDefault();
            const item = results.value[activeIndex.value];
            if (item) {
                if (item.kind === "scope") {
                    enterScope(item);
                } else if (item.href) {
                    router.push(item.href);
                    closeSearch();
                }
            }
        }
    }


    onMounted(() => {
        window.addEventListener("keydown", keyDown);
    });

    onUnmounted(() => {
        window.removeEventListener("keydown", keyDown);
    });

    watch(query, () => {
        activeIndex.value = 0;
    });

    watch(results, () => {
        if (!isOpen.value) {
            return;
        }

        activeIndex.value = Math.min(activeIndex.value, Math.max(results.value.length - 1, 0));
    });

    watch(activeIndex, () => {
        if (!isOpen.value) {
            return;
        }

        scrollActiveOptionIntoView();
    });
</script>

<style scoped lang="scss">
    .search-overlay {
        position: fixed;
        top: 0;
        left: 0;
        width: 100vw;
        height: 100vh;
        background: var(--el-overlay-color-lighter);
        z-index: 10000;
        display: flex;
        justify-content: center;
        align-items: flex-start;
        padding-top: 15vh;
    }

    .search-modal {
        width: 600px;
        max-width: 90vw;

        .search-container {
            --gs-font-size: 0.875rem;

            background: var(--ks-background-card);
            border: 1px solid var(--ks-border-primary);
            border-radius: var(--el-input-border-radius, var(--el-border-radius-base));
            box-shadow:
                0 8px 24px rgba(0,0,0,0.35);
            overflow: hidden;
            font-size: var(--gs-font-size);
        }

        :deep(.el-input) {
            font-size: var(--gs-font-size);

            .el-input__wrapper {
                padding: 8px 16px;
                border: 0;
                box-shadow: none;
                background: var(--ks-background-card);
                border-radius: var(--el-input-border-radius, var(--el-border-radius-base)) var(--el-input-border-radius, var(--el-border-radius-base)) 0 0;

                input {
                    color: var(--ks-content-primary);
                    background: transparent;
                }

                input::placeholder {
                    color: var(--ks-content-tertiary);
                }

                .close-button {
                    color: var(--ks-content-primary);
                    &:hover {
                        color: var(--ks-content-link);
                        background-color: var(--ks-border-primary);
                    }
                }
            }

            .scope-prefix {
                margin-left: 0.5rem;
                margin-right: 0.25rem;
                color: var(--ks-content-secondary);
                white-space: nowrap;
            }
        }

        .results {
            background: var(--ks-background-card);
            border-top: 1px solid var(--ks-border-primary);
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

        .result-link {
            font-size: var(--gs-font-size);
            padding: 6px 10px;
            border-radius: 6px;
            color: var(--ks-content-primary);
            text-decoration: none;
            align-items: center;
            transition: none;
            width: 100%;
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
            color: var(--ks-content-secondary);
            white-space: nowrap;
        }

        .result-separator {
            color: var(--ks-content-tertiary);
        }

        .result-leaf {
            white-space: nowrap;
        }

        .result-item.active .result-link {
            background-color: var(--ks-button-background-secondary-hover);
            color: var(--ks-content-primary);
        }

        .result-hint {
            margin-left: auto;
            color: var(--ks-content-secondary);
            font-size: var(--gs-font-size);
            white-space: nowrap;
            transition: none;
        }

        .empty {
            padding: 12px 16px;
            color: var(--ks-content-secondary);
            font-size: var(--gs-font-size);
        }
    }
</style>
