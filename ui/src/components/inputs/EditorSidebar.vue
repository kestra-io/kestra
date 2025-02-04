<template>
    <div
        v-show="explorerVisible"
        class="p-2 sidebar"
        @click="$refs.tree.setCurrentKey(undefined)"
        @contextmenu.prevent="onTabContextMenu"
    >
        <div class="d-flex flex-row">
            <el-select
                v-model="filter"
                :placeholder="$t('namespace files.filter')"
                filterable
                remote
                :remote-method="searchFilesList"
                class="filter"
            >
                <template #prefix>
                    <Magnify />
                </template>
                <el-option
                    v-for="item in searchResults"
                    :key="item"
                    :label="item"
                    :value="item"
                    @click.prevent.stop="chooseSearchResults(item)"
                />
            </el-select>
            <el-button-group class="d-flex">
                <el-tooltip
                    effect="light"
                    :content="$t('namespace files.create.file')"
                    transition=""
                    :hide-after="0"
                    :persistent="false"
                    popper-class="text-base"
                >
                    <el-button class="px-2" @click="toggleDialog(true, 'file')">
                        <FilePlus />
                    </el-button>
                </el-tooltip>
                <el-tooltip
                    effect="light"
                    :content="$t('namespace files.create.folder')"
                    transition=""
                    :hide-after="0"
                    :persistent="false"
                    popper-class="text-base"
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
                            <el-dropdown-item @click="$refs.filePicker.click()">
                                {{ $t("namespace files.import.files") }}
                            </el-dropdown-item>
                            <el-dropdown-item
                                @click="$refs.folderPicker.click()"
                            >
                                {{ $t("namespace files.import.folder") }}
                            </el-dropdown-item>
                        </el-dropdown-menu>
                    </template>
                </el-dropdown>
                <el-tooltip
                    effect="light"
                    :content="$t('namespace files.export')"
                    transition=""
                    :hide-after="0"
                    :persistent="false"
                    popper-class="text-base"
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
            :load="loadNodes"
            :data="items"
            highlight-current
            :allow-drop="
                (_, drop, dropType) => !drop.data?.leaf || dropType !== 'inner'
            "
            draggable
            node-key="id"
            v-loading="items === undefined"
            :props="{class: 'node', isLeaf: 'leaf'}"
            class="mt-3"
            @node-click="
                (data, node) =>
                    data.leaf
                        ? changeOpenedTabs({
                            action: 'open',
                            name: data.fileName,
                            extension: data.fileName.split('.').pop(),
                            path: getPath(node),
                        })
                        : undefined
            "
            @node-drag-start="
                nodeBeforeDrag = {
                    parent: $event.parent.data.id,
                    path: getPath($event.data.id),
                }
            "
            @node-drop="nodeMoved"
            @keydown.delete.prevent="deleteKeystroke"
        >
            <template #empty>
                <div class="m-5 empty">
                    <img :src="FileExplorerEmpty">
                    <h3>{{ $t("namespace files.no_items.heading") }}</h3>
                    <p>{{ $t("namespace files.no_items.paragraph") }}</p>
                </div>
            </template>
            <template #default="{data, node}">
                <el-dropdown
                    :ref="`dropdown__${data.id}`"
                    @contextmenu.prevent.stop="
                        toggleDropdown(`dropdown__${data.id}`)
                    "
                    trigger="contextmenu"
                    class="w-100"
                >
                    <el-row justify="space-between" class="w-100">
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
                                v-if="!data.leaf"
                                @click="toggleDialog(true, 'file', node)"
                            >
                                {{ $t("namespace files.create.file") }}
                            </el-dropdown-item>
                            <el-dropdown-item
                                v-if="!data.leaf"
                                @click="toggleDialog(true, 'folder', node)"
                            >
                                {{ $t("namespace files.create.folder") }}
                            </el-dropdown-item>
                            <el-dropdown-item @click="copyPath(data)">
                                {{ $t("namespace files.path.copy") }}
                            </el-dropdown-item>
                            <el-dropdown-item
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
                            </el-dropdown-item>
                            <el-dropdown-item @click="confirmRemove(node)">
                                {{
                                    $t(
                                        `namespace files.delete.${
                                            !data.leaf ? "folder" : "file"
                                        }`,
                                    )
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
                    ? $t('namespace files.create.file')
                    : $t('namespace files.create.folder')
            "
            width="500"
            @keydown.enter.prevent="dialog.name ? dialogHandler() : undefined"
        >
            <div class="pb-1">
                <span>
                    {{ $t(`namespace files.dialog.name.${dialog.type}`) }}
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
                    {{ $t("namespace files.dialog.parent_folder") }}
                </span>
            </div>
            <el-select
                v-model="dialog.folder"
                clearable
                size="large"
                class="mb-3"
            >
                <el-option
                    v-for="folder in folders"
                    :key="folder"
                    :value="folder"
                    :label="folder"
                />
            </el-select>
            <template #footer>
                <div>
                    <el-button @click="toggleDialog(false)">
                        {{ $t("cancel") }}
                    </el-button>
                    <el-button
                        type="primary"
                        :disabled="!dialog.name"
                        @click="dialogHandler"
                    >
                        {{ $t("namespace files.create.label") }}
                    </el-button>
                </div>
            </template>
        </el-dialog>

        <!-- Renaming dialog -->
        <el-dialog
            v-model="renameDialog.visible"
            :title="$t(`namespace files.rename.${renameDialog.type}`)"
            width="500"
            @keydown.enter.prevent="renameItem()"
        >
            <div class="pb-1">
                <span>
                    {{ $t(`namespace files.rename.new_${renameDialog.type}`) }}
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
                        {{ $t("cancel") }}
                    </el-button>
                    <el-button
                        type="primary"
                        :disabled="!renameDialog.name"
                        @click="renameItem()"
                    >
                        {{ $t("namespace files.rename.label") }}
                    </el-button>
                </div>
            </template>
        </el-dialog>

        <el-dialog
            v-model="confirmation.visible"
            :title="
                Array.isArray(confirmation.node?.data?.children)
                    ? $t('namespace files.dialog.folder_deletion')
                    : $t('namespace files.dialog.file_deletion')
            "
            width="500"
            @keydown.enter.prevent="removeItem()"
        >
            <span class="py-3">
                {{
                    Array.isArray(confirmation.node?.data?.children)
                        ? $t(
                            "namespace files.dialog.folder_deletion_description",
                        )
                        : $t("namespace files.dialog.file_deletion_description")
                }}
            </span>
            <template #footer>
                <div>
                    <el-button @click="confirmation.visible = false">
                        {{ $t("cancel") }}
                    </el-button>
                    <el-button type="primary" @click="removeItem()">
                        {{ $t("namespace files.dialog.confirm") }}
                    </el-button>
                </div>
            </template>
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
                {{ $t("namespace files.create.file") }}
            </el-menu-item>
            <el-menu-item @click="toggleDialog(true, 'folder')">
                {{ $t("namespace files.create.folder") }}
            </el-menu-item>
        </el-menu>
    </div>
</template>

<script>
    import {mapActions, mapMutations, mapState} from "vuex";

    import Utils from "../../utils/utils";

    import FileExplorerEmpty from "../../assets/icons/file_explorer_empty.svg";

    import Magnify from "vue-material-design-icons/Magnify.vue";
    import FilePlus from "vue-material-design-icons/FilePlus.vue";
    import FolderPlus from "vue-material-design-icons/FolderPlus.vue";
    import PlusBox from "vue-material-design-icons/PlusBox.vue";
    import FolderDownloadOutline from "vue-material-design-icons/FolderDownloadOutline.vue";

    import TypeIcon from "../utils/icons/Type.vue";

    const DIALOG_DEFAULTS = {
        visible: false,
        type: "file",
        name: undefined,
        folder: undefined,
        path: undefined,
    };

    const RENAME_DEFAULTS = {
        visible: false,
        type: "file",
        name: undefined,
        old: undefined,
    };

    export default {
        props: {
            currentNS: {
                type: String,
                default: null,
            },
        },
        components: {
            Magnify,
            FilePlus,
            FolderPlus,
            PlusBox,
            FolderDownloadOutline,
            TypeIcon,
        },
        data() {
            return {
                FileExplorerEmpty,
                namespace: undefined,
                filter: "",
                dialog: {...DIALOG_DEFAULTS},
                renameDialog: {...RENAME_DEFAULTS},
                dropdownRef: "",
                tree: {allExpanded: false},
                currentFolder: undefined,
                confirmation: {visible: false, data: {}},
                items: undefined,
                nodeBeforeDrag: undefined,
                searchResults: [],
                tabContextMenu: {visible: false, x: 0, y: 0},
            };
        },
        computed: {
            ...mapState({
                flow: (state) => state.flow.flow,
                explorerVisible: (state) => state.editor.explorerVisible,
            }),
            folders() {
                function extractPaths(basePath = "", array) {
                    const paths = [];

                    array.forEach((item) => {
                        if (item.type === "Directory") {
                            const folderPath = `${basePath}${item.fileName}`;
                            paths.push(folderPath);
                            paths.push(
                                ...extractPaths(
                                    `${folderPath}/`,
                                    item.children ?? [],
                                ),
                            );
                        }
                    });
                    return paths;
                }

                return extractPaths(undefined, this.items);
            },
        },
        methods: {
            ...mapMutations("editor", [
                "toggleExplorerVisibility",
                "changeOpenedTabs",
            ]),
            ...mapActions("namespace", [
                "createDirectory",
                "readDirectory",
                "createFile",
                "searchFiles",
                "renameFileDirectory",
                "moveFileDirectory",
                "deleteFileDirectory",
                "importFileDirectory",
                "exportFileDirectory",
            ]),
            sorted(items) {
                return items.sort((a, b) => {
                    if (a.type === "Directory" && b.type !== "Directory") return -1;
                    else if (a.type !== "Directory" && b.type === "Directory")
                        return 1;

                    return a.fileName.localeCompare(b.fileName);
                });
            },
            getFileNameWithExtension(fileNameWithExtension) {
                const lastDotIdx = fileNameWithExtension.lastIndexOf(".");

                return lastDotIdx !== -1
                    ? [
                        fileNameWithExtension.slice(0, lastDotIdx),
                        fileNameWithExtension.slice(lastDotIdx + 1),
                    ]
                    : [fileNameWithExtension, ""];
            },
            renderNodes(items) {
                if (this.items === undefined) {
                    this.items = [];
                }
                for (let i = 0; i < items.length; i++) {
                    const {type, fileName} = items[i];

                    if (type === "Directory") {
                        this.addFolder({fileName});
                    } else if (type === "File") {
                        const [fileName, extension] = this.getFileNameWithExtension(
                            items[i].fileName,
                        );
                        const file = {fileName, extension, leaf: true};
                        this.addFile({file});
                    }
                }
            },
            async loadNodes(node, resolve) {
                if (node.level === 0) {
                    const payload = {
                        namespace: this.currentNS ?? this.$route.params.namespace,
                    };
                    const items = await this.readDirectory(payload);

                    this.renderNodes(items);
                    this.items = this.sorted(this.items);
                }

                if (node.level >= 1) {
                    const payload = {
                        namespace: this.currentNS ?? this.$route.params.namespace,
                        path: this.getPath(node),
                    };

                    let children = await this.readDirectory(payload);
                    children = this.sorted(
                        children.map((item) => ({
                            ...item,
                            id: Utils.uid(),
                            leaf: item.type === "File",
                        })),
                    );


                    const updateChildren = (items, path, newChildren) => {
                        items.forEach((item, index) => {
                            if (this.getPath(item.id) === path) {
                                // Update children if the fileName matches
                                items[index].children = newChildren;
                            } else if (Array.isArray(item.children)) {
                                // Recursively search in children array
                                updateChildren(item.children, path, newChildren);
                            }
                        });
                    };

                    updateChildren(
                        this.items,
                        this.getPath(node.data.id),
                        children,
                    );

                    resolve(children);
                }
            },
            async searchFilesList(value) {
                if (!value) return;

                const results = await this.searchFiles({
                    namespace: this.currentNS ?? this.$route.params.namespace,
                    query: value,
                });
                this.searchResults = results.map((result) =>
                    result.replace(/^\/*/, ""),
                );
                return this.searchResults;
            },
            chooseSearchResults(item) {
                this.changeOpenedTabs({
                    action: "open",
                    name: item.split("/").pop(),
                    extension: item.split(".").pop(),
                    path: item,
                });

                this.filter = "";
            },
            toggleDropdown(reference) {
                if (this.dropdownRef) {
                    this.$refs[this.dropdownRef]?.handleClose();
                }

                this.dropdownRef = reference;
                this.$refs[reference].handleOpen();
            },
            dialogHandler() {
                if(this.dialog.type === "file"){
                    this.addFile({creation: true})
                } else {
                    this.addFolder(undefined, true)
                }
            },
            toggleDialog(isShown, type, node) {
                if (isShown) {
                    let folder;
                    if (node?.data?.leaf === false) {
                        folder = this.getPath(node.data.id);
                    } else {
                        const selectedNode = this.$refs.tree.getCurrentNode();
                        if (selectedNode?.leaf === false) {
                            node = selectedNode.id;
                            folder = this.getPath(selectedNode.id);
                        }
                    }
                    this.dialog.visible = true;
                    this.dialog.type = type;
                    this.dialog.folder = folder;

                    this.focusCreationInput();
                } else {
                    this.dialog.visible = false;
                    this.dialog = {...DIALOG_DEFAULTS};
                }
            },
            toggleRenameDialog(isShown, type, name, node) {
                if (isShown) {
                    this.renameDialog = {
                        visible: true,
                        type,
                        name,
                        old: name,
                        node,
                    };
                    this.focusRenamingInput();
                } else {
                    this.renameDialog = {...RENAME_DEFAULTS};
                }
            },
            renameItem() {
                const path = this.getPath(this.renameDialog.node);
                const start = path.substring(0, path.lastIndexOf("/") + 1);

                this.renameFileDirectory({
                    namespace: this.currentNS ?? this.$route.params.namespace,
                    old: `${start}${this.renameDialog.old}`,
                    new: `${start}${this.renameDialog.name}`,
                    type: this.renameDialog.type,
                });

                this.$refs.tree.getNode(this.renameDialog.node).data.fileName =
                    this.renameDialog.name;
                this.renameDialog = {...RENAME_DEFAULTS};
            },
            async nodeMoved(draggedNode) {
                try {
                    await this.moveFileDirectory({
                        namespace: this.currentNS ?? this.$route.params.namespace,
                        old: this.nodeBeforeDrag.path,
                        new: this.getPath(draggedNode.data.id),
                        type: draggedNode.data.type,
                    });
                } catch {
                    this.$refs.tree.remove(draggedNode.data.id);
                    this.$refs.tree.append(
                        draggedNode.data,
                        this.nodeBeforeDrag.parent,
                    );
                }
            },
            focusCreationInput() {
                setTimeout(() => {
                    this.$refs.creation_name.focus();
                }, 10);
            },
            focusRenamingInput() {
                setTimeout(() => {
                    this.$refs.renaming_name.focus();
                }, 10);
            },

            readFile(file) {
                return new Promise((resolve, reject) => {
                    const reader = new FileReader();
                    reader.onload = () => resolve(reader.result);
                    reader.onerror = reject;
                    reader.readAsArrayBuffer(file);
                });
            },
            async importFiles(event) {
                const importedFiles = event.target.files;

                try {
                    for (const file of importedFiles) {
                        if (file.webkitRelativePath) {
                            const filePath = file.webkitRelativePath;
                            const pathParts = filePath.split("/");
                            let currentFolder = this.items;
                            let folderPath = [];

                            // Traverse through each folder level in the path
                            for (let i = 0; i < pathParts.length - 1; i++) {
                                const folderName = pathParts[i];
                                folderPath.push(folderName);

                                // Find the folder in the current folder's children array
                                const folderIndex = currentFolder.findIndex(
                                    (item) =>
                                        typeof item === "object" &&
                                        item.fileName === folderName,
                                );
                                if (folderIndex === -1) {
                                    // If the folder doesn't exist, create it
                                    const newFolder = {
                                        id: Utils.uid(),
                                        fileName: folderName,
                                        children: [],
                                        type: "Directory",
                                    };
                                    currentFolder.push(newFolder);
                                    this.sorted(currentFolder);
                                    currentFolder = newFolder.children;
                                } else {
                                    // If the folder exists, move to the next level
                                    currentFolder =
                                        currentFolder[folderIndex].children;
                                }
                            }

                            // Extract file details
                            const fileName = pathParts[pathParts.length - 1];
                            const [name, extension] =
                                this.getFileNameWithExtension(fileName);

                            // Read file content
                            const content = await this.readFile(file);

                            this.importFileDirectory({
                                namespace:
                                    this.currentNS ?? this.$route.params.namespace,
                                content,
                                path: `${folderPath}/${fileName}`,
                            });

                            // Add file to the current folder
                            currentFolder.push({
                                id: Utils.uid(),
                                fileName: `${name}${
                                    extension ? `.${extension}` : ""
                                }`,
                                extension,
                                type: "File",
                            });
                        } else {
                            // Process files at root level (not in any folder)
                            const content = await this.readFile(file);
                            const [name, extension] = this.getFileNameWithExtension(
                                file.name,
                            );

                            this.importFileDirectory({
                                namespace:
                                    this.currentNS ?? this.$route.params.namespace,
                                content,
                                path: file.name,
                            });

                            this.items.push({
                                id: Utils.uid(),
                                fileName: `${name}${
                                    extension ? `.${extension}` : ""
                                }`,
                                extension,
                                leaf: !!extension,
                                type: "File",
                            });
                        }
                    }

                    this.$toast().success(
                        this.$t("namespace files.import.success"),
                    );
                } catch {
                    this.$toast().error(this.$t("namespace files.import.error"));
                } finally {
                    event.target.value = "";
                    this.import = "file";
                    this.dialog = {...DIALOG_DEFAULTS};
                }
            },
            exportFiles() {
                this.exportFileDirectory({
                    namespace: this.currentNS ?? this.$route.params.namespace,
                });
            },
            async addFile({file, creation, shouldReset = true}) {
                let FILE;

                if (creation) {
                    const [fileName, extension] = this.getFileNameWithExtension(
                        this.dialog.name,
                    );

                    FILE = {fileName, extension, content: "", leaf: true};
                } else {
                    FILE = file;
                }

                const {fileName, extension, content, leaf} = FILE;
                const NAME = `${fileName}${extension ? `.${extension}` : ""}`;
                const NEW = {
                    id: Utils.uid(),
                    fileName: NAME,
                    extension,
                    content,
                    leaf,
                    type: "File",
                };

                const path = `${this.dialog.folder ? `${this.dialog.folder}/` : ""}${NAME}`;
                if (creation) {
                    if ((await this.searchFilesList(path)).includes(path)) {
                        this.$toast().error(
                            this.$t("namespace files.create.already_exists"),
                        );
                        return;
                    }
                    await this.createFile({
                        namespace: this.currentNS ?? this.$route.params.namespace,
                        path,
                        content,
                        name: NAME,
                        creation: true,
                    });

                    this.changeOpenedTabs({
                        action: "open",
                        name: NAME,
                        path,
                        extension: extension,
                    });

                    this.dialog.folder = path.substring(0, path.lastIndexOf("/"));
                }

                if (!this.dialog.folder) {
                    this.items.push(NEW);
                    this.items = this.sorted(this.items);
                } else {
                    const SELF = this;
                    (function pushItemToFolder(basePath = "", array, pathParts) {
                        for (const item of array) {
                            const folderPath = `${basePath}${item.fileName}`;

                            if (
                                folderPath === SELF.dialog.folder &&
                                Array.isArray(item.children)
                            ) {
                                item.children = SELF.sorted([
                                    ...item.children,
                                    NEW,
                                ]);
                                return true; // Return true if the folder is found and item is pushed
                            }

                            if (
                                Array.isArray(item.children) &&
                                pushItemToFolder(
                                    `${folderPath}/`,
                                    item.children,
                                    pathParts.slice(1),
                                )
                            ) {
                                // Return true if the folder is found and item is pushed in recursive call
                                return true;
                            }
                        }

                        // If the folder does not exist, create it
                        if (pathParts && pathParts.length > 0 && pathParts[0]) {
                            const folderPath = `${basePath}${pathParts[0]}`;

                            if (folderPath === SELF.dialog.folder) {
                                const newFolder = SELF.folderNode(pathParts[0], [
                                    NEW,
                                ]);
                                array.push(newFolder);
                                array = SELF.sorted(array);

                                return true; // Return true if the folder is found and item is pushed
                            }
                            const newFolder = SELF.folderNode(pathParts[0], []);
                            array.push(newFolder);
                            array = SELF.sorted(array);

                            return pushItemToFolder(
                                `${basePath}${pathParts[0]}/`,
                                newFolder.children,
                                pathParts.slice(1),
                            );
                        }

                        return false;
                    })(undefined, this.items, path.split("/"));
                }

                if (shouldReset) {
                    this.dialog = {...DIALOG_DEFAULTS};
                }
            },
            confirmRemove(node) {
                this.confirmation = {visible: true, node};
            },
            async removeItem() {
                const {
                    node,
                    node: {data},
                } = this.confirmation;

                await this.deleteFileDirectory({
                    namespace: this.currentNS ?? this.$route.params.namespace,
                    path: this.getPath(node),
                    name: data.fileName,
                    type: data.type,
                });

                this.$refs.tree.remove(data.id);

                this.changeOpenedTabs({
                    action: "close",
                    name: data.fileName,
                });

                this.confirmation = {visible: false, node: undefined};
            },
            deleteKeystroke() {
                if (this.$refs.tree.getCurrentNode()) {
                    this.confirmRemove(
                        this.$refs.tree.getNode(
                            this.$refs.tree.getCurrentNode().id,
                        ),
                    );
                }
            },
            async addFolder(folder, creation) {
                const {fileName} = folder
                    ? folder
                    : {
                        fileName: this.dialog.name,
                    };

                const NEW = this.folderNode(fileName, folder?.children ?? []);

                if (creation) {
                    const path = `${
                        this.dialog.folder ? `${this.dialog.folder}/` : ""
                    }${fileName}`;

                    await this.createDirectory({
                        namespace: this.currentNS ?? this.$route.params.namespace,
                        path,
                        name: fileName,
                    });
                }

                if (!this.dialog.folder) {
                    const firstFolder = NEW.fileName.split("/")[0];
                    if (!this.items.find(item => item.fileName === firstFolder)) {
                        NEW.fileName = firstFolder;
                        this.items.push(NEW);
                        this.items = this.sorted(this.items);
                    }
                } else {
                    const SELF = this;
                    (function pushItemToFolder(basePath = "", array) {
                        for (let i = 0; i < array.length; i++) {
                            const item = array[i];
                            const folderPath = `${basePath}${item.fileName}`;
                            if (
                                folderPath === SELF.dialog.folder &&
                                Array.isArray(item.children)
                            ) {
                                // find the first node that is not present in the current tree and then add it.

                                const paths = NEW.fileName.split("/");
                                let index = 0;
                                let UNCOMMON_NODE = item;

                                while (UNCOMMON_NODE && index < paths.length) {
                                    // if any of node's children have path's folder name move ahead;
                                    if (index >= paths.length) break;

                                    const nextNode = UNCOMMON_NODE.children?.find(item => item.fileName.toLowerCase() === paths[index].toLowerCase());

                                    if (!nextNode) {
                                        break;
                                    }

                                    index++;
                                    UNCOMMON_NODE = nextNode;
                                }

                                // return as all folders are already present so no change required.
                                if (index === paths.length) return true;

                                // add the node with last folder name which is not present already.
                                NEW.fileName = paths[index];

                                if (!UNCOMMON_NODE.children) UNCOMMON_NODE.children = [];
                                UNCOMMON_NODE.children.push(NEW);
                                UNCOMMON_NODE.children = SELF.sorted(UNCOMMON_NODE.children);
                                return true; // Return true if the folder is found and item is pushed
                            } else if (Array.isArray(item.children)) {
                                if (
                                    pushItemToFolder(
                                        `${folderPath}/`,
                                        item.children,
                                    )
                                ) {
                                    return true; // Return true if the folder is found and item is pushed in recursive call
                                }
                            }
                        }
                        return false; // Return false if the folder is not found
                    })(undefined, this.items);
                }

                this.dialog = {...DIALOG_DEFAULTS};
            },
            folderNode(fileName, children) {
                return {
                    id: Utils.uid(),
                    fileName,
                    leaf: false,
                    children: children ?? [],
                    type: "Directory",
                };
            },
            getPath(name) {
                const nodes = this.$refs.tree.getNodePath(name);
                return nodes.map((obj) => obj.fileName).join("/");
            },
            copyPath(name) {
                const path = this.getPath(name);

                try {
                    Utils.copy(path);
                    this.$toast().success(this.$t("namespace files.path.success"));
                } catch {
                    this.$toast().error(this.$t("namespace files.path.error"));
                }
            },
            onTabContextMenu(event) {
                this.tabContextMenu = {
                    visible: true,
                    x: event.clientX,
                    y: event.clientY,
                };

                document.addEventListener("click", this.hideTabContextMenu);
            },
            hideTabContextMenu() {
                this.tabContextMenu.visible = false;
                document.removeEventListener("click", this.hideTabContextMenu);
            },
        },
        watch: {
            flow: {
                handler(flow) {
                    if (flow) {
                        this.changeOpenedTabs({
                            action: "open",
                            name: "Flow",
                            path: "Flow.yaml",
                            persistent: true,
                            flow: true,
                        });
                    }
                },
                immediate: true,
                deep: true,
            },
        },
    };
</script>

<style lang="scss">
.filter .el-input__wrapper {
    padding-right: 0px;
}

.el-tree {
    height: calc(100% - 64px);
    overflow: auto;

    .el-tree__empty-block {
        height: auto;
    }

    &::-webkit-scrollbar-thumb {
        background: var(--ks-button-background-primary);
        border-radius: 5px;

        html.dark & {
            background:  var(--ks-button-background-primary);
        }
    }

    .node {
        --el-tree-node-content-height: 36px;
        --el-tree-node-hover-bg-color: transparent;
        line-height: 36px;
    }

    .el-tree-node.is-current > .el-tree-node__content {
            min-width: fit-content;
    }
}
</style>

<style lang="scss" scoped>
@import "@kestra-io/ui-libs/src/scss/variables";

.sidebar {
    background: var(--ks-background-card);
    border-right: 1px solid var(--ks-border-primary);
    overflow-x: hidden;
    min-width: calc(30% - 8px);

    .empty {
        position: relative;
        top: 100px;
        text-align: center;
        color: white;

        html.light & {
            color: $tertiary;
        }

        & img {
            margin-bottom: 2rem;
        }

        & h3 {
            font-size: var(--font-size-lg);
            font-weight: 500;
            margin-bottom: 0.5rem;
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
        color: var(--ks-content-primary);

        &:hover {
            color: var(--ks-content-link);
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
            color: var(--bs-gray-900);

            &:hover {
                color: var(--ks-content-secondary);
            }
        }
    }
}
</style>
