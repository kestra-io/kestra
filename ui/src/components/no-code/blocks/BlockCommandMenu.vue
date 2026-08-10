<template>
    <Teleport to="body">
        <div
            class="block-command-menu-overlay"
            data-test="block-command-menu"
            @click="emit('close')"
        >
            <div
                class="block-command-menu"
                role="dialog"
                :aria-label="t('block_editor.command_menu.title')"
                @click.stop
                @keydown="onKeydown"
            >
                <div class="block-command-menu-search">
                    <KsInput
                        ref="searchInput"
                        v-model="query"
                        class="block-command-menu-input"
                        :placeholder="t('block_editor.command_menu.search_placeholder')"
                        :aria-label="t('block_editor.command_menu.search_placeholder')"
                        aria-controls="block-command-menu-listbox"
                        :aria-activedescendant="activeIndex >= 0 ? `block-command-menu-option-${activeIndex}` : undefined"
                        clearable
                        data-test="block-command-menu-search"
                    />
                    <span v-if="contextLabel" class="block-command-menu-context">{{ contextLabel }}</span>
                </div>

                <div
                    id="block-command-menu-listbox"
                    class="block-command-menu-list"
                    role="listbox"
                    :aria-label="t('block_editor.command_menu.title')"
                    data-test="block-command-menu-list"
                >
                    <template v-if="filteredItems.length">
                        <template v-for="(item, index) in filteredItems" :key="item.id">
                            <div
                                v-if="index === 0 || item.group !== filteredItems[index - 1].group"
                                class="block-command-menu-group"
                            >
                                {{ item.group }}
                            </div>
                            <button
                                :id="`block-command-menu-option-${index}`"
                                type="button"
                                role="option"
                                class="block-command-menu-item"
                                :class="{'block-command-menu-item--active': activeIndex === index}"
                                :aria-selected="activeIndex === index"
                                data-test="block-command-menu-item"
                                @click="run(item)"
                                @mouseenter="activeIndex = index"
                            >
                                <component :is="item.icon" v-if="item.icon" class="block-command-menu-item-ico" />
                                <span class="block-command-menu-item-main">
                                    <span class="block-command-menu-item-title">{{ item.title }}</span>
                                    <span v-if="item.subtitle" class="block-command-menu-item-sub">{{ item.subtitle }}</span>
                                </span>
                                <kbd v-if="item.shortcut" class="block-command-menu-item-key">{{ item.shortcut }}</kbd>
                            </button>
                        </template>
                    </template>
                    <p v-else class="block-command-menu-empty">{{ t('block_editor.command_menu.no_match') }}</p>
                </div>
            </div>
        </div>
    </Teleport>
</template>

<script setup lang="ts">
    import {computed, nextTick, onMounted, ref, watch, type Component} from "vue"
    import {useI18n} from "vue-i18n"

    import {KsInput} from "@kestra-io/design-system"

    export interface BlockCommandMenuItem {
        id: string
        group: string
        title: string
        subtitle?: string
        icon?: Component
        shortcut?: string
        /** Kept out of the idle list so the hundreds of task types only surface once the user types. */
        searchOnly?: boolean
        run: () => void
    }

    const SEARCH_ONLY_MAX_RESULTS = 20

    const props = defineProps<{
        items: BlockCommandMenuItem[]
        contextLabel?: string
    }>()

    const emit = defineEmits<{
        (e: "close"): void
    }>()

    const {t} = useI18n()

    const query = ref("")
    const activeIndex = ref(0)
    const searchInput = ref<InstanceType<typeof KsInput>>()

    const filteredItems = computed<BlockCommandMenuItem[]>(() => {
        const search = query.value.trim().toLowerCase()
        if (!search) return props.items.filter(item => !item.searchOnly)
        const matches = props.items.filter(item =>
            `${item.title} ${item.subtitle ?? ""}`.toLowerCase().includes(search),
        )
        return [
            ...matches.filter(item => !item.searchOnly),
            ...matches.filter(item => item.searchOnly).slice(0, SEARCH_ONLY_MAX_RESULTS),
        ]
    })

    watch(filteredItems, (items) => {
        if (activeIndex.value >= items.length) activeIndex.value = Math.max(0, items.length - 1)
    })

    function run(item: BlockCommandMenuItem) {
        item.run()
    }

    function onKeydown(event: KeyboardEvent) {
        if (!["Escape", "ArrowDown", "ArrowUp", "Enter"].includes(event.key)) return
        event.preventDefault()
        event.stopPropagation()

        if (event.key === "Escape") {
            if (query.value) {
                query.value = ""
            } else {
                emit("close")
            }
        } else if (event.key === "ArrowDown") {
            activeIndex.value = Math.min(activeIndex.value + 1, filteredItems.value.length - 1)
        } else if (event.key === "ArrowUp") {
            activeIndex.value = Math.max(activeIndex.value - 1, 0)
        } else if (event.key === "Enter") {
            const item = filteredItems.value[activeIndex.value]
            if (item) run(item)
        }
    }

    onMounted(() => nextTick(() => searchInput.value?.focus()))
