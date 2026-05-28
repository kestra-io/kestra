<template>
    <div
        class="p-2 sidebar"
        @contextmenu.prevent="onTabContextMenu"
        @click="onRootClick"
    >
        <div class="flex-row d-flex">
            <KsSelect
                v-model="filter"
                :placeholder="$t('namespace files.filter')"
                filterable
                remote
                :remoteMethod="filesStore.searchFilesList"
                class="filter"
            >
                <template #prefix>
                    <Magnify />
                </template>
                <KsOption
                    v-for="item in filesStore.searchResults"
                    :key="item"
                    :label="item"
                    :value="item"
                    @click.prevent.stop="chooseSearchResults(item)"
                />
            </KsSelect>
            <KsButtonGroup class="d-flex">
                <KsTooltip
                    :content="$t('namespace files.create.file')"
                >
                    <KsButton class="px-2" @click="toggleDialog(true, 'file')">
                        <FilePlus />
                    </KsButton>
                </KsTooltip>
                <KsTooltip
                    :content="$t('namespace files.create.folder')"
                >
                    <KsButton
                        class="px-2"
                        @click="toggleDialog(true, 'folder')"
                    >
                        <FolderPlus />
                    </KsButton>
                </KsTooltip>
                <input
                    ref="filePicker"
                    type="file"
                    multiple
                    class="hidden"
                    @change="importFiles"
                >
                <input
                    ref="folderPicker"
                    type="file"
                    webkitdirectory
                    mozdirectory
                    msdirectory
                    odirectory
                    directory
                    class="hidden"
                    @change="importFiles"
                >
                <KsDropdown>
                    <KsButton>
                        <PlusBox />
                    </KsButton>
                    <template #dropdown>
                        <KsDropdownMenu>
                            <KsDropdownItem @click="filePicker?.click()">
                                {{ $t("namespace files.import.files") }}
                            </KsDropdownItem>
                            <KsDropdownItem
                                @click="folderPicker?.click()"
                            >
                                {{ $t("namespace files.import.folder") }}
                            </KsDropdownItem>
                        </KsDropdownMenu>
                    </template>
                </KsDropdown>
                <KsTooltip
                    :content="$t('namespace files.export')"
                >
                    <KsButton class="px-2" @click="exportFiles()">
                        <FolderDownloadOutline />
                    </KsButton>
                </KsTooltip>
            </KsButtonGroup>
        </div>

        <KsTree
            ref="tree"
            lazy
            :load="filesStore.loadNodes"
            :data="filesStore.fileTree"
            :allowDrop="
                (_: any, drop: any, dropType: string) => !drop.data?.leaf || dropType !== 'inner'
            "
            draggable
            nodeKey="id"
            v-ks-loading="filesStore.fileTree === undefined"
            :props="({class: nodeClass, isLeaf: 'leaf'} as any)"
            class="mt-3"
            @node-drag-start="
                nodeBeforeDrag = {
                    parent: $event.parent.data.id,
                    path: filesStore.getPath($event.data.id) ?? '',
                }
            "
            @node-drop="nodeMoved"
            @keydown.delete.prevent="removeSelectedFiles"
        >
            <template #empty>
                <div class="m-4 empty">
                    <img alt="Empty icon" :src="FileExplorerEmpty">
                    <h3>{{ $t("namespace files.no_items.heading") }}</h3>
                    <p>{{ $t("namespace files.no_items.paragraph") }}</p>
                </div>
            </template>
            <template #default="{data, node}">
                <KsDropdown
                    :ref="(el: any) => dropdowns[data.id as string] = el"
                    @contextmenu.prevent.stop="toggleDropdown(data.id)"
                    trigger="contextmenu"
                    class="w-100"
                >
                    <div
                        class="tree-node-hitbox"
                        @mousedown.stop
                        @click.stop="(e) => { if(!selectionMode) onRowClickWrapper(data, node, e) }"
                    >
                        <div class="item-line">
                            <Checkbox
                                v-if="selectionMode"
                                class="me-2"
                                :modelValue="selectedNodes.includes(data.id)"
                                @update-model-value="checked => toggleCheckboxSelection(checked, node)"
                                @mousedown.stop
                                @click.stop
                            />
                            <TypeIcon
                                :name="data.fileName"
                                :folder="!data.leaf"
                                class="me-2"
                            />
                            <span class="filename" @click="(e) => { if(selectionMode) onRowClickWrapper(data, node, e) }">{{ data.fileName }}</span>
                        </div>
                    </div>
                    <template #dropdown>
                        <KsDropdownMenu>
                            <KsDropdownItem
                                v-if="!data.leaf && !multiSelected"
                                @click="toggleDialog(true, 'file', node)"
                            >
                                {{ $t("namespace files.create.file") }}
                            </KsDropdownItem>
                            <KsDropdownItem
                                v-if="!data.leaf && !multiSelected"
                                @click="toggleDialog(true, 'folder', node)"
                            >
                                {{ $t("namespace files.create.folder") }}
                            </KsDropdownItem>
                            <KsDropdownItem v-if="data.leaf && !multiSelected" @click="showRevisionsHistory(data)">
                                {{ $t("namespace files.revisions.history") }}
                            </KsDropdownItem>
                            <KsDropdownItem v-if="!multiSelected" @click="copyPath(data)">
                                {{ $t("namespace files.path.copy") }}
                            </KsDropdownItem>
                            <KsDropdownItem v-if="data.leaf && !multiSelected" @click="exportFile(node, data)">
                                {{ $t("namespace files.export_single") }}
                            </KsDropdownItem>
                            <KsDropdownItem
                                v-if="data.leaf && !multiSelected"
                                @click="
                                    toggleRenameDialog(
                                        true,
                                        !data.leaf ? 'folder' : 'file',
                                        data.fileName,
                                        node,
                                    )
                                "
                            >
                                {{
                                    $t(
                                        `namespace files.rename.${
                                            !data.leaf ? "folder" : "file"
                                        }`,
                                    )
                                }}
                            </KsDropdownItem>
                            <KsDropdownItem @click="removeSelectedFiles(data, node)">
                                {{
                                    selectedNodes.length <= 1 ? $t(
                                        `namespace files.delete.${
                                            !data.leaf ? "folder" : "file"
                                        }`,
                                    ) : $t(
                                        `namespace files.delete.${
                                            !data.leaf ? "folders" : "files"
                                        }`
                                        , {count: selectedNodes.length})
                                }}
                            </KsDropdownItem>
                        </KsDropdownMenu>
                    </template>
                </KsDropdown>
            </template>
        </KsTree>

        <!-- Creation dialog -->
        <KsDialog
            v-model="dialog.visible"
            :title="
                dialog.type === 'file'
                    ? $t('namespace files.create.file')
                    : $t('namespace files.create.folder')
            "
            width="500"
            @keydown.enter.prevent="dialog.name ? dialogHandler() : undefined"
            @opened="focusCreationInput"
        >
            <div class="pb-1">
                <span>
                    {{ $t(`namespace files.dialog.name.${dialog.type}`) }}
                </span>
            </div>
            <KsInput
                ref="creation_name"
                v-model="dialog.name"
                size="large"
                class="mb-3"
            />

            <div class="py-1">
                <span>
                    {{ $t("namespace files.dialog.parent_folder") }}
                </span>
            </div>
            <KsSelect
                v-model="dialog.folder"
                clearable
                size="large"
                class="mb-3"
            >
                <KsOption
                    v-for="folder in filesStore.folders"
                    :key="folder"
                    :value="folder"
                    :label="folder"
                />
            </KsSelect>
            <template #footer>
                <div>
                    <KsButton @click="toggleDialog(false)">
                        {{ $t("cancel") }}
                    </KsButton>
                    <KsButton
                        type="primary"
                        :disabled="!dialog.name"
                        @click="dialogHandler"
                    >
                        {{ $t("namespace files.create.label") }}
                    </KsButton>
                </div>
            </template>
        </KsDialog>

        <!-- Renaming dialog -->
        <KsDialog
            v-model="renameDialog.visible"
            :title="$t(`namespace files.rename.${renameDialog.type}`)"
            width="500"
            @keydown.enter.prevent="renameItem()"
            @opened="focusRenamingInput"
        >
            <div class="pb-1">
                <span>
                    {{ $t(`namespace files.rename.new_${renameDialog.type}`) }}
                </span>
            </div>
            <KsInput
                ref="renaming_name"
                v-model="renameDialog.name"
                size="large"
                class="mb-3"
            />
            <template #footer>
                <div>
                    <KsButton @click="toggleRenameDialog(false)">
                        {{ $t("cancel") }}
                    </KsButton>
                    <KsButton
                        type="primary"
                        :disabled="!renameDialog.name"
                        @click="renameItem()"
                    >
                        {{ $t("namespace files.rename.label") }}
                    </KsButton>
                </div>
            </template>
        </KsDialog>

        <KsDialog
            v-model="confirmation.visible"
            :title="confirmationLabels.title"
            width="500"
            @keydown.enter.prevent="removeItems()"
        >
            <span class="py-3" v-html="confirmationLabels.message" />
            <template #footer>
                <div>
                    <KsButton @click="confirmation.visible = false">
                        {{ $t("cancel") }}
                    </KsButton>
                    <KsButton type="primary" @click="removeItems()">
                        {{ $t("namespace files.dialog.deletion.confirm") }}
                    </KsButton>
                </div>
            </template>
        </KsDialog>

        <KsDialog
            v-model="revisionsHistory.visible"
            :title="$t('namespace files.revisions.history')"
            width="75%"
            top="10vh"
        >
            <Revisions
                v-if="revisionsHistory.visible"
                :lang="revisionsHistory.path.split('.').pop()!"
                :revisions="revisionsHistory.revisions"
                :revisionSource="fetchRevisionSource"
                @restore="restore"
                :editRouteQuery="false"
                class="revision-history-dialog-body"
            >
                <template #crud="{revision}">
                    <Crud permission="FLOW" :detail="{resourceType: 'NAMESPACE_FILE', namespace: route.params.namespace, path: revisionsHistory.path, revision}" />
                </template>
            </Revisions>
        </KsDialog>

        <KsMenu
            v-if="tabContextMenu.visible"
            :style="{
                left: `${tabContextMenu.x}px`,
                top: `${tabContextMenu.y}px`,
            }"
            class="tabs-context"
        >
            <KsMenuItem @click="toggleDialog(true, 'file')">
                {{ $t("namespace files.create.file") }}
            </KsMenuItem>
            <KsMenuItem @click="toggleDialog(true, 'folder')">
                {{ $t("namespace files.create.folder") }}
            </KsMenuItem>
        </KsMenu>
    </div>
