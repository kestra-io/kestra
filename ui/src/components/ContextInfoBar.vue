<template>
    <div class="barWrapper" :class="{opened: activeTab?.length > 0}">
        <button v-if="activeTab.length" class="barResizer" ref="resizeHandle" @mousedown="startResizing" />

        <el-button
            v-for="(button, key) of buttonsList"
            :key="key"
            :type="activeTab === key ? 'primary' : 'default'"
            :tag="button.url ? 'a' : 'button'"
            :href="button.url"
            @click="() => {if(!button.url){ setActiveTab(key)}}"
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
            :hide-after="0"
            :disabled="!configs.commitId"
        >
            <template #content>
                <code>{{ configs.commitId }}</code> <DateAgo v-if="configs.commitDate" :inverted="true" :date="configs.commitDate" />
            </template>
            <span class="versionNumber">{{ configs?.version }}</span>
        </el-tooltip>
        <el-button class="theme-switcher" @click="onSwitchTheme">
            <WeatherNight v-if="themeIsDark" />
            <WeatherSunny v-else />
        </el-button>
    </div>
    <div class="panelWrapper" :class="{panelTabResizing: resizing}" :style="{width: activeTab?.length ? `${panelWidth}px` : 0}">
        <div :style="{overflow: 'hidden'}">
            <button v-if="activeTab.length" class="closeButton" @click="setActiveTab('')">
                <Close />
            </button>
            <ContextDocs v-if="activeTab === 'docs'" />
            <ContextNews v-else-if="activeTab === 'news'" />
            <template v-else>
                {{ activeTab }}
            </template>
        </div>
    </div>
</template>

<script lang="ts" setup>
    import {computed, ref, watch, type Ref, type Component} from "vue";
    import {useMouse, watchThrottled} from "@vueuse/core"
    import ContextDocs from "./docs/ContextDocs.vue"
    import ContextNews from "./layout/ContextNews.vue"
    import DateAgo from "./layout/DateAgo.vue"

    import MessageOutline from "vue-material-design-icons/MessageOutline.vue"
    import FileDocument from "vue-material-design-icons/FileDocument.vue"
    import Slack from "vue-material-design-icons/Slack.vue"
    import Github from "vue-material-design-icons/Github.vue"
    import Calendar from "vue-material-design-icons/Calendar.vue"
    import Close from "vue-material-design-icons/Close.vue"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import WeatherSunny from "vue-material-design-icons/WeatherSunny.vue"
    import WeatherNight from "vue-material-design-icons/WeatherNight.vue"

    import {useStorage} from "@vueuse/core"
    import {useStore} from "vuex";
    import {useI18n} from "vue-i18n";
    import Utils from "../utils/utils";

    const {t} = useI18n({useScope: "global"});

    const store = useStore();

    const configs = computed(() => store.getters["misc/configs"]);
    const activeTab = computed(() => store.getters["misc/contextInfoBarOpenTab"])

    const lastNewsReadDate = useStorage<string | null>("feeds", null)

    const hasUnread = computed(() => {
        const feeds = store.state.misc.feeds
        return (
            lastNewsReadDate.value === null ||
            (feeds?.[0] && (new Date(lastNewsReadDate.value) < new Date(feeds[0].publicationDate)))
        )
    })

    const buttonsList: Record<string, {
        title:string,
        icon: Component,
        component?: Component,
        url?: string,
        hasUnreadMarker?: boolean
    }> = {
        news: {
            title: t("contextBar.news"),
            icon: MessageOutline,
            component: ContextNews,
            hasUnreadMarker: true
        },
        docs: {
            title: t("contextBar.docs"),
            icon: FileDocument,
            component: ContextDocs
        },
        help: {
            title: t("contextBar.help"),
            icon: Slack,
            url: "https://kestra.io/slack"
        },
        issue: {
            title: t("contextBar.issue"),
            icon: Github,
            url: "https://github.com/kestra-io/kestra/issues/new/choose"
        },
        demo: {
            title: t("contextBar.demo"),
            icon: Calendar,
            url: "https://kestra.io/demo"
        }
    }

    const panelWidth = ref(640)

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
            store.commit("misc/setContextInfoBarOpenTab", "")
        } else {
            store.commit("misc/setContextInfoBarOpenTab", tab)
        }
    }

    const themeIsDark = ref(localStorage.getItem("theme") === "dark")

    const onSwitchTheme = () => {
        themeIsDark.value = !themeIsDark.value;
        Utils.switchTheme(themeIsDark.value ? "dark" : "light");
    }
</script>

<style lang="scss" scoped>
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
            background-color: var(--content-alert);
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