</script>

<style scoped lang="scss">
    .block-command-menu-overlay {
        position: fixed;
        inset: 0;
        z-index: 3100;
        display: flex;
        justify-content: center;
        padding-top: 12vh;
        background: var(--ks-bg-scrim);
    }

    .block-command-menu {
        width: 32rem;
        max-width: 92vw;
        max-height: 70vh;
        display: flex;
        flex-direction: column;
        background: var(--ks-bg-elevated);
        border: 1px solid var(--ks-border-strong);
        border-radius: var(--ks-radius-lg);
        box-shadow: var(--ks-shadow-lg);
        overflow: hidden;
    }

    .block-command-menu-search {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-3);
        border-bottom: 1px solid var(--ks-border-subtle);
    }

    .block-command-menu-input {
        flex: 1;
    }

    .block-command-menu-context {
        flex-shrink: 0;
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-muted);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-lg);
        padding: 0 var(--ks-spacing-2);
        white-space: nowrap;
    }

    .block-command-menu-list {
        flex: 1;
        min-height: 0;
        overflow-y: auto;
        padding: var(--ks-spacing-2);
        display: flex;
        flex-direction: column;
        gap: 1px;
    }

    .block-command-menu-group {
        font-size: var(--ks-font-size-xs);
        text-transform: uppercase;
        letter-spacing: 0.05em;
        color: var(--ks-text-muted);
        padding: var(--ks-spacing-2) var(--ks-spacing-2) var(--ks-spacing-1);
    }

    .block-command-menu-item {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-2) var(--ks-spacing-2);
        border: none;
        border-radius: var(--ks-radius-base);
        background: transparent;
        cursor: pointer;
        text-align: left;
        transition: background-color 0.12s;

        &:hover,
        &--active {
            background: var(--ks-bg-hover);
        }
    }

    .block-command-menu-item-ico {
        flex-shrink: 0;
        display: flex;
        font-size: var(--ks-font-size-base);
        color: var(--ks-icon-default);
    }

    .block-command-menu-item-main {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 1px;
    }

    .block-command-menu-item-title {
        font-size: var(--ks-font-size-sm);
        font-weight: 500;
        color: var(--ks-text-primary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .block-command-menu-item-sub {
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-muted);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .block-command-menu-item-key {
        flex-shrink: 0;
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-muted);
        background: var(--ks-bg-tag);
        border-radius: var(--ks-radius-sm);
        padding: 1px var(--ks-spacing-1);
    }

    .block-command-menu-empty {
        padding: var(--ks-spacing-4);
        text-align: center;
        color: var(--ks-text-muted);
        font-size: var(--ks-font-size-sm);
        margin: 0;
    }
</style>
