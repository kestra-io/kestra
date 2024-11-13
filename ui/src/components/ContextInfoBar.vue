<script lang="ts" setup>
    import {computed, ref, watch, type Ref} from "vue";
    import {useMouse, watchThrottled} from "@vueuse/core"
    import ContextDocs from "./docs/ContextDocs.vue"

    import MessageOutline from "vue-material-design-icons/MessageOutline.vue"
    import FileDocument from "vue-material-design-icons/FileDocument.vue"
    import Slack from "vue-material-design-icons/Slack.vue"
    import Github from "vue-material-design-icons/Github.vue"
    import Calendar from "vue-material-design-icons/Calendar.vue"
    import Close from "vue-material-design-icons/Close.vue"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"


    import {useStore} from "vuex";

    const store = useStore();

    const configs = computed(() => store.state.misc.configs);


    const panelWidth = ref(400)

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
                panelWidth.value = Math.min(Math.max(newPanelWidth, 50), window.innerWidth / 2)
            }
        }, {throttle:20})

        watch(localActiveTab, (value) => {
            if(value.length){
                x.value = 0;
            }
        })

        return {startResizing, resizing}
    }
</script>

<template>
    <div class="barWrapper">
        <button v-if="activeTab.length" class="barResizer" ref="resizeHandle" @mousedown="startResizing" />
        <button class="barButton" :class="{barButtonActive: activeTab === 'news'}" @click="() => activeTab = 'news'">
            <MessageOutline class="buttonIcon" />News
        </button>
        <button class="barButton" :class="{barButtonActive: activeTab === 'docs'}" @click="() => activeTab = 'docs'">
            <FileDocument class="buttonIcon" />Docs
        </button>
        <a href="#" class="barButton">
            <Slack class="buttonIcon" />Help<OpenInNew class="openIcon" />
        </a>
        <a href="#" class="barButton">
            <Github class="buttonIcon" />Open an Issue<OpenInNew class="openIcon" />
        </a>
        <a href="#" class="barButton">
            <Calendar class="buttonIcon" />Get a demo<OpenInNew class="openIcon" />
        </a>
        <div style="flex:1" />
        <span class="versionNumber">{{ configs?.version }}</span>
    </div>
    <div class="panelWrapper" :class="{panelTabResizing: resizing}" :style="{width: activeTab?.length ? `${panelWidth}px` : 0}">
        <button v-if="activeTab.length" class="closeButton" @click="activeTab = ''">
            <Close />
        </button>
        <ContextDocs v-if="activeTab === 'docs'" />
        <template v-else>
            {{ activeTab }}
        </template>
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
}

.barWrapper .barButton:hover{
    border-color: var(--bs-primary);
}

.barWrapper .barButtonActive{
    background: var(--bs-primary);
    color: var(--bs-primary-color);
    border-color: var(--bs-primary);
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
