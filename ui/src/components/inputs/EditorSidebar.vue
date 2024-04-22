<template>
    <div class="w-25 p-3 sidebar">
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
                    <el-button class="px-2" @click="toggleDialog(true, 'folder')">
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
                            <el-dropdown-item @click="$refs.folderPicker.click()">
                                {{ $t("namespace files.import.folder") }}
                            </el-dropdown-item>
                        </el-dropdown-menu>
                    </template>
                </el-dropdown>
            </el-button-group>
        </div>

        <el-tree
            ref="tree"
            :data="items"
            node-key="name"
            :filter-node-method="filterNode"
            highlight-current
            default-expand-all
            draggable
            :empty-text="$t('namespace files.no_items')"
            :props="{label: 'name', children: 'children', class: 'node'}"
            class="mt-3"
        >
            <template #default="{data, node}">
                <el-dropdown
                    :ref="`dropdown__${data.name}`"
                    @contextmenu.prevent="toggleDropdown(`dropdown__${data.name}`)"
                    trigger="contextmenu"
                >
                    <el-row justify="space-between">
                        <el-col :span="10">
                            <el-button class="ps-0">
                                <component
                                    :is="data.children ? 'FolderOutline' : 'FileDocumentOutline'"
                                />
                            </el-button>
                            <span class="filename"> {{ data.name }}</span>
                        </el-col>
                    </el-row>
                    <template #dropdown>
                        <el-dropdown-menu>
                            <el-dropdown-item @click="toggleDialog(true, 'file', node)">
                                {{ $t("namespace files.create.file") }}
                            </el-dropdown-item>
                            <el-dropdown-item @click="toggleDialog(true, 'folder', node)">
                                {{ $t("namespace files.create.folder") }}
                            </el-dropdown-item>
                            <el-dropdown-item @click="renameItemDialog(data)">
                                {{
                                    $t(
                                        `namespace files.rename.${
                                            Array.isArray(data.children) ? "folder" : "file"
                                        }`
                                    )
                                }}
                            </el-dropdown-item>
                            <el-dropdown-item @click="confirmRemove(data)">
                                {{
                                    $t(
                                        `namespace files.delete.${
                                            Array.isArray(data.children) ? "folder" : "file"
                                        }`
                                    )
                                }}
                            </el-dropdown-item>
                        </el-dropdown-menu>
                    </template>
                </el-dropdown>
            </template>
        </el-tree>

        <el-dialog
            v-model="dialog.visible"
            :title="
                dialog.rename
                    ? $t('namespace files.rename.label')
                    : dialog.type === 'file'
                        ? $t('namespace files.create.file')
                        : $t('namespace files.create.folder')
            "
            width="500"
            @open="focusInput()"
            @keydown.enter.prevent="dialog.name ? dialogHandler() : undefined"
        >
            <el-input
                ref="name"
                v-model="dialog.name"
                :placeholder="$t('namespace files.dialog.name')"
                size="large"
                class="mb-3"
            />
            <el-select
                v-if="dialog.type === 'file' && !dialog.rename"
                v-model="dialog.extension"
                :placeholder="$t('namespace files.dialog.select')"
                size="large"
                class="mb-3"
            >
                <el-option
                    v-for="extension in extensions"
                    :key="extension.value"
                    :value="extension.value"
                    :label="extension.label"
                />
            </el-select>
            <el-select
                v-if="!dialog.rename"
                v-model="dialog.folder"
                :placeholder="$t('namespace files.dialog.select_folder')"
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
                        {{
                            dialog.rename
                                ? $t("namespace files.rename.label")
                                : $t("namespace files.create.label")
                        }}
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
                        ? $t("namespace files.dialog.folder_deletion_description")
                        : $t("namespace files.dialog.file_deletion_description")
                }}
            </span>
            <template #footer>
                <div>
                    <el-button @click="confirmation.visible = false">
                        {{ $t("cancel") }}
                    </el-button>
                    <el-button type="primary" @click="removeItem(confirmation.data.name)">
                        {{ $t("namespace files.dialog.confirm") }}
                    </el-button>
                </div>
            </template>
        </el-dialog>
    </div>
</template>

