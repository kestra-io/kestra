<template>
    <div v-if="hasButtons && activeTab" class="contextDrawer" :style="{width: `${drawerWidth}px`}">
        <KsSplitter
            class="drawerSplitter"
            :style="{width: `${maxDrawerWidth}px`}"
        >
            <KsSplitterPanel class="drawerSpacerPanel" :min="0" />

            <KsSplitterPanel v-model:size="drawerWidth" :min="MIN_DRAWER_WIDTH" :max="maxDrawerWidth">
                <div class="drawerContent">
                    <div class="tabBar">
                        <KsButton
                            v-for="(button, key) of buttons"
                            :key="key"
                            :type="activeTab === key ? 'primary' : 'default'"
                            :tag="button.url ? 'a' : 'button'"
                            :href="button.url"
                            @click="() => { if (!button.url) { setActiveTab(key as string) } }"
                            :target="button.url ? '_blank' : undefined"
                        >
                            <component :is="button.icon" class="tab-icon" />{{ button.title }}
                            <OpenInNew v-if="button.url" class="open-in-new" />
                            <div v-if="button.hasUnreadMarker === true && hasUnread" class="newsDot" />
                        </KsButton>

                        <div style="flex: 1" />

                        <KsIconButton class="close-btn" :ariaLabel="$t('close')" @click="setActiveTab('')">
                            <Close />
                        </KsIconButton>
                    </div>

                    <div class="panelContent">
                        <KeepAlive>
                            <ContextDocs v-if="activeTab === 'docs'" />
                            <ContextNews v-else-if="activeTab === 'news'" />
                        </KeepAlive>
                    </div>
                </div>
            </KsSplitterPanel>
        </KsSplitter>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useStorage, useWindowSize} from "@vueuse/core"
    import ContextDocs from "./docs/ContextDocs.vue"
    import ContextNews from "./layout/ContextNews.vue"

    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import Close from "vue-material-design-icons/Close.vue"

    import {useApiStore} from "../stores/api"
    import {useMiscStore} from "override/stores/misc"
    import {useContextButtons} from "override/composables/contextButtons"

    const {buttons} = useContextButtons()
    const apiStore = useApiStore()
    const miscStore = useMiscStore()

    const activeTab = computed(() => miscStore.contextInfoBarOpenTab)
    const hasButtons = computed(() => Object.keys(buttons).length > 0)

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
        flex-shrink: 0;
        overflow: hidden;
    }

    .drawerContent {
        display: flex;
        flex-direction: column;
        height: 100%;
        width: 100%;
    }

    .tabBar {
        flex-shrink: 0;
        padding: 0.5rem 0.75rem;
        border-bottom: 1px solid var(--ks-border-default);
        display: flex;
        flex-direction: row;
        align-items: center;
        gap: 0.5rem;
        font-size: var(--ks-font-size-sm);
        overflow-x: auto;

        &::-webkit-scrollbar {
            height: 0;
        }
        scrollbar-width: none;

        .kel-button {
            font-size: var(--ks-font-size-sm);
            height: auto;
            padding: 5px 10px;
            position: relative;
        }

        .kel-button + .kel-button {
            margin-left: 0;
        }

        .tab-icon {
            margin-right: 0.25rem;
        }

        .open-in-new {
            margin-left: 0.25rem;
            opacity: 0.25;
        }

        .close-btn {
            border: none;
            color: var(--ks-text-dim);
        }

        .newsDot {
            width: 10px;
            height: 10px;
            background-color: var(--ks-status-error);
            border: 2px solid var(--ks-btn-secondary-bg-default);
            border-radius: 50%;
            display: block;
            position: absolute;
            bottom: -4px;
            right: -4px;
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