</template>

<script lang="ts">
    import {InjectionKey} from "vue"
    import {EditorTabProps} from "./EditorWrapper.vue"

    export const FILES_OPEN_TAB_INJECTION_KEY = Symbol("files-open-tab-injection-key") as InjectionKey<(tab: EditorTabProps) => void>
    export const FILES_CLOSE_TAB_INJECTION_KEY = Symbol("files-close-tab-injection-key") as InjectionKey<(tab: {path: string}) => void>
</script>

<script lang="ts" setup>
    import {ref, computed, inject, watch} from "vue"
    import {useRoute} from "vue-router"
    import {useNamespacesStore} from "override/stores/namespaces"
    import * as Utils from "../../utils/utils"
    import FileExplorerEmpty from "../../assets/icons/file_explorer_empty.svg"
    import Magnify from "vue-material-design-icons/Magnify.vue"
    import FilePlus from "vue-material-design-icons/FilePlus.vue"
    import FolderPlus from "vue-material-design-icons/FolderPlus.vue"
    import PlusBox from "vue-material-design-icons/PlusBox.vue"
    import FolderDownloadOutline from "vue-material-design-icons/FolderDownloadOutline.vue"
    import TypeIcon from "../utils/icons/Type.vue"
    import {useI18n} from "vue-i18n"
    import {useToast} from "../../utils/toast"
    import {
        ElTreeNode,
        getFileNameWithExtension,
        isDirectory,
        TreeNode,
        TreeNodeFile,
        useFileExplorerStore,
    } from "../../stores/fileExplorer"
    import Revisions, {Revision} from "../layout/Revisions.vue"
    import Crud from "override/components/auth/Crud.vue"
    import Checkbox from "../layout/Checkbox.vue"

    const DIALOG_DEFAULTS:Dialog = {
        visible: false,
        type: "file",
        name: undefined,
        folder: undefined,
        path: undefined,
    }

    const RENAME_DEFAULTS:Dialog = {
        visible: false,
        type: "file",
        name: undefined,
        old: undefined,
    }

    const props = defineProps<{
        currentNS?: string | null;
    }>()

    const openTab = inject(FILES_OPEN_TAB_INJECTION_KEY)

    const route = useRoute()
    const namespacesStore = useNamespacesStore()
    const filesStore = useFileExplorerStore()

    watch(
        () => props.currentNS,
        (newNS) => {
            if(newNS){
                filesStore.namespaceId = newNS
                filesStore.loadNodes()
            }
        },
    )

    if(props.currentNS){
        filesStore.namespaceId = props.currentNS
    }

    interface Dialog{
        visible: boolean;
        type: "file" | "folder";
        name?: string;
        folder?: string;
        path?: string;
        old?: string;
        node?: ElTreeNode;
    }

    const filter = ref<string>("")
    const dialog = ref<Dialog>({...DIALOG_DEFAULTS})
    const renameDialog = ref<Dialog>({...RENAME_DEFAULTS})
    const tree = ref<any>()
    const filePicker = ref<HTMLInputElement>()
    const folderPicker = ref<HTMLInputElement>()
    const dropdowns = ref<Record<string, {handleClose: () => void; handleOpen: () => void}>>({})
    const revisionsHistory = ref<{ visible: boolean, path: string, revisions: Revision[] }>({visible: false, path: "", revisions: []})
    const confirmation = ref<{ visible: boolean; data?: any; nodes?: any[] }>({visible: false, data: {}})
    const nodeBeforeDrag = ref<{
        parent: string;
        path: string;
    }>()
    const tabContextMenu = ref<{ visible: boolean; x: number; y: number }>({visible: false, x: 0, y: 0})
    const selectedNodes = ref<any[]>([])
    const selectionMode = computed(() => selectedNodes.value.length > 1)
    const lastClickedIndex = ref<number | null>(null)

    const selectedFiles = computed(() => {
        return selectedNodes.value.map(id => filesStore.getPath(id)).filter((p): p is string => !!p)
    })

    const flatTree = computed(() => {
        return flattenTree(filesStore.fileTree ?? [])
    })

    const {t} = useI18n()
    const toast = useToast()

    const namespaceId = computed<string>(() => props.currentNS ?? route.params.namespace as string)

    const multiSelected = computed(() => selectedNodes.value.length > 1)

    const confirmationLabels = computed(() => {
        const files = confirmation.value.nodes?.filter(n => n.type === "File")
        const filesCount = files?.length ?? 0
        const folders = confirmation.value.nodes?.filter(n => n.type === "Directory")
        const foldersCount = folders?.length ?? 0
        const labels = {title: t("namespace files.dialog.deletion.title"), message: ""}
        if (foldersCount === 1) labels.message = t("namespace files.dialog.deletion.folder_single", {name: folders?.[0].fileName})
        else if (filesCount === 1) labels.message = t("namespace files.dialog.deletion.file_single", {name: files?.[0].fileName})
        else if (foldersCount > 0 && filesCount > 0) labels.message = t("namespace files.dialog.deletion.mixed", {folders: foldersCount, files: filesCount})
        else if (foldersCount > 0) labels.message = t("namespace files.dialog.deletion.folders", {count: foldersCount})
        else labels.message = t("namespace files.dialog.deletion.files", {count: filesCount})
        return labels
    })

    function nodeClass(data: any) {
        if (selectedNodes.value.includes(data.id)) {
            return "node selected-tree-node"
        }
        return "node"
    }

    function flattenTree(itemsArr: TreeNode[], parentPath = ""): any[] {
        const result: any[] = []
        for (const item of itemsArr) {
            const fullPath = `${parentPath}${item.fileName}`
            result.push({path: fullPath, fileName: item.fileName, id: item.id})
            if (isDirectory(item) && item.children?.length > 0) {
                result.push(...flattenTree(item.children, `${fullPath}/`))
            }
        }
        return result.filter(i => i.path)
    }

    function handleNodeClick(data: any, node: ElTreeNode, event: MouseEvent | null = null) {
        const path = filesStore.getPath(node.data.id) ?? ""
        const flatList = flatTree.value
        const currentIndex = flatList.findIndex(item => item.path === path)
        if (currentIndex === -1) return

        const isCtrl = !!event && (event.ctrlKey || event.metaKey)
        const isShift = !!event && event.shiftKey

        if (isShift) {
            let anchorIndex = lastClickedIndex.value

            if (anchorIndex === null) {
                if (selectedNodes.value.length === 1) {
                    const anchorId = selectedNodes.value[0]
                    const anchorPath = filesStore.getPath(anchorId) ?? ""
                    const idx = flatList.findIndex(i => i.path === anchorPath)
                    anchorIndex = idx !== -1 ? idx : currentIndex
                } else {
                    anchorIndex = currentIndex
                }
            }

            const start = Math.min(anchorIndex, currentIndex)
            const end = Math.max(anchorIndex, currentIndex)
            const slice = flatList.slice(start, end + 1)

            selectedNodes.value = slice.map(item => item.id)

            if (selectedNodes.value.length == 1){
                tree.value?.setCurrentKey(selectedNodes.value[0])
            }

            syncTreeCurrentKey()
            return
        }

        if (isCtrl) {
            const isSelected = selectedNodes.value.includes(node.data.id)

            if (isSelected) {
                selectedNodes.value = selectedNodes.value.filter(id => id !== node.data.id)
            } else {
                selectedNodes.value.push(node.data.id)
            }
            lastClickedIndex.value = currentIndex

            syncTreeCurrentKey()
            return
        }

        selectedNodes.value = [node.data.id]
        lastClickedIndex.value = currentIndex
        syncTreeCurrentKey()

        if (data.leaf) {
            openTab?.({
                name: data.fileName,
                path,
                extension: data.fileName.split(".").pop(),
                flow: false,
                dirty: false,
            })
        }
    }

    function onRowClickWrapper(data: TreeNode, node: ElTreeNode, event: MouseEvent) {

        const target = event.target as HTMLElement
        if (target.closest("input, .neon-checkbox, .checkbox")) {
            return
        }
        const isCtrl = event.ctrlKey || event.metaKey
        const isShift = event.shiftKey

        if (selectionMode.value && !isShift && !isCtrl) {
            selectedNodes.value = [node.data.id]

            const flatList = flatTree.value
            lastClickedIndex.value = flatList.findIndex(
                i => i.id === node.data.id,
            )

            syncTreeCurrentKey()

            if (data.leaf) {
                openTab?.({
                    name: data.fileName,
                    path: filesStore.getPath(node.data.id) ?? "",
                    extension: data.fileName.split(".").pop()!,
                    flow: false,
                    dirty: false,
                })
            }
            return
        }
        handleNodeClick(data, node, event)
    }

    function onRootClick(event: MouseEvent) {
        const target = event.target as HTMLElement
        if (target.closest(".kel-tree-node__content, .kel-tree-node, .filename, .neon-checkbox, button, input, .kel-input")) {
            return
        }
        selectedNodes.value = []
        lastClickedIndex.value = null
        syncTreeCurrentKey()
    }

    function syncTreeCurrentKey() {
        const treeRef = tree.value
        if (!treeRef) return

        if (selectedNodes.value.length === 1) {
            treeRef.setCurrentKey(selectedNodes.value[0])
        } else {
            treeRef.setCurrentKey(null)
        }
    }

    function toggleCheckboxSelection(checked: boolean, node: ElTreeNode) {
        const path = filesStore.getPath(node.data.id) ?? ""
        const nodeId = node.data.id
        if (checked) {
            if (!selectedNodes.value.includes(nodeId)) {
                selectedNodes.value.push(nodeId)
            }
            const flatList = flatTree.value
            lastClickedIndex.value = flatList.findIndex(i => i.path === path)
            syncTreeCurrentKey()
            return
        }
        selectedNodes.value = selectedNodes.value.filter(id => id !== nodeId)
        if(selectedNodes.value.length === 0){
            lastClickedIndex.value = null
        }
        syncTreeCurrentKey()
    }

    async function fetchRevisionSource(revision: number): Promise<string> {
        return (await namespacesStore.readFile({namespace: namespaceId.value, path: revisionsHistory.value.path, revision})).content ?? ""
    }

    async function restore(source: string) {
        await namespacesStore.saveOrCreateFile({
            namespace: namespaceId.value,
            path: revisionsHistory.value.path,
            content: source,
        })

        toast.success(t("namespace files.revisions.restore.success"))

        closeTab?.({path: revisionsHistory.value.path})
        openTab?.({
            name: revisionsHistory.value.path.split("/").pop()!,
            path: revisionsHistory.value.path,
            extension: revisionsHistory.value.path.split(".").pop()!,
            flow: false,
            dirty: false,
        })

        const newRevision = revisionsHistory.value.revisions.map(r => r.revision).sort((a, b) => a - b).reverse()[0] + 1
        revisionsHistory.value.revisions = [...revisionsHistory.value.revisions, {
            revision: newRevision,
            source: source,
        }]
    }

    async function removeSelectedFiles(_data?: any, node?: ElTreeNode) {
        if (selectedFiles.value.length <= 1 && node) {
            selectedNodes.value = [node.data.id]
        }
        const nodes = selectedFiles.value.map((filePath) => {
            return filesStore.findNodeByPath(filePath)
        })
        confirmRemove(nodes)
    }

    function chooseSearchResults(item: string) {
        const name = item.split("/").pop()
        if(!name) return
        openTab?.({
            name,
            extension: item.split(".").pop()!,
            path: item,
            flow: false,
            dirty: false,
        })
        filter.value = ""
    }

    function toggleDropdown(id: string) {
        const path = filesStore.getPath(id) ?? ""
        if (!selectedNodes.value.includes(id)) {
            selectedNodes.value = [id]
            const flatList = flatTree.value
            lastClickedIndex.value = flatList.findIndex(i => i.path === path)
        }

        for(const dd in dropdowns.value){
            if(dd !== id){
                dropdowns.value[dd]?.handleClose()
            }
        }
        dropdowns.value[id]?.handleOpen()
    }

    async function dialogHandler() {
        if (dialog.value.type === "file") {
            await addFile({creation: true})
        } else {
            await addFolder(undefined, true)
        }
    }

    function toggleDialog(isShown: boolean, type?: "file" | "folder", node?: any) {
        if (isShown) {
            let folder
            if (node?.data?.leaf === false) {
                folder = filesStore.getPath(node.data.id)
            } else {
                const selectedKey = tree.value.getCurrentKey ? tree.value.getCurrentKey() : null
                const selectedNode = selectedKey ? tree.value.getNode(selectedKey) : null
                if (selectedNode?.leaf === false) {
                    node = selectedNode.id
                    folder = filesStore.getPath(selectedNode.id)
                }
            }
            if(!type) return
            dialog.value.visible = true
            dialog.value.type = type
            dialog.value.folder = folder
        } else {
            dialog.value.visible = false
            dialog.value = {...DIALOG_DEFAULTS}
        }
    }

    function toggleRenameDialog(isShown: boolean, type?: "file" | "folder", name?: string, node?: ElTreeNode) {
        if (isShown && type) {
            renameDialog.value = {
                visible: true,
                type,
                name,
                old: name,
                node,
            }
        } else {
            renameDialog.value = {...RENAME_DEFAULTS}
        }
    }

    function renameItem() {
        const path = renameDialog.value.node?.data.id ? filesStore.getPath(renameDialog.value.node.data.id) ?? "" : ""
        const start = path.substring(0, path.lastIndexOf("/") + 1)
        namespacesStore.renameFileDirectory({
            namespace: namespaceId.value,
            old: `${start}${renameDialog.value.old}`,
            new: `${start}${renameDialog.value.name}`,
        })
        tree.value.getNode(renameDialog.value.node).data.fileName = renameDialog.value.name
        renameDialog.value = {...RENAME_DEFAULTS}
    }

    async function nodeMoved(draggedNode: any) {
        try {
            await namespacesStore.moveFileDirectory({
                namespace: namespaceId.value,
                old: nodeBeforeDrag.value?.path ?? "",
                new: filesStore.getPath(draggedNode.data.id) ?? "",
            })
        } catch {
            tree.value.remove(draggedNode.data.id)
            tree.value.append(draggedNode.data, nodeBeforeDrag.value?.parent)
        }
    }

    const creation_name = ref<any>()
    const renaming_name = ref<any>()

    function focusCreationInput() {
        creation_name.value?.$el?.querySelector("input")?.focus()
    }

    function focusRenamingInput() {
        renaming_name.value?.$el?.querySelector("input")?.focus()
    }

    async function importFiles(event: Event) {
        const importedFiles = (event.target as HTMLInputElement).files
        if (!importedFiles) return
        try {
            filesStore.importFiles(importedFiles)
            toast.success(t("namespace files.import.success"))
        } catch {
            toast.error(t("namespace files.import.error"))
        } finally {
            (event.target as HTMLInputElement).value = ""
            dialog.value = {...DIALOG_DEFAULTS}
        }
    }

    function exportFiles() {
        namespacesStore.exportFileDirectory({
            namespace: namespaceId.value,
        })
    }

    async function addFile({file, creation, shouldReset = true}: { file?: Omit<TreeNodeFile, "id" | "type">; creation?: boolean; shouldReset?: boolean }) {
        let FILE: Omit<TreeNodeFile, "id" | "type">
        if (creation && dialog.value.name) {
            const [fileName, extension] = getFileNameWithExtension(dialog.value.name)
            FILE = {fileName, extension, content: "", leaf: true}
        } else {
            if(!file) return
            FILE = file
        }

        const {path, file: createdFile} = await filesStore.addFile(FILE, dialog.value.folder, creation)
        if (creation) {
            if(path === undefined || createdFile === undefined) return
            openTab?.({
                name: createdFile.fileName,
                path,
                extension: createdFile.extension ?? "",
                flow: false,
                dirty: false,
            })
            dialog.value.folder = path.substring(0, path.lastIndexOf("/"))
        }

        if (shouldReset) {
            dialog.value = {...DIALOG_DEFAULTS}
        }
    }

    function confirmRemove(nodes: any[]) {
        confirmation.value = {
            visible: true,
            nodes: Array.isArray(nodes) ? nodes : [nodes],
        }
    }

    const closeTab = inject(FILES_CLOSE_TAB_INJECTION_KEY)

    async function removeItems() {
        if(confirmation.value.nodes === undefined) return
        await Promise.all(confirmation.value.nodes.map(async (node) => {
            const path = filesStore.getPath(node.id) ?? ""
            try {
                await namespacesStore.deleteFileDirectory({
                    namespace: props.currentNS ?? route.params.namespace as string,
                    path,
                })
                tree.value.remove(node.id)
                closeTab?.({
                    path,
                })
            } catch (error) {
                console.error(`Failed to delete file: ${node.fileName}`, error)
                toast.error(`Failed to delete file: ${node.fileName}`)
            }
        }))
        confirmation.value = {visible: false, nodes: []}
        toast.success("Selected files deleted successfully.")
    }

    async function addFolder(folder?: {fileName: string, children?: TreeNode[]}, creation?: boolean) {
        const parentPath = dialog.value.folder || ""
        filesStore.addFolder({
            fileName: dialog.value.name ?? "unknown",
            parentPath,
            ...folder,
        }, creation)
        dialog.value = {...DIALOG_DEFAULTS}
    }

    async function showRevisionsHistory(data: TreeNode) {
        revisionsHistory.value.path = filesStore.getPath(data.id) ?? ""
        revisionsHistory.value.revisions = (await namespacesStore.fileRevisions({
            namespace: namespaceId.value,
            path: revisionsHistory.value.path,
        }))
        revisionsHistory.value.visible = true
    }

    function copyPath(name: TreeNode) {
        const path = filesStore.getPath(name.id) ?? ""
        try {
            Utils.copy(path)
            toast.success(t("namespace files.path.success"))
        } catch {
            toast.error(t("namespace files.path.error"))
        }
    }

    async function exportFile(node: TreeNode, data: {fileName: string}) {
        const {content} = await namespacesStore.readFile({
            path: filesStore.getPath(node.id) ?? "",
            namespace: namespaceId.value,
        })
        if(!content?.length)
            throw new Error("File is empty or undefined")
        const blob = new Blob([content], {type: "text/plain"})
        Utils.downloadUrl(window.URL.createObjectURL(blob), data.fileName)
    }

    function onTabContextMenu(event: MouseEvent) {
        tabContextMenu.value = {
            visible: true,
            x: event.clientX,
            y: event.clientY,
        }
        document.addEventListener("click", hideTabContextMenu)
    }

    function hideTabContextMenu() {
        tabContextMenu.value.visible = false
        document.removeEventListener("click", hideTabContextMenu)
    }