<script>
    import {mapState} from "vuex";

    import Magnify from "vue-material-design-icons/Magnify.vue";
    import Close from "vue-material-design-icons/Close.vue";
    import FilePlus from "vue-material-design-icons/FilePlus.vue";
    import FolderPlus from "vue-material-design-icons/FolderPlus.vue";
    import PlusBox from "vue-material-design-icons/PlusBox.vue";
    import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue";
    import FolderOutline from "vue-material-design-icons/FolderOutline.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import {YamlUtils} from "@kestra-io/ui-libs";

    const YAML = "yml";
    const DIALOG_DEFAULTS = {
        visible: false,
        type: undefined,
        name: undefined,
        extension: YAML,
        folder: "",
        rename: false,
    };

    export default {
        components: {
            Magnify,
            Close,
            FilePlus,
            FolderPlus,
            PlusBox,
            FileDocumentOutline,
            FolderOutline,
            Delete,
        },
        data() {
            return {
                filter: "",

                dialog: {...DIALOG_DEFAULTS},
                extensions: [{label: YAML.toUpperCase(), value: YAML}],
                dropdownRef: "",

                currentFolder: "",

                confirmation: {visible: false, data: {}},

                items: [],
            };
        },
        computed: {
            ...mapState("flow", ["flows", "total"]),

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
        },
        methods: {
            toggleDropdown(reference) {
                if (this.dropdownRef) {
                    this.$refs[this.dropdownRef].handleClose();
                }

                this.dropdownRef = reference;
                this.$refs[reference].handleOpen();
            },
            dialogHandler() {
                const {rename, type} = this.dialog;

                if (rename) {
                    this.renameItem();
                    return;
                } else {
                    type === "file" ? this.addFile() : this.addFolder();
                }
            },
            filterNode(value, data) {
                if (!value) return true;
                return data.name.includes(value);
            },
            toggleDialog(isShown, type, node) {
                if (isShown) {
                    const selected = this.$refs.tree.getCurrentNode();
                    const folder =
                        selected && Array.isArray(selected.children) ? selected.name : null;

                    this.dialog.visible = true;
                    this.dialog.type = type;
                    this.dialog.folder = folder ?? node?.label ?? "";

                    this.focusInput();
                } else {
                    this.dialog = {...DIALOG_DEFAULTS};
                }
            },
            focusInput() {
                setTimeout(() => {
                    this.$refs.name.focus();
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
                const folder = event.target.files[0].webkitRelativePath;
                const imported = [...event.target.files];

                try {
                    if (folder) {
                        const name = folder.split("/")[0];
                        this.addFolder({name});
                        this.dialog.folder = name;
                    }

                    for (const file of imported) {
                        const content = await this.readFile(file);
                        const [name, extension] = file.name.split(".");

                        this.addFile({name, extension, content}, false);
                    }
                    this.$toast().success(this.$t("namespace files.import.success"));
                } catch (_error) {
                    this.$toast().error(this.$t("namespace files.import.error"));
                } finally {
                    event.target.value = "";
                    this.import = "file";

                    this.dialog = {...DIALOG_DEFAULTS};
                }
            },
            addFile(file, shouldReset = true) {
                const {name, extension, content} = file
                    ? file
                    : {
                        name: this.dialog.name,
                        extension: this.dialog.extension,
                        content: `# Initial content of your ${this.dialog.name}.${this.dialog.extension} file`,
                    };
                const NEW = {name: `${name}.${extension}`, extension, content};

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
            renameItemDialog(item) {
                this.dialog = {visible: true, rename: true, name: item.name, item};
            },
            renameItem() {
                const SELF = this;
                (function rename(array) {
                    for (let i = 0; i < array.length; i++) {
                        const item = array[i];
                        if (item.name === SELF.dialog.item.name) {
                            item.name = SELF.dialog.name;
                            return true;
                        } else if (Array.isArray(item.children)) {
                            if (rename(item.children)) {
                                return true;
                            }
                        }
                    }
                    return false;
                })(this.items);

                this.dialog = {...DIALOG_DEFAULTS};
            },
            confirmRemove(data) {
                this.confirmation = {visible: true, data};
            },
            removeItem(name) {
                function removeChildren(array) {
                    for (let i = 0; i < array.length; i++) {
                        const child = array[i]; // Renamed item to child
                        if (child.name === name) {
                            array.splice(i, 1);
                            i--; // Adjust the loop index due to removal
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
                this.confirmation = {visible: false, data: {}};
            },
            addFolder(folder) {
                const {name} = folder
                    ? folder
                    : {
                        name: this.dialog.name,
                    };

                this.items.push({name, children: folder?.children ?? []});
                this.dialog = {...DIALOG_DEFAULTS};
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
                        this.addFile({
                            name: `${flow[0].id}`,
                            extension: YAML,
                            content: YamlUtils.parse(flow[0]),
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

    .el-tree-node__content .el-row {
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
      color: var(--bs-primary);
    }
  }
}
</style>