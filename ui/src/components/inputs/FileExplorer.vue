<template>
    <div
        class="p-2 sidebar"
        @click="tree.setCurrentKey(undefined)"
        @contextmenu.prevent="onTabContextMenu"
    >
        <div class="flex-row d-flex">
            <el-select
                v-model="filter"
                :placeholder="t('namespace files.filter')"
                filterable
                remote
                :remoteMethod="filesStore.searchFilesList"
                class="filter"
            >
                <template #prefix>
                    <Magnify />
                </template>
                <el-option
                    v-for="item in filesStore.searchResults"
                    :key="item"
                    :label="item"
                    :value="item"
                    @click.prevent.stop="chooseSearchResults(item)"
                />
            </el-select>
            <el-button-group class="d-flex">
                <el-tooltip
                    effect="light"
                    :content="t('namespace files.create.file')"
                    transition=""
                    :hideAfter="0"
                    :persistent="false"
                    popperClass="text-base"
                >
                    <el-button class="px-2" @click="toggleDialog(true, 'file')">
                        <FilePlus />
                    </el-button>
                </el-tooltip>
                <el-tooltip
                    effect="light"
                    :content="t('namespace files.create.folder')"
                    transition=""
                    :hideAfter="0"
                    :persistent="false"
                    popperClass="text-base"
                >
                    <el-button
                        class="px-2"
                        @click="toggleDialog(true, 'folder')"
                    >
                        <FolderPlus />
                    </el-button>
                </el-tooltip>
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
                <el-dropdown>
                    <el-button>
                        <PlusBox />
                    </el-button>
                    <template #dropdown>
                        <el-dropdown-menu>
                            <el-dropdown-item @click="filePicker?.click()">
                                {{ t("namespace files.import.files") }}
                            </el-dropdown-item>
                            <el-dropdown-item
                                @click="folderPicker?.click()"
                            >
                                {{ t("namespace files.import.folder") }}
                            </el-dropdown-item>
                        </el-dropdown-menu>
                    </template>
                </el-dropdown>
                <el-tooltip
                    effect="light"
                    :content="t('namespace files.export')"
                    transition=""
                    :hideAfter="0"
                    :persistent="false"
                    popperClass="text-base"
                >
                    <el-button class="px-2" @click="exportFiles()">
                        <FolderDownloadOutline />
                    </el-button>
                </el-tooltip>
            </el-button-group>
        </div>

        <el-tree
            ref="tree"
            lazy
            :load="filesStore.loadNodes"
            :data="filesStore.fileTree"
            highlightCurrent
            :allowDrop="
                (_: any, drop: any, dropType: string) => !drop.data?.leaf || dropType !== 'inner'
            "
            draggable
            nodeKey="id"
            v-loading="filesStore.fileTree === undefined"
            :props="{class: nodeClass, isLeaf: 'leaf'}"
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
                    <h3>{{ t("namespace files.no_items.heading") }}</h3>
                    <p>{{ t("namespace files.no_items.paragraph") }}</p>
                </div>
            </template>
            <template #default="{data, node}">
                <el-dropdown
                    :ref="(el: any) => dropdowns[data.id as string] = el"
                    @contextmenu.prevent.stop="toggleDropdown(data.id)"
                    trigger="contextmenu"
                    class="w-100"
                >
                    <el-row
                        justify="space-between"
                        class="w-100"
                        @click="(event: MouseEvent) => handleNodeClick(data, node, event)"
                    >
                        <el-col class="w-100">
                            <TypeIcon
                                :name="data.fileName"
                                :folder="!data.leaf"
                                class="me-2"
                            />
                            <span class="filename"> {{ data.fileName }}</span>
                        </el-col>
                    </el-row>
                    <template #dropdown>
                        <el-dropdown-menu>
                            <el-dropdown-item
                                v-if="!data.leaf && !multiSelected"
                                @click="toggleDialog(true, 'file', node)"
                            >
                                {{ t("namespace files.create.file") }}
                            </el-dropdown-item>
                            <el-dropdown-item
                                v-if="!data.leaf && !multiSelected"
                                @click="toggleDialog(true, 'folder', node)"
                            >
                                {{ t("namespace files.create.folder") }}
                            </el-dropdown-item>
                            <el-dropdown-item v-if="data.leaf && !multiSelected" @click="showRevisionsHistory(data)">
                                {{ t("namespace files.revisions.history") }}
                            </el-dropdown-item>
                            <el-dropdown-item v-if="!multiSelected" @click="copyPath(data)">
                                {{ t("namespace files.path.copy") }}
                            </el-dropdown-item>
                            <el-dropdown-item v-if="data.leaf && !multiSelected" @click="exportFile(node, data)">
                                {{ t("namespace files.export_single") }}
                            </el-dropdown-item>
                            <el-dropdown-item
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
                                    t(
                                        `namespace files.rename.${
                                            !data.leaf ? "folder" : "file"
                                        }`,
                                    )
                                }}
                            </el-dropdown-item>
                            <el-dropdown-item @click="removeSelectedFiles()">
                                {{
                                    selectedNodes.length <= 1 ? t(
                                        `namespace files.delete.${
                                            !data.leaf ? "folder" : "file"
                                        }`,
                                    ) : t(
                                        `namespace files.delete.${
                                            !data.leaf ? "folders" : "files"
                                        }`
                                        , {count: selectedNodes.length})
                                }}
                            </el-dropdown-item>
                        </el-dropdown-menu>
                    </template>
                </el-dropdown>
            </template>
        </el-tree>

        <!-- Creation dialog -->
        <el-dialog
            v-model="dialog.visible"
            :title="
                dialog.type === 'file'
                    ? t('namespace files.create.file')
                    : t('namespace files.create.folder')
            "
            width="500"
            @keydown.enter.prevent="dialog.name ? dialogHandler() : undefined"
        >
            <div class="pb-1">
                <span>
                    {{ t(`namespace files.dialog.name.${dialog.type}`) }}
                </span>
            </div>
            <el-input
                ref="creation_name"
                v-model="dialog.name"
                size="large"
                class="mb-3"
            />

            <div class="py-1">
                <span>
                    {{ t("namespace files.dialog.parent_folder") }}
                </span>
            </div>
            <el-select
                v-model="dialog.folder"
                clearable
                size="large"
                class="mb-3"
            >
                <el-option
                    v-for="folder in filesStore.folders"
                    :key="folder"
                    :value="folder"
                    :label="folder"
                />
            </el-select>
            <template #footer>
                <div>
                    <el-button @click="toggleDialog(false)">
                        {{ t("cancel") }}
                    </el-button>
                    <el-button
                        type="primary"
                        :disabled="!dialog.name"
                        @click="dialogHandler"
                    >
                        {{ t("namespace files.create.label") }}
                    </el-button>
                </div>
            </template>
        </el-dialog>

        <!-- Renaming dialog -->
        <el-dialog
            v-model="renameDialog.visible"
            :title="t(`namespace files.rename.${renameDialog.type}`)"
            width="500"
            @keydown.enter.prevent="renameItem()"
        >
            <div class="pb-1">
                <span>
                    {{ t(`namespace files.rename.new_${renameDialog.type}`) }}
                </span>
            </div>
            <el-input
                ref="renaming_name"
                v-model="renameDialog.name"
                size="large"
                class="mb-3"
            />
            <template #footer>
                <div>
                    <el-button @click="toggleRenameDialog(false)">
                        {{ t("cancel") }}
                    </el-button>
                    <el-button
                        type="primary"
                        :disabled="!renameDialog.name"
                        @click="renameItem()"
                    >
                        {{ t("namespace files.rename.label") }}
                    </el-button>
                </div>
            </template>
        </el-dialog>

        <el-dialog
            v-model="confirmation.visible"
            :title="confirmationLabels.title"
            width="500"
            @keydown.enter.prevent="removeItems()"
        >
            <span class="py-3" v-html="confirmationLabels.message" />
            <template #footer>
                <div>
                    <el-button @click="confirmation.visible = false">
                        {{ t("cancel") }}
                    </el-button>
                    <el-button type="primary" @click="removeItems()">
                        {{ t("namespace files.dialog.deletion.confirm") }}
                    </el-button>
                </div>
            </template>
        </el-dialog>

        <el-dialog
            v-model="revisionsHistory.visible"
            :title="t('namespace files.revisions.history')"
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
        </el-dialog>

        <el-menu
            v-if="tabContextMenu.visible"
            :style="{
                left: `${tabContextMenu.x}px`,
                top: `${tabContextMenu.y}px`,
            }"
            class="tabs-context"
        >
            <el-menu-item @click="toggleDialog(true, 'file')">
                {{ t("namespace files.create.file") }}
            </el-menu-item>
            <el-menu-item @click="toggleDialog(true, 'folder')">
                {{ t("namespace files.create.folder") }}
            </el-menu-item>
        </el-menu>
    </div>
