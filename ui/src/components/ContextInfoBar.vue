<template>
    <div v-if="Object.keys(buttons).length" class="barWrapper" :class="{opened: activeTab?.length > 0}">
        <button v-if="activeTab.length" class="barResizer" ref="resizeHandle" @mousedown="startResizing" />

        <el-button
            v-for="(button, key) of {...buttons, ...props.additionalButtons}"
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
        </el-button>

        <div style="flex:1" />

        <el-tooltip
            effect="light"
            :persistent="false"
            transition=""
            :hideAfter="0"
            :disabled="!miscStore.configs?.commitId"
        >
            <template #content>
                <code>{{ miscStore.configs?.commitId }}</code> <DateAgo v-if="miscStore.configs?.commitDate" :inverted="true" :date="miscStore.configs.commitDate" />
            </template>
            <span class="versionNumber">{{ miscStore.configs?.version }}</span>
        </el-tooltip>
        <el-button class="theme-switcher" @click="onSwitchTheme">
            <WeatherNight v-if="themeIsDark" />
            <WeatherSunny v-else />
        </el-button>
    </div>
    <div class="panelWrapper" ref="panelWrapper" :class="{panelTabResizing: resizing}" :style="{width: activeTab?.length ? `${panelWidth}px` : 0}">
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
</template>

<script setup lang="ts">
    import {computed, ref, watch, type Ref, type Component, PropType} from "vue";
    import {useMouse, watchThrottled, useStorage} from "@vueuse/core"
    import ContextDocs from "./docs/ContextDocs.vue"
    import ContextNews from "./layout/ContextNews.vue"
    import DateAgo from "./layout/DateAgo.vue"

    import Close from "vue-material-design-icons/Close.vue"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import WeatherSunny from "vue-material-design-icons/WeatherSunny.vue"
    import WeatherNight from "vue-material-design-icons/WeatherNight.vue"

    import Utils from "../utils/utils";
    import {useApiStore} from "../stores/api";
    import {useMiscStore} from "override/stores/misc";

    import {useContextButtons} from "override/composables/contextButtons";
    const {buttons} = useContextButtons();

    const apiStore = useApiStore();
    const miscStore = useMiscStore();

    const activeTab = computed(() => miscStore.contextInfoBarOpenTab)

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
            default: () => ({})
        }
    });

    const panelWidth = ref(640)
    const panelWrapper = ref<HTMLDivElement | null>(null)

    const {startResizing, resizing} = useResizablePanel(activeTab)

    function useResizablePanel(localActiveTab: Ref<string>) {
        const {x} = useMouse()

        const resizing = ref(false)
        const resizingStartPosition = ref(0)
        const referencePanelWidth = ref(0)
        const startResizing = () => {
            resizingStartPosition.value = x.value;
            referencePanelWidth.value = panelWidth.value;
            resizing.value = true;

            document.body.addEventListener("mouseup", () => {
                resizing.value = false;
            }, {once: true})
        }

        watchThrottled(x, () => {
            if(resizing.value){
                const newPanelWidth = referencePanelWidth.value + (resizingStartPosition.value - x.value);
                panelWidth.value = Math.min(Math.max(newPanelWidth, 50), window.innerWidth * .5)
            }
        }, {throttle:20})

        watch(localActiveTab, (value) => {
            if(value.length){
                x.value = 0;
            }
        })

        return {startResizing, resizing}
    }

    function setActiveTab(tab: string) {
        if (activeTab.value === tab) {
            miscStore.contextInfoBarOpenTab = "";
        } else {
            miscStore.contextInfoBarOpenTab = tab;
        }
    }

    const themeIsDark = ref(localStorage.getItem("theme") === "dark")

    const onSwitchTheme = () => {
        themeIsDark.value = !themeIsDark.value;
        const theme = themeIsDark.value ? "dark" : "light";
        Utils.switchTheme(miscStore, theme);
    }
</script>

<style scoped lang="scss">
    @use 'element-plus/theme-chalk/src/mixins/mixins' as *;

    .barResizer {
        height: 100vh;
        width: 5px;
        position: absolute;
        top: 0;
        left: 0;
        z-index: 1040;
        background-color: var(--ks-button-background-primary);
        opacity: 0;
        transition: opacity .1s;
        border: none;
        cursor: col-resize;

        &:hover {
            opacity: 1;
        }
    }

    .barWrapper {
        position: relative;
        width: 4rem;
        padding: 0.75rem;
        writing-mode: vertical-rl;
        text-orientation: mixed;
        border-left: 1px solid var(--ks-border-primary);
        display: flex;
        align-items: center;
        gap: 0.5rem;
        font-size: var(--font-size-sm);
        overflow-y: auto;
        &::-webkit-scrollbar {
            width: 0;
        }
        scrollbar-width: none;

        &.opened {
            border-right: 1px solid var(--ks-border-primary);
        }

        .el-button {
            font-size: var(--font-size-sm);
            height: auto;
            padding: 10px 5px;
            width: 32px;
            position: relative;
        }

        .el-button + .el-button {
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
            color: var(--bs-text-opacity-5);
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
        transition: width .1s;
        width: 0;
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
        }

        &.panelTabResizing {
            transition: none;
        }
    }
</style>
