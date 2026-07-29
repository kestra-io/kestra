<template>
    <div v-if="hasButtons" class="contextDrawer" :class="{'is-closed': !activeTab}" :style="{'--drawer-width': `${drawerWidth}px`}">
        <KsSplitter
            class="drawerSplitter"
            :style="{width: `${maxDrawerWidth}px`}"
        >
            <KsSplitterPanel class="drawerSpacerPanel" :min="0" />

            <KsSplitterPanel v-model:size="drawerWidth" :min="MIN_DRAWER_WIDTH" :max="maxDrawerWidth">
                <div class="drawerContent">
                    <div v-if="showTabBar" class="tabBar">
                        <KsTabs
                            class="context-tabs"
                            :modelValue="activeTab"
                            type="box"
                            :beforeLeave="handleBeforeLeave"
                        >
                            <KsTabPane
                                v-for="(button, key) of visibleTabButtons"
                                :key="key"
                                :name="key as string"
                            >
                                <template #label>
                                    <span class="tab-label" :class="{'tab-label--active': key === activeTab}">
                                        <component :is="button.icon" class="tab-icon" />
                                        {{ button.title }}
                                        <OpenInNew v-if="button.url" class="open-in-new" />
                                        <span v-if="isUnread(button)" class="newsDot" />
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
</template>

<script setup lang="ts">
    import {computed, ref, watch, PropType} from "vue"
    import {useWindowSize} from "@vueuse/core"

    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"

    import {useMiscStore} from "override/stores/misc"
    import {useContextButtons, type Button} from "override/composables/contextButtons"

    const props = defineProps({
        additionalButtons: {
            type: Object as PropType<Record<string, Button>>,
            default: () => ({}),
        },
    })

    const {buttons} = useContextButtons()
    const miscStore = useMiscStore()

    const contextButtons = computed(() => ({...buttons, ...props.additionalButtons}))
    const hasButtons = computed(() => Object.keys(contextButtons.value).length > 0)

    // `hidden: true` opts a button out of the tab strip while still letting it resolve panel content.
    const visibleTabButtons = computed(() => Object.fromEntries(
        Object.entries(contextButtons.value).filter(([, button]) => !button.hidden),
    ))

    // A tab hidden by the current route (e.g. the AI dock on the full-page /ai) falls back to the first
    // visible tab, so reopening the dock there never gets stuck on a stripless hidden pane. Panel-only
    // surfaces (the notifications bell) are exempt — they are meant to open as a stripless panel.
    const activeTab = computed(() => {
        const stored = miscStore.contextInfoBarOpenTab
        const button = stored ? contextButtons.value[stored] : undefined
        if (button?.hidden && !button.panelOnly) return Object.keys(visibleTabButtons.value)[0] ?? ""
        return stored
    })

    // Hide the strip entirely only while a panel-only surface (e.g. notifications) is active.
    const showTabBar = computed(() => contextButtons.value[activeTab.value]?.panelOnly !== true)

    // Each button supplies its own unread source; the drawer just renders the dot.
    function isUnread(button: {hasUnreadMarker?: boolean; unread?: {readonly value: boolean}}) {
        return button.hasUnreadMarker === true && !!button.unread?.value
    }

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
        transition: width 0.32s cubic-bezier(0.22, 1, 0.36, 1);

        &.is-closed {
            width: 0;
        }
    }

    @media (prefers-reduced-motion: reduce) {
        .contextDrawer {
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