</template>

<script lang="ts">
    import {InjectionKey} from "vue";
    import {EditorTabProps} from "./EditorWrapper.vue";

    export const FILES_OPEN_TAB_INJECTION_KEY = Symbol("files-open-tab-injection-key") as InjectionKey<(tab: EditorTabProps) => void>;
    export const FILES_CLOSE_TAB_INJECTION_KEY = Symbol("files-close-tab-injection-key") as InjectionKey<(tab: {path: string}) => void>;
</script>

<script lang="ts" setup>
    import {ref, computed, onMounted, onBeforeUnmount, nextTick, inject, watch} from "vue";
    import {useRoute} from "vue-router";
    import {useNamespacesStore} from "override/stores/namespaces";
    import Utils from "../../utils/utils";
    import FileExplorerEmpty from "../../assets/icons/file_explorer_empty.svg";
    import Magnify from "vue-material-design-icons/Magnify.vue";
    import FilePlus from "vue-material-design-icons/FilePlus.vue";
    import FolderPlus from "vue-material-design-icons/FolderPlus.vue";
    import PlusBox from "vue-material-design-icons/PlusBox.vue";
    import FolderDownloadOutline from "vue-material-design-icons/FolderDownloadOutline.vue";
    import TypeIcon from "../utils/icons/Type.vue";
    import {useI18n} from "vue-i18n";
    import {useToast} from "../../utils/toast";
    import {
        ElTreeNode,
        getFileNameWithExtension,
        isDirectory, 
        TreeNode,
        TreeNodeFile,
        useFileExplorerStore
    } from "../../stores/fileExplorer";
    import Revisions, {Revision} from "../layout/Revisions.vue";
    import Crud from "override/components/auth/Crud.vue";

    const DIALOG_DEFAULTS:Dialog = {
        visible: false,
        type: "file",
        name: undefined,
        folder: undefined,
        path: undefined,
    };

    const RENAME_DEFAULTS:Dialog = {
        visible: false,
        type: "file",
        name: undefined,
        old: undefined,
    };

    const props = defineProps<{
        currentNS?: string | null;
    }>();

    const openTab = inject(FILES_OPEN_TAB_INJECTION_KEY);

    const route = useRoute();
    const namespacesStore = useNamespacesStore();
    const filesStore = useFileExplorerStore();

    watch(
        () => props.currentNS,
        (newNS) => {
            if(newNS){
                filesStore.namespaceId = newNS
                filesStore.loadNodes();
            }
        },
    );

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

    const filter = ref<string>("");
    const dialog = ref<Dialog>({...DIALOG_DEFAULTS});
    const renameDialog = ref<Dialog>({...RENAME_DEFAULTS});
    const tree = ref<any>();
    const filePicker = ref<HTMLInputElement>();
    const folderPicker = ref<HTMLInputElement>();
    const dropdowns = ref<Record<string, {handleClose: () => void; handleOpen: () => void}>>({});
    const revisionsHistory = ref<{ visible: boolean, path: string, revisions: Revision[] }>({visible: false, path: "", revisions: []});
    const confirmation = ref<{ visible: boolean; data?: any; nodes?: any[] }>({visible: false, data: {}});
    const nodeBeforeDrag = ref<{
        parent: string;
        path: string;
    }>();
    const tabContextMenu = ref<{ visible: boolean; x: number; y: number }>({visible: false, x: 0, y: 0});
    const selectedFiles = ref<string[]>([]);
    const selectedNodes = ref<any[]>([]);
    const lastClickedIndex = ref<number | null>(null);

    const {t} = useI18n();
    const toast = useToast();

    const namespaceId = computed<string>(() => props.currentNS ?? route.params.namespace as string);

    const multiSelected = computed(() => selectedNodes.value.length > 1);

    const confirmationLabels = computed(() => {
        const files = confirmation.value.nodes?.filter(n => n.type === "File").length ?? 0;
        const foldersCount = confirmation.value.nodes?.filter(n => n.type === "Directory").length ?? 0;
        const labels = {title: t("namespace files.dialog.deletion.title"), message: ""};
        if (foldersCount > 0 && files > 0) labels.message = t("namespace files.dialog.deletion.mixed", {folders: foldersCount, files});
        else if (foldersCount > 0) labels.message = t("namespace files.dialog.deletion.folders", {count: foldersCount});
        else labels.message = t("namespace files.dialog.deletion.files", {count: files});
        return labels;
    });

    function nodeClass(data: any) {
        if (selectedNodes.value.includes(data.id)) {
            return "node selected-tree-node";
        }
        return "node";
    }

    function flattenTree(itemsArr: TreeNode[], parentPath = ""): any[] {
        const result: any[] = [];
        for (const item of itemsArr) {
            const fullPath = `${parentPath}${item.fileName}`;
            result.push({path: fullPath, fileName: item.fileName, id: item.id});
            if (isDirectory(item) && item.children?.length > 0) {
                result.push(...flattenTree(item.children, `${fullPath}/`));
            }
        }
        return result.filter(i => i.path);
    }

    function handleNodeClick(data: any, node: ElTreeNode, event: MouseEvent | null = null) {
        const path = filesStore.getPath(node.data.id) ?? "";
        const flatList = flattenTree(filesStore.fileTree);
        const currentIndex = flatList.findIndex(item => item.path === path);
        const isCtrl = event && (event.ctrlKey || (event as any).metaKey);
        const isShift = event && event.shiftKey;

        if (isShift && lastClickedIndex.value !== null) {
            const start = Math.min(lastClickedIndex.value, currentIndex);
            const end = Math.max(lastClickedIndex.value, currentIndex);
            selectedFiles.value = flatList.slice(start, end + 1).map(item => item.path);
            selectedNodes.value = flatList.slice(start, end + 1).map(item => item.id);
        } else if (isCtrl) {
            const isSelected = selectedNodes.value.includes(node.data.id);
            if (isSelected) {
                selectedFiles.value = [...selectedFiles.value.filter(file => file !== path)];
                selectedNodes.value = [...selectedNodes.value.filter(id => id !== node.data.id)];
            } else {
                selectedFiles.value = [...selectedFiles.value, path];
                selectedNodes.value = [...selectedNodes.value, node.data.id];
            }
            lastClickedIndex.value = currentIndex;
        } else {
            selectedFiles.value = [path];
            selectedNodes.value = [node.data.id];
            lastClickedIndex.value = currentIndex;
            if (data.leaf) {
                openTab?.({
                    name: data.fileName,
                    path: path,
                    extension: data.fileName.split(".").pop(),
                    flow: false,
                    dirty: false
                });
            }
        }
    }

    async function fetchRevisionSource(revision: number): Promise<string> {
        return namespacesStore.readFile({namespace: namespaceId.value, path: revisionsHistory.value.path, revision})
    }

    async function restore(source: string) {
        await namespacesStore.saveOrCreateFile({
            namespace: namespaceId.value,
            path: revisionsHistory.value.path,
            content: source
        });

        toast.success(t("namespace files.revisions.restore.success"));

        closeTab?.({path: revisionsHistory.value.path});
        openTab?.({
            name: revisionsHistory.value.path.split("/").pop()!,
            path: revisionsHistory.value.path,
            extension: revisionsHistory.value.path.split(".").pop()!,
            flow: false,
            dirty: false
        })

        const newRevision = revisionsHistory.value.revisions.map(r => r.revision).sort((a, b) => a - b).reverse()[0] + 1;
        revisionsHistory.value.revisions = [...revisionsHistory.value.revisions, {
            revision: newRevision,
            source: source
        }];
    }

    async function removeSelectedFiles() {
        const nodes = selectedFiles.value.map((filePath) => {
            return filesStore.findNodeByPath(filePath);
        });
        confirmRemove(nodes);
    }

    function chooseSearchResults(item: string) {
        const name = item.split("/").pop()
        if(!name) return;
        openTab?.({
            name,
            extension: item.split(".").pop()!,
            path: item,
            flow: false,
            dirty: false
        });
        filter.value = "";
    }

    function toggleDropdown(id: string) {
        if(selectedNodes.value.length === 0) {
            selectedNodes.value.push(id);
            selectedFiles.value.push(filesStore.getPath(id) ?? "");
        }

        for(const dd in dropdowns.value){
            if(dd !== id){
                dropdowns.value[dd].handleClose();
            }
        };

        dropdowns.value[id]?.handleOpen()
    }

    function dialogHandler() {
        if (dialog.value.type === "file") {
            addFile({creation: true});
        } else {
            addFolder(undefined, true);
        }
    }

    function toggleDialog(isShown: boolean, type?: "file" | "folder", node?: any) {
        if (isShown) {
            let folder;
            if (node?.data?.leaf === false) {
                folder = filesStore.getPath(node.data.id);
            } else {
                const selectedNode = tree.value.getCurrentNode();
                if (selectedNode?.leaf === false) {
                    node = selectedNode.id;
                    folder = filesStore.getPath(selectedNode.id);
                }
            }
            if(!type) return
            dialog.value.visible = true;
            dialog.value.type = type;
            dialog.value.folder = folder;
            focusCreationInput();
        } else {
            dialog.value.visible = false;
            dialog.value = {...DIALOG_DEFAULTS};
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
            };
            focusRenamingInput();
        } else {
            renameDialog.value = {...RENAME_DEFAULTS};
        }
    }

    function renameItem() {
        const path = renameDialog.value.node?.data.id ? filesStore.getPath(renameDialog.value.node.data.id) ?? "" : "";
        const start = path.substring(0, path.lastIndexOf("/") + 1);
        namespacesStore.renameFileDirectory({
            namespace: namespaceId.value,
            old: `${start}${renameDialog.value.old}`,
            new: `${start}${renameDialog.value.name}`,
        });
        tree.value.getNode(renameDialog.value.node).data.fileName = renameDialog.value.name;
        renameDialog.value = {...RENAME_DEFAULTS};
    }

    async function nodeMoved(draggedNode: any) {
        try {
            await namespacesStore.moveFileDirectory({
                namespace: namespaceId.value,
                old: nodeBeforeDrag.value?.path ?? "",
                new: filesStore.getPath(draggedNode.data.id) ?? "",
            });
        } catch {
            tree.value.remove(draggedNode.data.id);
            tree.value.append(draggedNode.data, nodeBeforeDrag.value?.parent);
        }
    }

    const creation_name = ref<any>();
    const renaming_name = ref<any>();

    function focusCreationInput() {
        nextTick(() => {
            creation_name.value?.focus();
        });
    }

    function focusRenamingInput() {
        nextTick(() => {
            renaming_name.value?.focus();
        });
    }

    async function importFiles(event: Event) {
        const importedFiles = (event.target as HTMLInputElement).files;
        if (!importedFiles) return;
        try {
            filesStore.importFiles(importedFiles);
            toast.success(t("namespace files.import.success"));
        } catch {
            toast.error(t("namespace files.import.error"));
        } finally {
            (event.target as HTMLInputElement).value = "";
            dialog.value = {...DIALOG_DEFAULTS};
        }
    }

    function exportFiles() {
        namespacesStore.exportFileDirectory({
            namespace: namespaceId.value,
        });
    }

    async function addFile({file, creation, shouldReset = true}: { file?: Omit<TreeNodeFile, "id" | "type">; creation?: boolean; shouldReset?: boolean }) {
        let FILE: Omit<TreeNodeFile, "id" | "type">;
        if (creation && dialog.value.name) {
            const [fileName, extension] = getFileNameWithExtension(dialog.value.name);
            FILE = {fileName, extension, content: "", leaf: true};
        } else {
            if(!file) return;
            FILE = file;
        }

        const {path, file: createdFile} = await filesStore.addFile(FILE, dialog.value.folder, creation)
        if (creation) {
            if(path === undefined || createdFile === undefined) return;
            openTab?.({
                name: createdFile.fileName,
                path,
                extension: createdFile.extension ?? "",
                flow: false,
                dirty: false
            });
            dialog.value.folder = path.substring(0, path.lastIndexOf("/"));
        }

        if (shouldReset) {
            dialog.value = {...DIALOG_DEFAULTS};
        }
    }

    function confirmRemove(nodes: any[]) {
        confirmation.value = {
            visible: true,
            nodes: Array.isArray(nodes) ? nodes : [nodes],
        };
    }

    const closeTab = inject(FILES_CLOSE_TAB_INJECTION_KEY);

    async function removeItems() {
        if(confirmation.value.nodes === undefined) return;
        await Promise.all(confirmation.value.nodes.map(async (node) => {
            const path = filesStore.getPath(node.id) ?? "";
            try {
                await namespacesStore.deleteFileDirectory({
                    namespace: props.currentNS ?? route.params.namespace as string,
                    path,
                });
                tree.value.remove(node.id);
                closeTab?.({
                    path,
                });
            } catch (error) {
                console.error(`Failed to delete file: ${node.fileName}`, error);
                toast.error(`Failed to delete file: ${node.fileName}`);
            }
        }));
        confirmation.value = {visible: false, nodes: []};
        toast.success("Selected files deleted successfully.");
    }

    async function addFolder(folder?: {fileName: string, children?: TreeNode[]}, creation?: boolean) {
        const parentPath = dialog.value.folder || "";
        filesStore.addFolder({
            fileName: dialog.value.name ?? "unknown",
            parentPath,
            ...folder,
        }, creation)
        dialog.value = {...DIALOG_DEFAULTS};
    }

    async function showRevisionsHistory(data: TreeNode) {
        revisionsHistory.value.path = filesStore.getPath(data.id) ?? "";
        revisionsHistory.value.revisions = (await namespacesStore.fileRevisions({
            namespace: namespaceId.value,
            path: revisionsHistory.value.path
        }));
        revisionsHistory.value.visible = true;
    }

    function copyPath(name: TreeNode) {
        const path = filesStore.getPath(name.id) ?? "";
        try {
            Utils.copy(path);
            toast.success(t("namespace files.path.success"));
        } catch {
            toast.error(t("namespace files.path.error"));
        }
    }

    async function exportFile(node: TreeNode, data: {fileName: string}) {
        const content = await namespacesStore.readFile({
            path: filesStore.getPath(node.id) ?? "",
            namespace: namespaceId.value,
        });
        const blob = new Blob([content], {type: "text/plain"});
        Utils.downloadUrl(window.URL.createObjectURL(blob), data.fileName);
    }

    function onTabContextMenu(event: MouseEvent) {
        tabContextMenu.value = {
            visible: true,
            x: event.clientX,
            y: event.clientY,
        };
        document.addEventListener("click", hideTabContextMenu);
    }

    function hideTabContextMenu() {
        tabContextMenu.value.visible = false;
        document.removeEventListener("click", hideTabContextMenu);
    }

    function clearSelection() {
        selectedFiles.value = [];
        selectedNodes.value = [];
        lastClickedIndex.value = null;
    }

    onMounted(async () => {
        document.addEventListener("click", clearSelection);
    });

    onBeforeUnmount(() => {
        document.removeEventListener("click", clearSelection);
    });

