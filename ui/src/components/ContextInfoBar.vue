<template>
    <div class="barWrapper" :class="{opened: activeTab?.length > 0}">
        <button v-if="activeTab.length" class="barResizer" ref="resizeHandle" @mousedown="startResizing" />

        <el-button :type="activeTab === 'news' ? 'primary' : 'default'" @click="() => setActiveTab('news')">
            <MessageOutline />{{ t('contextBar.news') }}
            <div v-if="hasUnread" class="newsDot" />
        </el-button>

        <el-button :type="activeTab === 'docs' ? 'primary' : 'default'" @click="() => setActiveTab('docs')">
            <FileDocument />{{ t('contextBar.docs') }}
        </el-button>

        <el-button tag="a" href="https://kestra.io/slack" target="_blank">
            <Slack />{{ t('contextBar.help') }}<OpenInNew class="opacity-25" />
        </el-button>

        <el-button tag="a" href="https://github.com/kestra-io/kestra/issues/new/choose" target="_blank">
            <Github />{{ t('contextBar.issue') }}<OpenInNew class="opacity-25" />
        </el-button>

        <el-button tag="a" href="https://kestra.io/demo" target="_blank">
            <Calendar />{{ t('contextBar.demo') }}<OpenInNew class="opacity-25" />
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
    </div>
    <div class="panelWrapper" :class="{panelTabResizing: resizing}" :style="{width: activeTab?.length ? `${panelWidth}px` : 0}">
        <div :style="{overflow: 'hidden'}">
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
    import {useI18n} from "vue-i18n";

    const {t} = useI18n();

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

    function setActiveTab(tab: string) {
        if (activeTab.value === tab) {
            activeTab.value = ""
        } else {
            activeTab.value = tab
        }
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
        background-color: var(--bs-primary);
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
        border-left: 1px solid var(--el-border-color);
        display: flex;
        align-items: center;
        gap: 0.5rem;
        font-size: var(--font-size-sm);

        &.opened {
            border-right: 1px solid var(--el-border-color);
        }

        .el-button {
            font-size: var(--font-size-sm);
            height: auto;
            padding: 10px 5px;
            width: 32px;
        }

        .el-button + .el-button {
            margin-left: 0;
        }

        .versionNumber {
            color: var(--bs-gray-400);
            html.dark & {
                color: var(--bs-gray-600);
            }
            margin-top: var(--spacer);
        }

        &:deep(.el-button .material-design-icon:not(.open-in-new-icon)) {
            transform: rotate(90deg);
            margin-bottom: 0.75rem;
        }

        &:deep(.open-in-new-icon) {
            transform: rotate(90deg);
            margin-top: 0.75rem;
            margin-bottom: 0;
            color: var(--bs-text-opacity-5);
        }

        @include res(xs) {
            display: none;
        }
    }

    .panelWrapper {
        transition: width .1s;
        width: 0;
        position: relative;
        overflow-y: auto;

        .closeButton {
            position: fixed;
            top: var(--spacer);
            right: var(--spacer);
            color: var(--bs-tertiary-color);
            background: none;
            border: none;
        }

        &.panelTabResizing {
            transition: none;
        }
    }
</style>
