<template>
    <div v-if="hasButtons && !activeTab.length" class="barWrapper">
        <KsButton
            v-for="(button, key) of contextButtons"
            :key="key"
            :type="activeTab === key ? 'primary' : 'default'"
            :tag="button.url ? 'a' : 'button'"
            :href="button.url"
            @click="() => {if(!button.url){ setActiveTab(key as string)}}"
            :target="button.url ? '_blank' : undefined"
        >
            <component :is="button.icon" class="context-button-icon" />{{ button.title }}
            <OpenInNew v-if="button.url" class="open-in-new" />
            <div v-if="button.hasUnreadMarker === true && hasUnread" class="newsDot" />
        </KsButton>

        <div style="flex:1" />

        <KsTooltip
            :disabled="!miscStore.configs?.commitId"
        >
            <template #content>
                <code>{{ miscStore.configs?.commitId }}</code> <KsDateAgo v-if="miscStore.configs?.commitDate" :inverted="true" :date="miscStore.configs.commitDate" />
            </template>
            <span class="versionNumber">{{ miscStore.configs?.version }}</span>
        </KsTooltip>
        <KsButton class="theme-switcher" @click="onSwitchTheme">
            <WeatherNight v-if="themeIsDark" />
            <WeatherSunny v-else />
        </KsButton>
    </div>

    <div v-else-if="hasButtons" class="contextInfoSidebar" :style="{width: `${sidebarWidth}px`}">
        <KsSplitter
            class="contextInfoSplitter"
            :style="{width: `${maxSidebarWidth}px`}"
        >
            <KsSplitterPanel class="contextInfoSpacerPanel" :min="0" />

            <KsSplitterPanel v-model:size="sidebarWidth" :min="minSidebarWidth" :max="maxSidebarWidth">
                <div class="contextInfoContent">
                    <div class="barWrapper opened">
                        <KsButton
                            v-for="(button, key) of contextButtons"
                            :key="key"
                            :type="activeTab === key ? 'primary' : 'default'"
                            :tag="button.url ? 'a' : 'button'"
                            :href="button.url"
                            @click="() => {if(!button.url){ setActiveTab(key as string)}}"
                            :target="button.url ? '_blank' : undefined"
                        >
                            <component :is="button.icon" class="context-button-icon" />{{ button.title }}
                            <OpenInNew v-if="button.url" class="open-in-new" />
                            <div v-if="button.hasUnreadMarker === true && hasUnread" class="newsDot" />
                        </KsButton>

                        <div style="flex:1" />

                        <KsTooltip
                            :disabled="!miscStore.configs?.commitId"
                        >
                            <template #content>
                                <code>{{ miscStore.configs?.commitId }}</code> <KsDateAgo v-if="miscStore.configs?.commitDate" :inverted="true" :date="miscStore.configs.commitDate" />
                            </template>
                            <span class="versionNumber">{{ miscStore.configs?.version }}</span>
                        </KsTooltip>
                        <KsButton class="theme-switcher" @click="onSwitchTheme">
                            <WeatherNight v-if="themeIsDark" />
                            <WeatherSunny v-else />
                        </KsButton>
                    </div>

                    <div class="panelWrapper">
                        <div :style="{overflow: 'hidden'}">
                            <button v-if="activeTab.length" class="closeButton" @click="setActiveTab('')">
                                <Close />
                            </button>
                            <KeepAlive v-if="activeTab">
                                <ContextDocs v-if="activeTab === 'docs'" />
                                <ContextNews v-else-if="activeTab === 'news'" />
                                <template v-else>
                                    {{ activeTab }}
                                </template>
                            </KeepAlive>
                        </div>
                    </div>
                </div>
            </KsSplitterPanel>
        </KsSplitter>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch, type Component, PropType} from "vue"
    import {useStorage, useWindowSize} from "@vueuse/core"
    import ContextDocs from "./docs/ContextDocs.vue"
    import ContextNews from "./layout/ContextNews.vue"

    import Close from "vue-material-design-icons/Close.vue"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import WeatherSunny from "vue-material-design-icons/WeatherSunny.vue"
    import WeatherNight from "vue-material-design-icons/WeatherNight.vue"

    import * as Utils from "../utils/utils"
    import {useApiStore} from "../stores/api"
    import {useMiscStore} from "override/stores/misc"

    import {useContextButtons} from "override/composables/contextButtons"
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

    const props = defineProps({
        additionalButtons: {
            type: Object as PropType<Record<string, {
                title: string;
                icon?: Component;
                url: string;
                hasUnreadMarker: false;
            }>>,
            default: () => ({}),
        },
    })

    const BAR_WIDTH_PX = 64
    const PANEL_MIN_WIDTH_PX = 50

    const sidebarWidth = ref(704)
    const {width: windowWidth} = useWindowSize()
    const minSidebarWidth = BAR_WIDTH_PX + PANEL_MIN_WIDTH_PX
    const maxSidebarWidth = computed(() => windowWidth.value * 0.5 + BAR_WIDTH_PX)

    watch(maxSidebarWidth, (value) => {
        sidebarWidth.value = Math.min(Math.max(sidebarWidth.value, minSidebarWidth), value)
    })

    function setActiveTab(tab: string) {
        if (activeTab.value === tab) {
            miscStore.contextInfoBarOpenTab = ""
        } else {
            miscStore.contextInfoBarOpenTab = tab
        }
    }

    const themeIsDark = ref(localStorage.getItem("theme") === "dark")

    const onSwitchTheme = () => {
        themeIsDark.value = !themeIsDark.value
        const theme = themeIsDark.value ? "dark" : "light"
        Utils.switchTheme(miscStore, theme)
    }