</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

.sidebar {
    background: var(--ks-background-panel);
    border-right: 1px solid var(--ks-border-primary);
    overflow-x: hidden;
    min-width: calc(20% - 11px);
    width: 20%;

    :deep(.revision-history-dialog-body) {
        // We substract the dialog margins and title height (78px)
        height: calc(100vh - (var(--el-dialog-margin-top) * 2) - 78px);
    }

    .filter{
        .el-input__wrapper {
            padding-right: 0px;
        }
    }

    .empty {
        position: relative;
        top: 100px;
        text-align: center;
        color: var(--ks-content-secondary);

        & img {
            margin-bottom: 2rem;
        }

        & h3 {
            font-size: var(--font-size-lg);
            font-weight: 500;
            margin-bottom: 0.5rem;
            color: var(--ks-content-secondary);
        }

        & p {
            font-size: var(--font-size-sm);
        }
    }

    :deep(.el-button):not(.el-dialog .el-button) {
        border: 0;
        background: none;
        outline: none;
        opacity: 0.5;
        padding-left: .5rem;
        padding-right: .5rem;

        &.el-button--primary {
            opacity: 1;
        }
    }

    .hidden {
        display: none;
    }

    .filename {
        font-size: var(--el-font-size-small);

        &:hover {
            color: var(--ks-content-link-hover);
        }
    }

    ul.tabs-context {
        position: fixed;
        z-index: 9999;
        border: 1px solid var(--ks-border-primary);

        & li {
            height: 30px;
            padding: 16px;
            font-size: var(--el-font-size-small);
            color: var(--ks-content-primary);

            &:hover {
                color: var(--ks-content-secondary);
            }
        }
    }

    :deep(.el-tree) {
        height: calc(100% - 64px);
        overflow: auto;
        background: var(--ks-background-panel);

        .el-tree__empty-block {
            height: auto;
        }

        .node {
            --el-tree-node-content-height: fit-content;
            --el-tree-node-hover-bg-color: transparent;
        }

        .el-tree-node__content {
            margin-bottom: 2px !important;
            padding-left: 0 !important;
            border: 1px solid transparent;

            &:last-child{
                margin-bottom: 0px;
            }

            &:hover{
                background: none;
                border: 1px solid var(--ks-border-active);
            }
        }

        .is-expanded {
            .el-tree-node__children {
                margin-left: 11px !important;
                padding-left: 0 !important;
                border-left: 1px solid var(--ks-border-primary);
            }
        }

        .el-tree-node.is-current > .el-tree-node__content {
            min-width: fit-content;
            border: 1px solid var(--ks-border-active);
            background: var(--ks-button-background-primary);

            .filename {
                color: var(--ks-button-content-primary);
            }
        }
        .el-tree-node.selected-tree-node > .el-tree-node__content {
            background-color: var(--ks-button-background-primary);
            min-width: fit-content;
            .filename {
                color: var(--ks-button-content-primary);
            }
        }
    }
}
</style>
