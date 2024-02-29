<template>
    <top-nav-bar :title="routeInfo.title">
        <template #additional-right>
            <namespace-select
                class="fit-content"
                data-type="flow"
                :value="namespace"
                @update:model-value="namespaceUpdate"
                allow-create
                :is-filter="false"
            />

            <el-dropdown>
                <el-button :icon="Plus" class="p-2 m-0" />
                <template #dropdown>
                    <el-dropdown-menu>
                        <el-dropdown-item :icon="FilePlus" @click="pickFile">
                            <input
                                ref="filePicker"
                                type="file"
                                multiple
                                style="display: none"
                                @change="importNsFiles"
                            >
                            {{ $t("namespace files.import.file") }}
                        </el-dropdown-item>
                        <el-dropdown-item :icon="FolderPlus" @click="pickFolder">
                            <input
                                ref="folderPicker"
                                type="file"
                                webkitdirectory
                                mozdirectory
                                msdirectory
                                odirectory
                                directory
                                style="display: none"
                                @change="importNsFiles"
                            >
                            {{ $t("namespace files.import.folder") }}
                        </el-dropdown-item>
                    </el-dropdown-menu>
                </template>
            </el-dropdown>
            <el-tooltip :hide-after="50" :content="$t('namespace files.export')">
                <el-button :icon="FolderZip" class="p-2 m-0" @click="exportNsFiles" />
            </el-tooltip>
            <trigger-flow
                ref="triggerFlow"
                :disabled="!flow"
                :flow-id="flow"
                :namespace="namespace"
            />
        </template>
    </top-nav-bar>
    <iframe
        style="visibility:hidden;"
        onload="this.style.visibility = 'visible';"
        v-if="namespace"
        class="vscode-editor"
        :src="vscodeIndexUrl"
        ref="vscodeIde"
    />
    <section v-else class="container">
        <el-alert type="info" :closable="false">
            {{ $t("namespace choice") }}
        </el-alert>
    </section>
</template>

<script setup>
    import NamespaceSelect from "./NamespaceSelect.vue";
    import TopNavBar from "../layout/TopNavBar.vue";
    import TriggerFlow from "../flows/TriggerFlow.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import FolderPlus from "vue-material-design-icons/FolderPlus.vue";
    import FilePlus from "vue-material-design-icons/FilePlus.vue";
    import FolderZip from "vue-material-design-icons/FolderZip.vue";
</script>

<script>
    import RouteContext from "../../mixins/routeContext";
    import RestoreUrl from "../../mixins/restoreUrl";
    import {apiUrl} from "override/utils/route";
    import {mapState} from "vuex";
    import {storageKeys} from "../../utils/constants";

    export default {
        mixins: [RouteContext, RestoreUrl],
        methods: {
            importNsFiles(event) {
                const files = [...event.target.files];
                Promise.all(files.map(file => {
                    return this.$store
                        .dispatch("namespace/importFile", {
                            namespace: this.namespace,
                            file: file
                        })
                })).then(() => {
                    this.$message({
                        message: this.$t("namespace files.import.success"),
                        type: "success"
                    });
                }).catch(() => {
                    this.$message({
                        message: this.$t("namespace files.import.error"),
                        type: "error"
                    });
                }).finally(() => {
                    this.$refs.vscodeIde.contentDocument.location.reload(true);
                    event.target.value = "";
                });
            },
            exportNsFiles() {
                this.$store.dispatch("namespace/exportFiles", {
                    namespace: this.namespace
                });
            },
            pickFile() {
                this.$refs.filePicker.click();
            },
            pickFolder() {
                this.$refs.folderPicker.click();
            },
            namespaceUpdate(namespace) {
                localStorage.setItem(storageKeys.LATEST_NAMESPACE, namespace);
                this.$router.push({
                    params: {
                        namespace
                    }
                });
            },
            handleTabsDirty(tabs) {
                // Add tabs not saved
                this.tabsNotSaved = this.tabsNotSaved.concat(tabs.dirty)
                // Removed tabs closed
                this.tabsNotSaved = this.tabsNotSaved.filter(e => !tabs.closed.includes(e))
                this.$store.dispatch("core/isUnsaved", this.tabsNotSaved.length > 0);
            }
        },
        data() {
            return {
                flow: null,
                tabsNotSaved: [],
                uploadFileName: undefined
            }
        },
        mounted() {
            window.addEventListener("message", (event) => {
                const message = event.data;
                if (message.type === "kestra.tabFileChanged") {
                    const flowsFolderPath = `/${this.namespace}/_flows/`;
                    const filePath = message.filePath.path;
                    if (filePath.startsWith(flowsFolderPath)) {
                        const fileName = filePath.split(flowsFolderPath)[1];
                        // trim the eventual extension
                        this.flow = fileName.split(".")[0];
                    } else {
                        this.flow = undefined;
                    }
                } else if (message.type === "kestra.tabsChanged") {
                    this.handleTabsDirty(message.tabs);
                } else if (message.type === "kestra.flowSaved") {
                    this.$refs.triggerFlow.loadDefinition();
                }
            });

            // Setup namespace
            const namespace = localStorage.getItem(storageKeys.LATEST_NAMESPACE) ? localStorage.getItem(storageKeys.LATEST_NAMESPACE) : localStorage.getItem(storageKeys.DEFAULT_NAMESPACE);
            if (namespace) {
                this.namespaceUpdate(namespace);
            } else if (this.namespaces?.length > 0) {
                this.namespaceUpdate(this.namespaces[0]);
            } else if (localStorage.getItem("tourDoneOrSkip") !== "true") {
                this.$router.push({
                    name: "flows/create",
                    params: {
                        tenant: this.$route.params.tenant
                    }
                });
            }
        },
        computed: {
            ...mapState("namespace", ["namespaces"]),
            routeInfo() {
                return {
                    title: this.$t("editor")
                };
            },
            theme() {
                return localStorage.getItem("theme") || "light";
            },
            vscodeIndexUrl() {
                const uiSubpath = KESTRA_UI_PATH === "./" ? "/" : KESTRA_UI_PATH;
                return `${uiSubpath}vscode.html?KESTRA_UI_PATH=${uiSubpath}&KESTRA_API_URL=${apiUrl(this.$store)}&THEME=${this.theme}&namespace=${this.namespace}`;
            },
            namespace() {
                return this.$route.params.namespace;
            }
        }
    }
</script>

<style lang="scss">
    .fit-content {
        width: fit-content;
    }
    .vscode-editor {
        height: calc(100vh - 64.8px);
        width: 100%;
    }
</style>