</script>

<style scoped lang="scss">
    @use 'element-plus/theme-chalk/src/mixins/mixins' as *;

    .contextInfoSplitter {
        position: absolute;
        top: 0;
        right: 0;
        bottom: 0;
        height: 100%;
        flex-shrink: 0;

        :deep(.kel-splitter-panel) {
            min-width: 0;
        }

        :deep(.contextInfoSpacerPanel) {
            overflow: hidden;
            pointer-events: none;
        }

        :deep(.kel-splitter-bar) {
            background-color: transparent;
        }

        :deep(.kel-splitter__splitter) {
            width: 5px;
            background-color: transparent;
            transition: background-color .1s;

            &:hover,
            &.is-dragging {
                background-color: var(--ks-button-background-primary);
            }
        }
    }

    .contextInfoContent {
        display: flex;
        height: 100%;
        width: 100%;
    }

    .contextInfoSidebar {
        position: relative;
        height: 100%;
        flex-shrink: 0;
        overflow: hidden;
    }

    .barWrapper {
        position: relative;
        width: 4rem;
        min-width: 4rem;
        flex-shrink: 0;
        padding: 0.75rem;
        writing-mode: vertical-rl;
        text-orientation: mixed;
        border-left: 1px solid var(--ks-border-primary);
        display: flex;
        align-items: center;
        gap: 0.5rem;
        font-size: var(--ks-font-size-sm);
        overflow-y: auto;
        &::-webkit-scrollbar {
            width: 0;
        }
        scrollbar-width: none;

        &.opened {
            border-right: 1px solid var(--ks-border-primary);
        }

        .kel-button {
            font-size: var(--ks-font-size-sm);
            height: auto;
            padding: 10px 5px;
            width: 32px;
            position: relative;
        }

        .kel-button + .kel-button {
            margin-left: 0;
        }

        .versionNumber {
            color: var(--ks-content-tertiary);
            opacity: .4;
            margin-top: 1rem;
            white-space: nowrap;
        }

        .theme-switcher {
            transform: rotate(-90deg);
        }

        .context-button-icon {
            transform: rotate(90deg);
            margin-bottom: 0.75rem;
        }

        .open-in-new {
            transform: rotate(90deg);
            margin-top: 0.75rem;
            margin-bottom: 0;
            opacity: .25;
        }

        @include res(xs) {
            display: none;
        }

        .newsDot{
            width: 10px;
            height: 10px;
            background-color: var(--ks-content-alert);
            border: 2px solid var(--ks-button-background-secondary);
            border-radius: 50%;
            display: block;
            position: absolute;
            bottom: -4px;
            right: -4px;
        }
    }

    .panelWrapper {
        flex: 1;
        height: 100%;
        min-width: 0;
        position: relative;
        overflow-y: auto;
        &::-webkit-scrollbar {
            width: 0px;
        }
        scrollbar-width: none;

        .closeButton {
            position: fixed;
            top: 1rem;
            right: 1rem;
            color: var(--ks-content-tertiary);
            background: none;
            border: none;
            z-index: 5;
        }

    }
</style>
