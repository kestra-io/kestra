<template>
    <!--
        When the namespace has no files yet, show a dedicated empty state without
        the file browser. The splitter is kept mounted (v-show) so the explorer can
        load the tree and own the create-file dialog reused by the empty state.
    -->
    <NamespaceFilesEmpty
        v-if="filesStore.isEmpty"
        @new-file="fileExplorer?.openCreationDialog('file')"
    />
    <KsSplitter
        v-show="!filesStore.isEmpty"
        class="default-theme file-splitter"
        v-bind="$attrs"
        @resize-end="onResize"
    >
        <KsSplitterPanel
            min="10%"
            key="sideBar"
            :size="sideBarSize"
        >
            <FileExplorer
                ref="fileExplorer"
                :currentNS="namespace"
                style="width: 100%;height: 100%;"
            />
        </KsSplitterPanel>
        <KsSplitterPanel
            min="20%"
            key="editor"
            :size="editorSize"
        >
            <div v-if="!panels.length" class="namespace-files-no-selection">
                <FileDocumentOutline :size="28" />
                <KsText tag="p" class="namespace-files-no-selection-title">
                    {{ $t("namespace files.no_selection.heading") }}
                </KsText>
                <KsText tag="p" size="small" class="namespace-files-no-selection-description">
                    {{ $t("namespace files.no_selection.description") }}
                </KsText>
            </div>
            <MultiPanelTabs v-else-if="mounted" v-model="panels" />
        </KsSplitterPanel>
    </KsSplitter>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useMounted, useStorage} from "@vueuse/core"
    import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue"
    import FileExplorer from "../../inputs/FileExplorer.vue"
    import NamespaceFilesEmpty from "./NamespaceFilesEmpty.vue"
    import MultiPanelTabs from "../../MultiPanelTabs.vue"
    import {useFileExplorerStore} from "../../../stores/fileExplorer"
    import {CODE_PREFIX, getTabFromFilesTab, getTabPropsFromFilePath, useFilesPanels} from "../../flows/useFilesPanels"
    import {useFlowStore} from "../../../stores/flow"
    import {useStoredPanels} from "../../../composables/useStoredPanels"

    const mounted = useMounted()

    const props = defineProps<{
        namespace: string
    }>()

    const flowStore = useFlowStore()
    const filesStore = useFileExplorerStore()

    const fileExplorer = ref<{ openCreationDialog: (type?: "file" | "folder") => void }>()

    watch(() => props.namespace, (newVal) => {
        flowStore.flow = {
            namespace: newVal,
            id: "",
            revision: 0,
            source: `namespace: ${newVal}\n`,
            errors: [],
        }
    }, {immediate: true})

    const sideBarSize = useStorage("namespace-files-editor-view-sidebar-size", 1)
    const editorSize = useStorage("namespace-files-editor-view-editor-size", 4)

    function onResize(_index: number, sizes: number[]) {
        sideBarSize.value = sizes[0]
        editorSize.value = sizes[1]
    }

    const {panels} = useStoredPanels(
        `namespace-files-editor-view-panels-${props.namespace}`,
        [{
            deserialize: (value: string) => {
                if(value.startsWith(`${CODE_PREFIX}-`)){
                    value = value.slice(5)
                } else {
                    // not a file tab
                    return
                }
                const tabProps = getTabPropsFromFilePath(value, false)
                if(!tabProps) return

                return getTabFromFilesTab(tabProps)
            },
        }],

    )

    useFilesPanels(panels, computed(() => props.namespace))
</script>

<style lang="scss" scoped>
    .file-splitter {
        margin: 2rem;
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-lg);
        width: auto;
        height: calc(100% - 4rem);
        overflow: auto;
    }
    .namespace-files-no-selection {
        width: 100%;
        height: 100%;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: var(--ks-spacing-1);
        color: var(--ks-icon-muted);
        text-align: center;
    }

    .namespace-files-no-selection-title {
        color: var(--ks-text-primary);
    }

    .namespace-files-no-selection-description {
        color: var(--ks-text-secondary);
    }
</style>