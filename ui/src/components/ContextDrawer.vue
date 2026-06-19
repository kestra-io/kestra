<template>
    <Transition name="drawer">
        <div v-if="hasButtons && activeTab" class="contextDrawer" :style="{'--drawer-width': `${drawerWidth}px`}">
            <KsSplitter
                class="drawerSplitter"
                :style="{width: `${maxDrawerWidth}px`}"
            >
                <KsSplitterPanel class="drawerSpacerPanel" :min="0" />

                <KsSplitterPanel v-model:size="drawerWidth" :min="MIN_DRAWER_WIDTH" :max="maxDrawerWidth">
                    <div class="drawerContent">
                        <div class="tabBar">
                            <KsTabs
                                class="context-tabs"
                                :modelValue="activeTab"
                                type="box"
                                :beforeLeave="handleBeforeLeave"
                            >
                                <KsTabPane
                                    v-for="(button, key) of contextButtons"
                                    :key="key"
                                    :name="key as string"
                                >
                                    <template #label>
                                        <span class="tab-label" :class="{'tab-label--active': key === activeTab}">
                                            <component :is="button.icon" class="tab-icon" />
                                            {{ button.title }}
                                            <OpenInNew v-if="button.url" class="open-in-new" />
                                            <span v-if="button.hasUnreadMarker === true && hasUnread" class="newsDot" />
                                        </span>
                                    </template>
                                </KsTabPane>
                            </KsTabs>

                        </div>

                        <div class="panelContent">
                            <KeepAlive v-if="activeTab">
                                <component
                                    :is="contextButtons[activeTab]?.component"
                                    v-if="contextButtons[activeTab]?.component"
                                    :key="activeTab"
                                />
                            </KeepAlive>
                        </div>
                    </div>
                </KsSplitterPanel>
            </KsSplitter>
        </div>
    </Transition>
</template>

<script setup lang="ts">
    import {computed, ref, watch, type Component, PropType} from "vue"
    import {useStorage, useWindowSize} from "@vueuse/core"

    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"

    import {useApiStore} from "../stores/api"
    import {useMiscStore} from "override/stores/misc"
    import {useContextButtons} from "override/composables/contextButtons"

    const props = defineProps({
        additionalButtons: {
            type: Object as PropType<Record<string, {
                title: string;
                icon?: Component;
                component?: Component;
                url?: string;
                hasUnreadMarker?: boolean;
            }>>,
            default: () => ({}),
        },
    })

    const {buttons} = useContextButtons()
    const apiStore = useApiStore()
    const miscStore = useMiscStore()

    const activeTab = computed(() => miscStore.contextInfoBarOpenTab)
    const contextButtons = computed(() => ({...buttons, ...props.additionalButtons}))
    const hasButtons = computed(() => Object.keys(contextButtons.value).length > 0)

    const lastNewsReadDate = useStorage<string | null>("feeds", null)
    const hasUnread = computed(() => {
        const feeds = apiStore.feeds
        return (
            lastNewsReadDate.value === null ||
            (feeds?.[0] && (new Date(lastNewsReadDate.value) < new Date(feeds[0].publicationDate)))
        )
    })

    const MIN_DRAWER_WIDTH = 200
    const drawerWidth = ref(640)
    const {width: windowWidth} = useWindowSize()
    const maxDrawerWidth = computed(() => windowWidth.value * 0.5)

    watch(maxDrawerWidth, (value) => {
        drawerWidth.value = Math.min(Math.max(drawerWidth.value, MIN_DRAWER_WIDTH), value)
    })

    function setActiveTab(tab: string) {
        if (tab) miscStore.lastContextTab = tab
        miscStore.contextInfoBarOpenTab = tab
    }

    // URL entries open in a new tab without becoming the active pane.
    function handleBeforeLeave(newName: string | number) {
        const key = String(newName)
        const button = contextButtons.value[key]
        if (button?.url) {
            window.open(button.url, "_blank", "noopener,noreferrer")
            return false
        }
        setActiveTab(key)
        return true
    }

</script>

<style scoped lang="scss">
    .drawerSplitter {
        position: absolute;
        top: 0;
        right: 0;
        bottom: 0;
        height: 100%;
        flex-shrink: 0;

        :deep(.kel-splitter-panel) {
            min-width: 0;
        }

        :deep(.drawerSpacerPanel) {
            overflow: hidden;
            pointer-events: none;
        }

        :deep(.kel-splitter-bar) {
            background-color: transparent;
        }

        :deep(.kel-splitter__splitter) {
            width: 5px;
            background-color: transparent;
            transition: background-color 0.1s;

            &:hover,
            &.is-dragging {
                background-color: var(--ks-btn-primary-bg-default);
            }
        }
    }

    .contextDrawer {
        position: relative;
        height: 100%;
        width: var(--drawer-width);
        flex-shrink: 0;
        overflow: hidden;
    }

    .drawer-enter-active,
    .drawer-leave-active {
        transition: width 0.2s ease, opacity 0.2s ease;
    }

    .drawer-enter-from,
    .drawer-leave-to {
        width: 0 !important;
        opacity: 0;
    }

    @media (prefers-reduced-motion: reduce) {
        .drawer-enter-active,
        .drawer-leave-active {
            transition: none;
        }
    }

    .drawerContent {
        display: flex;
        flex-direction: column;
        height: 100%;
        width: 100%;
    }

    .tabBar {
        flex-shrink: 0;
        display: flex;
        flex-direction: row;
        align-items: stretch;
        background-color: var(--ks-bg-input);
        border-left: 1px solid var(--ks-border-default);

        .context-tabs {
            flex: 1;
            min-width: 0;
        }

        .tab-label {
            display: inline-flex;
            align-items: center;
            gap: 0.25rem;
            position: relative;

            &--active .tab-icon {
                color: var(--ks-icon-active);
            }
        }

        .open-in-new {
            opacity: 0.5;
        }

        .newsDot {
            width: 8px;
            height: 8px;
            background-color: var(--ks-status-error);
            border: 2px solid var(--ks-bg-input);
            border-radius: 50%;
            position: absolute;
            top: -3px;
            right: -5px;
        }
    }

    .panelContent {
        flex: 1;
        min-height: 0;
        min-width: 0;
        position: relative;
        overflow-y: auto;

        &::-webkit-scrollbar {
            width: 0;
        }
        scrollbar-width: none;
    }
</style>
