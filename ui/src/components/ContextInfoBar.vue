<script lang="ts" setup>
    import {computed, ref, watch, type Ref} from "vue";
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

    import {useStorage} from "@vueuse/core"
    import {useStore} from "vuex";

    const store = useStore();

    const configs = computed(() => store.state.misc.configs);

    const lastNewsReadDate = useStorage<string | null>("feeds", null)

    const hasUnread = computed(() => {
        const feeds = store.state.misc.feeds
        return (
            lastNewsReadDate.value === null ||
            (feeds?.[0] && (new Date(lastNewsReadDate.value) < new Date(feeds[0].publicationDate)))
        )
    })

    const panelWidth = ref(640)

    const activeTab = ref("")

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

    function setActiveTab(tab: string){
        if(activeTab.value === tab){
            activeTab.value = ""
        }else{
            activeTab.value = tab
        }
    }
</script>

<template>
    <div class="barWrapper">
        <button v-if="activeTab.length" class="barResizer" ref="resizeHandle" @mousedown="startResizing" />
        <button class="barButton" :class="{barButtonActive: activeTab === 'news'}" @click="() => setActiveTab('news')">
            <MessageOutline class="buttonIcon" />News
            <div v-if="hasUnread" class="newsDot" />
        </button>
        <button class="barButton" :class="{barButtonActive: activeTab === 'docs'}" @click="() => setActiveTab('docs')">
            <FileDocument class="buttonIcon" />Docs
        </button>
        <a href="https://kestra.io/slack" target="_blank" class="barButton">
            <Slack class="buttonIcon" />Help<OpenInNew class="openIcon" />
        </a>
        <a href="https://github.com/kestra-io/kestra/issues/new/choose" target="_blank" class="barButton">
            <Github class="buttonIcon" />Open an Issue<OpenInNew class="openIcon" />
        </a>
        <a href="https://kestra.io/demo" target="_blank" class="barButton">
            <Calendar class="buttonIcon" />Get a demo<OpenInNew class="openIcon" />
        </a>
        <div style="flex:1" />
        <span class="versionNumber">{{ configs?.version }}</span>
        <el-tooltip
            v-if="configs?.commitId"
            effect="light"
            :persistent="false"
            transition=""
            :hide-after="0"
            placement="left"
        >
            <template #content>
                <DateAgo :inverted="true" :date="configs.commitDate" />
            </template>
            <span class="commitNumber">{{ configs?.commitId }}</span>
        </el-tooltip>
    </div>
    <div class="panelWrapper" :class="{panelTabResizing: resizing}" :style="{width: activeTab?.length ? `${panelWidth}px` : 0}">
        <div :style="{overflow: 'hidden', width:`${panelWidth}px`}">
            <button v-if="activeTab.length" class="closeButton" @click="activeTab = ''">
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

<style scoped>
.barResizer{
    height: 100vh;
    width: 10px;
    position: absolute;
    top: 0;
    left: -5px;
    z-index: 1040;
    background-color: var(--bs-primary);
    background: linear-gradient(90deg, transparent 0%, var(--bs-primary) 50%, transparent 100%);
    opacity: 0;
    transition: opacity .1s;
    border: none;
    cursor: col-resize;
}

.barResizer:hover{
    opacity: 1;
}

.barWrapper {
    position: relative;
    width: 65px;
    padding: 16px;
    writing-mode: vertical-rl;
    text-orientation: mixed;
    border-left: 1px solid var(--el-border-color);
    display: flex;
    align-items: center;
    gap: 8px;
    background: var(--card-bg);
}

.barWrapper .barButton{
    background:  var(--bs-border-color);
    color: var(--bs-body-color);
    border-radius: 5px;
    border: 1px solid var(--el-border-color);
    padding: 10px 5px;
    width: 32px;
    display: block;
    line-height: normal;
    white-space: nowrap;
    position: relative;
}

.barWrapper .barButton:hover{
    border-color: var(--bs-primary);
}

.barWrapper .barButtonActive{
    background: var(--bs-primary);
    border-color: var(--bs-primary);
    color: white;
    html.dark & {
        color: var(--bs-primary-color);
    }
}

.newsDot{
    width: 10px;
    height: 10px;
    background: #FD7278;
    border: 2px solid white;
    border-radius: 50%;
    display: block;
    position: absolute;
    bottom: -4px;
    right: -4px;
    html.dark & {
        border-color: #2F3342;
    }
}

.buttonIcon{
    transform: rotate(90deg);
    margin-bottom: 8px;
}

.openIcon{
    transform: rotate(90deg);
    margin-top: 8px;
    color: var(--bs-tertiary-color);
    font-size: 12px;
}

.versionNumber{
    font-size: 12px;
    color: var(--bs-tertiary-color);
    margin-top: var(--spacer);
}

.commitNumber{
    font-size: 12px;
    color: var(--bs-primary);
    margin-top: calc(.5 * var(--spacer));
}

.panelWrapper{
    transition: width .3s;
    width: 0;
    position: relative;
    overflow-y: auto;
}

.panelWrapper .closeButton{
    position: fixed;
    top: var(--spacer);
    right: var(--spacer);
    color: var(--bs-tertiary-color);
    background: none;
    border: none;
}

.panelTabResizing{
    transition: none;
}
</style>