</script>

<style scoped lang="scss">

.sidebar {
    background: var(--ks-bg-surface);
    border-right: 1px solid var(--ks-border-default);
    overflow-x: hidden;
    min-width: calc(20% - 11px);
    width: 20%;

    :deep(.revision-history-dialog-body) {
        // We subtract the dialog margins and title height (78px)
        height: calc(100vh - (var(--kel-dialog-margin-top) * 2) - 78px);
    }

    .filter{
        .kel-input__wrapper {
            padding-right: 0px;
        }
    }

    .empty {
        position: relative;
        top: 100px;
        text-align: center;
        color: var(--ks-text-secondary);

        & img {
            margin-bottom: 2rem;
        }

        & h3 {
            font-size: var(--ks-font-size-lg);
            font-weight: 500;
            margin-bottom: 0.5rem;
            color: var(--ks-text-secondary);
        }

        & p {
            font-size: var(--ks-font-size-sm);
        }
    }

    :deep(.kel-button):not(.kel-dialog .kel-button) {
        border: 0;
        background: none;
        outline: none;
        opacity: 0.5;
        padding-left: .5rem;
        padding-right: .5rem;

        &.kel-button--primary {
            opacity: 1;
        }
    }

    .hidden {
        display: none;
    }

    .filename {
        font-size: var(--ks-font-size-sm);

        &:hover {
            color: var(--ks-text-link);
        }
    }

    ul.tabs-context {
        position: fixed;
        z-index: 9999;
        border: 1px solid var(--ks-border-default);

        & li {
            height: 30px;
            padding: 16px;
            font-size: var(--ks-font-size-sm);
            color: var(--ks-text-primary);

            &:hover {
                color: var(--ks-text-secondary);
            }
        }
    }

    :deep(.kel-tree) {
        height: calc(100% - 64px);
        overflow: auto;
        background: var(--ks-bg-surface);

        .kel-tree__empty-block {
            height: auto;
        }

        .node {
            --kel-tree-node-hover-bg-color: transparent;
        }

        .kel-tree-node__content {
            display: flex;
            align-items: center;
            margin-bottom: 2px !important;
            padding-left: 0 !important;
            border: 1px solid transparent;

            &:last-child{
                margin-bottom: 0px;
            }

            &:hover{
                background: none;
                border: 1px solid var(--ks-border-focus);
            }
        }

        .is-expanded {
            .kel-tree-node__children {
                margin-left: 11px !important;
                padding-left: 0 !important;
                border-left: 1px solid var(--ks-border-default);
            }
        }

        .kel-tree-node.is-current > .kel-tree-node__content {
            min-width: fit-content;
            border: 1px solid var(--ks-border-focus);
            background: var(--ks-btn-primary-bg-default);

            .filename {
                color: var(--ks-btn-primary-text);
            }
        }
        .kel-tree-node.selected-tree-node > .kel-tree-node__content {
            background-color: var(--ks-btn-primary-bg-default);
            min-width: fit-content;
            .filename {
                color: var(--ks-btn-primary-text);
            }
        }
    }

    :deep(.tree-node-hitbox) {
        width: 100%;
        flex: 1;
        display: flex;
        align-items: center;
    }

    .item-line{
        display: flex;
        align-items: center;
    }
}
</style>
