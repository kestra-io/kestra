<template>
    <div v-show="explorerVisible" class="w-25 p-3 sidebar">
        <div class="d-flex flex-row">
            <el-input
                v-model="filter"
                :placeholder="$t('namespace files.filter')"
                class="filter"
            >
                <template #suffix>
                    <el-button
                        class="px-2"
                        @click="filter.length ? (filter = '') : undefined"
                    >
                        <component :is="filter.length ? 'Close' : 'Magnify'" />
                    </el-button>
                </template>
            </el-input>
            <el-button-group class="d-flex">
                <el-tooltip
                    :content="$t('namespace files.create.file')"
                    transition=""
                    :hide-after="0"
                    :persistent="false"
                >
                    <el-button class="px-2" @click="toggleDialog(true, 'file')">
                        <FilePlus />
                    </el-button>
                </el-tooltip>
                <el-tooltip
                    :content="$t('namespace files.create.folder')"
                    transition=""
                    :hide-after="0"
                    :persistent="false"
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
                    multiple
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
                                {{ $t("namespace files.import.file") }}
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
                    :content="
                        $t(
                            `namespace files.tree.${
                                tree.allExpanded ? 'collapse' : 'expand'
                            }`
                        )
                    "
                    transition=""
                    :hide-after="0"
                    :persistent="false"
                >
                    <el-button
                        class="px-2"
                        @click="toggleExpanded(!tree.allExpanded)"
                    >
                        <component
                            :is="
                                tree.allExpanded
                                    ? 'CollapseAllOutline'
                                    : 'ExpandAllOutline'
                            "
                        />
                    </el-button>
                </el-tooltip>
            </el-button-group>
        </div>

        <el-tree
            ref="tree"
            lazy
            :load="loadNodes"
            node-key="name"
            highlight-current
            draggable
            :allow-drop="(_, drop) => !drop.leaf"
            :empty-text="$t('namespace files.no_items')"
            :props="{class: 'node', isLeaf: 'leaf'}"
            class="mt-3"
            @node-click="
                (data) =>
                    data.leaf
                        ? changeOpenedTabs({
                            action: 'open',
                            name: data.fileName,
                            extension: data.fileName.split('.')[0],
                        })
                        : undefined
            "
            @node-drop="nodeMoved"
        >
            <template #default="{data, node}">
                <el-dropdown
                    :ref="`dropdown__${data.fileName}`"
                    @contextmenu.prevent.stop="
                        toggleDropdown(`dropdown__${data.fileName}`)
                    "
                    trigger="contextmenu"
                >
                    <el-row justify="space-between">
                        <el-col :span="10">
                            <span class="me-2">
                                <img
                                    :src="getIcon(!data.leaf, data.fileName)"
                                    :alt="data.extension"
                                    width="18"
                                >
                            </span>
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
                                        data.fileName
                                    )
                                "
                            >
                                {{
                                    $t(
                                        `namespace files.rename.${
                                            !data.leaf ? "folder" : "file"
                                        }`
                                    )
                                }}
                            </el-dropdown-item>
                            <el-dropdown-item @click="confirmRemove(data)">
                                {{
                                    $t(
                                        `namespace files.delete.${
                                            !data.leaf ? "folder" : "file"
                                        }`
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

            <template v-if="dialog.folder">
                <div class="py-1">
                    <span>
                        {{ $t("namespace files.dialog.parent_folder") }}
                    </span>
                </div>
                <el-select v-model="dialog.folder" size="large" class="mb-3">
                    <el-option
                        v-for="folder in folders"
                        :key="folder"
                        :value="folder"
                        :label="folder"
                    />
                </el-select>
            </template>
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
                Array.isArray(confirmation.data.children)
                    ? $t('namespace files.dialog.folder_deletion')
                    : $t('namespace files.dialog.file_deletion')
            "
            width="500"
        >
            <span class="py-3">
                {{
                    Array.isArray(confirmation.data.children)
                        ? $t(
                            "namespace files.dialog.folder_deletion_description"
                        )
                        : $t("namespace files.dialog.file_deletion_description")
                }}
            </span>
            <template #footer>
                <div>
                    <el-button @click="confirmation.visible = false">
                        {{ $t("cancel") }}
                    </el-button>
                    <el-button
                        type="primary"
                        @click="removeItem(confirmation.data.name)"
                    >
                        {{ $t("namespace files.dialog.confirm") }}
                    </el-button>
                </div>
            </template>
        </el-dialog>
    </div>
</template>

<script>
    import {mapState, mapMutations, mapActions} from "vuex";

    import Utils from "../../utils/utils";

    import Magnify from "vue-material-design-icons/Magnify.vue";
    import Close from "vue-material-design-icons/Close.vue";
    import FilePlus from "vue-material-design-icons/FilePlus.vue";
    import FolderPlus from "vue-material-design-icons/FolderPlus.vue";
    import PlusBox from "vue-material-design-icons/PlusBox.vue";
    import CollapseAllOutline from "vue-material-design-icons/CollapseAllOutline.vue";
    import ExpandAllOutline from "vue-material-design-icons/ExpandAllOutline.vue";
    import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue";
    import FolderOutline from "vue-material-design-icons/FolderOutline.vue";
    import Delete from "vue-material-design-icons/Delete.vue";

    import {getVSIFileIcon, getVSIFolderIcon} from "file-extension-icon-js";

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
        components: {
            Magnify,
            Close,
            FilePlus,
            FolderPlus,
            PlusBox,
            CollapseAllOutline,
            ExpandAllOutline,
            FileDocumentOutline,
            FolderOutline,
            Delete,
        },
        data() {
            return {
                namespace: undefined,

                filter: "",

                dialog: {...DIALOG_DEFAULTS},
                renameDialog: {...RENAME_DEFAULTS},
                dropdownRef: "",

                tree: {allExpanded: false},

                currentFolder: "",

                confirmation: {visible: false, data: {}},

                items: [],
            };
        },
        computed: {
            ...mapState({
                flows: (state) => state.flow.flows,
                explorerVisible: (state) => state.editor.explorerVisible,
            }),
            folders() {
                function extractNames(array) {
                    const names = [];

                    array.forEach((item) => {
                        if (item.children) {
                            names.push(item.name);
                            names.push(...extractNames(item.children));
                        }
                    });
                    return names;
                }

                return extractNames(this.items);
            },
            sortedItems() {
                return this.items.slice().sort((a, b) => {
                    if (a.children && !b.children) return -1;
                    else if (!a.children && b.children) return 1;

                    return a.name.localeCompare(b.name);
                });
            },
        },
        methods: {
            ...mapMutations("editor", ["changeOpenedTabs"]),
            ...mapActions("namespace", [
                "createDirectory",
                "readDirectory",
                "createFile",
                "renameFileDirectory",
                "moveFileDirectory",
                "deleteFileDirectory",
            ]),
            renderNodes(nodes) {
                for (let i = 0; i < nodes.length; i++) {
                    const {type, fileName} = nodes[i];

                    if (type === "Directory") {
                        this.addFolder({name: fileName, leaf: false});
                    } else if (type === "File") {
                        const [name, extension] = nodes[i].fileName.split(".");
                        const file = {
                            name,
                            extension,
                            content: "",
                            leaf: true,
                        };
                        this.addFile({file});
                    }
                }
            },
            async loadNodes(node, resolve) {
                if (node.level === 0) {
                    const payload = {namespace: this.$route.params.namespace};
                    const items = await this.readDirectory(payload);

                    this.renderNodes(items);

                    const resolved = items.map((item) => ({
                        ...item,
                        leaf: item.type === "File",
                    }));

                    resolve(resolved);
                }

                if (node.level >= 1) {
                    const payload = {
                        namespace: this.$route.params.namespace,
                        path: this.getPath(node),
                    };

                    const items = await this.readDirectory(payload);

                    this.renderNodes(items);

                    const resolved = items.map((item) => ({
                        ...item,
                        leaf: item.type === "File",
                    }));

                    resolve(resolved);
                }
            },
            getIcon(isFolder, name) {
                if (isFolder) return getVSIFolderIcon("folder");

                if (!name) return;

                // Making sure icon is correct for 'yml' files
                if (name.endsWith(".yml")) {
                    name = name.replace(/\.yml$/, ".yaml");
                }

                return getVSIFileIcon(name);
            },
            toggleExpanded(isExpanded) {
                Object.keys(this.$refs.tree.store.nodesMap).forEach((key) => {
                    this.$refs.tree.store.nodesMap[key].expanded = isExpanded;
                });

                this.tree.allExpanded = isExpanded;
            },
            toggleDropdown(reference) {
                if (this.dropdownRef) {
                    this.$refs[this.dropdownRef]?.handleClose();
                }

                this.dropdownRef = reference;
                this.$refs[reference].handleOpen();
            },
            dialogHandler() {
                this.dialog.type === "file"
                    ? this.addFile({creation: true})
                    : this.addFolder(undefined, true);
            },
            toggleDialog(isShown, type, node) {
                if (isShown) {
                    const selected = this.$refs.tree.getCurrentNode();
                    const folder =
                        selected && Array.isArray(selected.children)
                            ? selected.name
                            : null;

                    this.dialog.visible = true;
                    this.dialog.type = type;
                    this.dialog.folder = folder ?? node?.label ?? undefined;
                    this.dialog.path = this.getPath(node);

                    this.focusCreationInput();
                } else {
                    this.dialog.visible = false;
                    this.dialog = {...DIALOG_DEFAULTS};
                }
            },
            toggleRenameDialog(isShown, type, name) {
                if (isShown) {
                    this.renameDialog = {visible: true, type, name, old: name};
                    this.focusRenamingInput();
                } else {
                    this.renameDialog = {...RENAME_DEFAULTS};
                }
            },
            renameItem() {
                const SELF = this;
                (function rename(array) {
                    for (let i = 0; i < array.length; i++) {
                        const item = array[i];
                        if (item.name === SELF.renameDialog.old) {
                            item.name = SELF.renameDialog.name;
                            return true;
                        } else if (Array.isArray(item.children)) {
                            if (rename(item.children)) {
                                return true;
                            }
                        }
                    }
                    return false;
                })(this.items);

                this.renameFileDirectory({
                    namespace: this.$route.params.namespace,
                    old: this.renameDialog.old,
                    new: this.renameDialog.name,
                });

                this.renameDialog = {...RENAME_DEFAULTS};
            },
            nodeMoved(node, target, type) {
                if (type === "inner") {
                    this.moveFileDirectory({
                        namespace: this.$route.params.namespace,
                        old: this.getPath(node),
                        new: `${this.getPath(target)}/${this.getPath(node)}`,
                    });
                } else {
                    // console.log(node, target);
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
                    reader.readAsText(file);
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
                                        item.name === folderName
                                );
                                if (folderIndex === -1) {
                                    // If the folder doesn't exist, create it
                                    const newFolder = {
                                        name: folderName,
                                        children: [],
                                    };
                                    currentFolder.push(newFolder);
                                    currentFolder = newFolder.children;
                                } else {
                                    // If the folder exists, move to the next level
                                    currentFolder =
                                        currentFolder[folderIndex].children;
                                }
                            }

                            // Extract file details
                            const fileName = pathParts[pathParts.length - 1];
                            const [name, extension] = fileName.split(".");

                            // Read file content
                            const content = await this.readFile(file);

                            // Add file to the current folder
                            currentFolder.push({
                                name: `${name}.${extension}`,
                                extension,
                                content,
                            });
                        } else {
                            // Process files at root level (not in any folder)
                            const content = await this.readFile(file);
                            const [name, extension] = file.name.split(".");

                            this.items.push({
                                name: `${name}.${extension}`,
                                extension,
                                content,
                            });
                        }
                    }

                    this.$toast().success(
                        this.$t("namespace files.import.success")
                    );
                } catch (error) {
                    this.$toast().error(this.$t("namespace files.import.error"));
                } finally {
                    event.target.value = "";
                    this.import = "file";
                    this.dialog = {...DIALOG_DEFAULTS};
                }
            },
            addFile({file, creation, shouldReset = true}) {
                const {name, extension, content} = file
                    ? file
                    : {
                        name: this.dialog.name.split(".")[0],
                        extension: this.dialog.name.split(".")[1],
                        content: "",
                    };
                const NAME = `${name}.${extension}`;
                const NEW = {name: NAME, extension, content};

                if (creation) {
                    const path = `/${
                        this.dialog.path ? `${this.dialog.path}/` : ""
                    }${NAME}`;
                    this.createFile({
                        namespace: this.$route.params.namespace,
                        path,
                        content,
                    });

                    this.changeOpenedTabs({
                        action: "open",
                        name: NAME,
                        extension: extension,
                        local: true,
                    });
                }

                if (!this.dialog.folder) {
                    this.items.push(NEW);
                } else {
                    const SELF = this;
                    (function pushItemToFolder(array) {
                        for (let i = 0; i < array.length; i++) {
                            const item = array[i];
                            if (
                                item.name === SELF.dialog.folder &&
                                Array.isArray(item.children)
                            ) {
                                item.children.push(NEW);
                                return true; // Return true if the folder is found and item is pushed
                            } else if (Array.isArray(item.children)) {
                                if (pushItemToFolder(item.children)) {
                                    return true; // Return true if the folder is found and item is pushed in recursive call
                                }
                            }
                        }
                        return false; // Return false if the folder is not found
                    })(this.items);
                }

                if (shouldReset) {
                    this.dialog = {...DIALOG_DEFAULTS};
                }
            },

            confirmRemove(data) {
                this.confirmation = {visible: true, data};
            },
            removeItem(name) {
                function removeChildren(array) {
                    for (let i = 0; i < array.length; i++) {
                        const child = array[i];
                        if (child.name === name) {
                            array.splice(i, 1);
                            i--;
                        } else if (Array.isArray(child.children)) {
                            removeChildren(child.children, name);
                        }
                    }
                }
                const remove = (items) => {
                    items.forEach((item, index) => {
                        if (item.name === name) {
                            items.splice(index, 1);
                        } else if (Array.isArray(item.children)) {
                            removeChildren(item.children);
                        }
                    });
                };
                remove(this.items);

                this.deleteFileDirectory({
                    namespace: this.$route.params.namespace,
                    path: name,
                });
                this.changeOpenedTabs({action: "close", name});

                this.confirmation = {visible: false, data: {}};
            },
            addFolder(folder, creation) {
                const {name} = folder
                    ? folder
                    : {
                        name: this.dialog.name,
                    };

                const NEW = {name, leaf: false, children: folder?.children ?? []};

                if (creation) {
                    const path = `/${
                        this.dialog.path ? `${this.dialog.path}/` : ""
                    }${name}`;
                    this.createDirectory({
                        namespace: this.$route.params.namespace,
                        path,
                    });
                }

                if (!this.dialog.folder) {
                    this.items.push(NEW);
                } else {
                    const SELF = this;
                    (function pushItemToFolder(array) {
                        for (let i = 0; i < array.length; i++) {
                            const item = array[i];
                            if (
                                item.name === SELF.dialog.folder &&
                                Array.isArray(item.children)
                            ) {
                                item.children.push(NEW);
                                return true; // Return true if the folder is found and item is pushed
                            } else if (Array.isArray(item.children)) {
                                if (pushItemToFolder(item.children)) {
                                    return true; // Return true if the folder is found and item is pushed in recursive call
                                }
                            }
                        }
                        return false; // Return false if the folder is not found
                    })(this.items);
                }

                this.dialog = {...DIALOG_DEFAULTS};
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
                } catch (_error) {
                    this.$toast().error(this.$t("namespace files.path.error"));
                }
            },
        },
        watch: {
            filter(value) {
                if (this.$refs.tree) {
                    this.$refs.tree.filter(value);
                }
            },
            flows: {
                handler(flow) {
                    if (flow && flow.length) {
                        this.changeOpenedTabs({
                            action: "open",
                            name: "Flow",
                            persistent: true,
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
    overflow: hidden auto;

    &::-webkit-scrollbar {
        width: 2px;
    }

    &::-webkit-scrollbar-track {
        background: var(--card-bg);
    }

    &::-webkit-scrollbar-thumb {
        background: var(--bs-primary);
        border-radius: 0px;
    }

    .node {
        --el-tree-node-content-height: 36px;
        --el-tree-node-hover-bg-color: transparent;
        line-height: 36px;

        .el-tree-node__content {
            width: 100%;
        }
    }
}
</style>

<style lang="scss" scoped>
.sidebar {
    flex: unset;
    background: var(--card-bg);
    border-right: 1px solid var(--bs-border-color);

    :deep(.el-button):not(.el-dialog .el-button) {
        border: 0;
        background: none;
        outline: none;
        opacity: 0.5;
        padding-left: calc(var(--spacer) / 2);
        padding-right: calc(var(--spacer) / 2);

        &.el-button--primary {
            opacity: 1;
        }
    }

    .hidden {
        display: none;
    }

    .filename {
        font-size: var(--el-font-size-small);
        color: var(--el-text-color-regular);

        &:hover {
            color: white;
        }
    }
}
</style>