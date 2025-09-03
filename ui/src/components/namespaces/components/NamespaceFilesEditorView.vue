<template>
    <div class="button-top">
        <el-tooltip
            effect="light"
            v-if="!isCreating"
            ref="toggleExplorer"
            :content="
                t(
                    `namespace files.toggle.${
                        explorerVisible ? 'hide' : 'show'
                    }`
                )
            "
        >
            <el-button @click="toggleExplorerVisibility()">
                <span class="pe-2 toggle-button">{{ t("files") }}</span>
                <MenuOpen v-if="explorerVisible" />
                <MenuClose v-else />
            </el-button>
        </el-tooltip>

        <el-scrollbar v-if="!isCreating" always ref="tabsScrollRef" class="ms-1 tabs">
            <el-button
                v-for="(tab, index) in openedTabs"
                :key="index"
                :class="{'tab-active': isActiveTab(tab)}"
                draggable="true"
                @dragstart="onDragStart($event, index)"
                @dragover.prevent="onDragOver($event, index)"
                @drop.prevent="onDrop($event, index)"
                @click="changeCurrentTab(tab)"
                :disabled="isActiveTab(tab)"
                @contextmenu.prevent.stop="onTabContextMenu($event, tab, index)"
            >
                <TypeIcon :name="tab.name" />
                <el-tooltip
                    effect="light"
                    v-if="tab.path && !tab.persistent"
                    :content="tab.path"
                    transition=""
                    :hide-after="0"
                    :persistent="false"
                >
                    <span class="tab-name px-2">{{ tab.name }}</span>
                </el-tooltip>
                <span class="tab-name px-2" v-else>{{ tab.name }}</span>
                <CircleMedium v-show="tab.dirty" />
                <Close
                    v-if="!tab.persistent"
                    @click.prevent.stop="closeTab(tab, index)"
                    class="cursor-pointer"
                />
            </el-button>
        </el-scrollbar>

        <el-menu
            v-if="tabContextMenu.visible"
            :style="{left: `${tabContextMenu.x}px`, top: `${tabContextMenu.y}px`}"
            class="tabs-context"
        >
            <el-menu-item :disabled="tabContextMenu.tab?.persistent" @click="closeTab(tabContextMenu.tab, tabContextMenu.index)">
                {{ t("namespace_editor.close.tab") }}
            </el-menu-item>
            <el-menu-item @click="closeAllTabs">
                {{ t("namespace_editor.close.all") }}
            </el-menu-item>
            <el-menu-item @click="closeOtherTabs(tabContextMenu.tab)">
                {{ t("namespace_editor.close.other") }}
            </el-menu-item>
            <el-menu-item @click="closeTabsToRight(tabContextMenu.index)">
                {{ t("namespace_editor.close.right") }}
            </el-menu-item>
        </el-menu>

        <div class="d-inline-flex align-items-center">
            <EditorButtons
                v-if="isCreating || openedTabs.length"
                :is-creating="props.isCreating"
                :is-read-only="props.isReadOnly"
                :can-delete="canDelete()"
                :is-allowed-edit="isAllowedEdit"
                :have-change="flowYaml !== flowYamlOrigin"
                :flow-have-tasks="flowHaveTasks"
                :errors="flowErrors"
                :warnings="flowWarnings"
                @delete-flow="deleteFlow"
                @save="save"
                @copy="
                    () =>
                        router.push({
                            name: 'flows/create',
                            query: {copy: 'true'},
                            params: {tenant: routeParams.tenant},
                        })
                "
                :is-namespace="isNamespace"
            />
        </div>
    </div>
    <div v-bind="$attrs" class="main-editor" v-loading="isLoading">
        <div
            id="editorWrapper"
            v-if="combinedEditor || viewType === editorViewTypes.SOURCE"
            :class="combinedEditor ? 'editor-combined' : ''"
            style="flex: 1;"
        >
            <template v-if="editorViewType === 'YAML'">
                <template v-if="isCreating || openedTabs.length">
                    <Editor
                        class="position-relative"
                        ref="editorDomElement"
                        @save="save"
                        @execute="execute"
                        :path="currentTab?.path"
                        :diff-overview-bar="false"
                        :model-value="flowYaml"
                        :schema-type="isCurrentTabFlow? 'flow': undefined"
                        :lang="currentTab?.extension === undefined ? 'yaml' : undefined"
                        :extension="currentTab?.extension"
                        @update:model-value="editorUpdate"
                        :creating="isCreating"
                        @restart-guided-tour="() => persistViewType(editorViewTypes.SOURCE)"
                        @tab-loaded="onTabLoaded"
                        :read-only="isReadOnly"
                        :navbar="false"
                        :diff-side-by-side="false"
                    />
                </template>
                <div v-else class="no-tabs-opened">
                    <div class="img mb-1" />

                    <div>
                        <h5 class="mb-0 fw-bold">
                            {{ t("namespace_editor.empty.title") }}
                        </h5>
                        <p>
                            {{ t("namespace_editor.empty.create_message") }}
                        </p>
                    </div>

                    <div class="empty-state-actions mt-1">
                        <el-dropdown>
                            <el-button :icon="Plus" type="primary">
                                {{ t("create") }}
                            </el-button>
                            <template #dropdown>
                                <el-dropdown-menu>
                                    <el-dropdown-item @click="createFile">
                                        <FilePlus class="me-2" />
                                        {{ t("namespace files.create.file") }}
                                    </el-dropdown-item>
                                    <el-dropdown-item @click="createFolder">
                                        <FolderPlus class="me-2" />
                                        {{ t("namespace files.create.folder") }}
                                    </el-dropdown-item>
                                </el-dropdown-menu>
                            </template>
                        </el-dropdown>
                        <input
                            ref="$refsFilePicker"
                            type="file"
                            multiple
                            class="hidden"
                            @change="handleFileImport"
                        >
                        <input
                            ref="$refsFilePicker"
                            type="file"
                            webkitdirectory
                            mozdirectory
                            msdirectory
                            odirectory
                            directory
                            class="hidden"
                            @change="handleFileImport"
                        >
                        <el-dropdown>
                            <el-button :icon="Download" type="primary">
                                {{ t("import") }}
                            </el-button>
                            <template #dropdown>
                                <el-dropdown-menu>
                                    <el-dropdown-item @click="$refsFilePicker?.click()">
                                        <File class="me-2" />
                                        {{ t("namespace files.import.files") }}
                                    </el-dropdown-item>
                                    <el-dropdown-item @click="$refsFolderPicker?.click()">
                                        <Folder class="me-2" />
                                        {{ t("namespace files.import.folder") }}
                                    </el-dropdown-item>
                                </el-dropdown-menu>
                            </template>
                        </el-dropdown>
                    </div>
                    <el-divider>{{ t("namespace_editor.empty.video_message") }}</el-divider>

                    <div class="video-container">
                        <iframe
                            src="https://www.youtube.com/embed/o-d-GaXUiKQ?si=TTjV8jgRg6-lj_cC"
                            frameborder="0"
                            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                            allowfullscreen
                        />
                    </div>
                </div>
            </template>
        </div>
    </div>
    <el-dialog
        v-model="dialog.visible"
        :title="dialog.type === 'file' ? t('namespace files.create.file') : t('namespace files.create.folder')"
        width="500"
        @keydown.enter.prevent="dialog.name ? dialogHandler() : undefined"
    >
        <div class="pb-1">
            <span>{{ t(`namespace files.dialog.name.${dialog.type}`) }}</span>
        </div>
        <el-input
            ref="creation_name"
            v-model="dialog.name"
            size="large"
            class="mb-3"
        />
        <div class="py-1">
            <span>{{ t("namespace files.dialog.parent_folder") }}</span>
        </div>
        <el-select
            v-model="dialog.folder"
            clearable
            size="large"
            class="mb-3 w-100"
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
                <el-button @click="dialog.visible = false">
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
</template>

<script setup lang="ts">
    import {computed, getCurrentInstance, nextTick, onBeforeUnmount, onMounted, ref, watch} from "vue";
    import {useRoute, useRouter} from "vue-router";
    import {useStorage} from "@vueuse/core";
    import {useI18n} from "vue-i18n";
    import {useToast} from "../../../utils/toast";

    import {useAuthStore} from "override/stores/auth";
    import {useNamespacesStore} from "override/stores/namespaces";
    import {useCoreStore} from "../../../stores/core";
    import {usePluginsStore} from "../../../stores/plugins";
    import {useFlowStore} from "../../../stores/flow";
    import {EditorTabProps, useEditorStore} from "../../../stores/editor";

    import {useFlowOutdatedErrors} from "../../inputs/flowOutdatedErrors";

    import permission from "../../../models/permission";
    import action from "../../../models/action";
    import {storageKeys, editorViewTypes} from "../../../utils/constants";
    import MenuOpen from "vue-material-design-icons/MenuOpen.vue";
    import MenuClose from "vue-material-design-icons/MenuClose.vue";
    import Close from "vue-material-design-icons/Close.vue";
    import CircleMedium from "vue-material-design-icons/CircleMedium.vue";
    import FilePlus from "vue-material-design-icons/FilePlus.vue";
    import FolderPlus from "vue-material-design-icons/FolderPlus.vue";
    import Download from "vue-material-design-icons/Download.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import File from "vue-material-design-icons/File.vue";
    import Folder from "vue-material-design-icons/Folder.vue";

    import TypeIcon from "../../utils/icons/Type.vue"
    import {ElTooltip} from "element-plus"

    import Editor from "../../inputs/Editor.vue";
    import EditorButtons from "../../inputs/EditorButtons.vue";



    const coreStore = useCoreStore();
    const flowStore = useFlowStore();
    const namespacesStore = useNamespacesStore();
    const router = useRouter();
    const route = useRoute();
    const emit = defineEmits(["follow", "expand-subflow"]);
    const toast = useToast();
    const {t} = useI18n();
    const tours = getCurrentInstance()?.appContext.config.globalProperties.$tours;
    const tabsScrollRef = ref();

    const $refsFilePicker = ref<HTMLInputElement | null>(null);
    const $refsFolderPicker = ref<HTMLInputElement | null>(null);

    const props = defineProps({
        flowGraph: {
            type: Object,
            required: false,
            default: undefined,
        },
        flowId: {
            type: String,
            required: false,
            default: undefined,
        },
        flow: {
            type: Object,
            required: false,
            default: undefined,
        },
        namespace: {
            type: String,
            required: false,
            default: undefined,
        },
        execution: {
            type: Object,
            default: undefined,
        },
        isCreating: {
            type: Boolean,
            default: false,
        },
        isReadOnly: {
            type: Boolean,
            default: true,
        },
        isDirty: {
            type: Boolean,
            default: false,
        },
        graphOnly: {
            type: Boolean,
            default: false,
        },
        total: {
            type: Number,
            default: null,
        },
        flowValidation: {
            type: Object,
            default: undefined,
        },
        expandedSubflows: {
            type: Array,
            default: () => [],
        },
        nextRevision: {
            type: Number,
            default: 1,
        },
        isNamespace: {
            type: Boolean,
            default: false,
        },
    });

    flowStore.isCreating = props.isCreating;
    const guidedProperties = ref(coreStore.guidedProperties);

    const isCurrentTabFlow = computed(() => currentTab?.value?.extension === undefined)
    const isFlow = computed(() => currentTab?.value?.flow || props.isCreating);

    const {translateError, translateErrorWithKey} = useFlowOutdatedErrors()

    const flowErrors = computed(() => flowStore.flowErrors?.map(translateError));

    const flowWarnings = computed(() => {
        if (isFlow.value) {
            const outdatedWarning =
                flowStore.flowValidation?.outdated && !flowStore.isCreating
                    ? [translateErrorWithKey(flowStore.flowValidation?.constraints ?? "")]
                    : [];

            const deprecationWarnings =
                flowStore.flowValidation?.deprecationPaths?.map(
                    (f: string) => `${f} ${t("is deprecated")}.`
                ) ?? [];

            const otherWarnings = flowStore.flowValidation?.warnings ?? [];

            const warnings = [
                ...outdatedWarning,
                ...deprecationWarnings,
                ...otherWarnings,
            ];

            return warnings.length === 0 ? undefined : warnings;
        }

        return undefined;
    });

    const flowHaveTasks = computed(() => Boolean(flowStore.flowHaveTasks));
    const editorViewType = useStorage(storageKeys.EDITOR_VIEW_TYPE, "YAML");

    watch(editorViewType, (value) => {
        if(value === "NO_CODE") {
            editorWidth.value = editorWidth.value > 33.3 ? 33.3 : editorWidth.value;
        }
    });

    const loadViewType = () => {
        return localStorage.getItem(editorViewTypes.STORAGE_KEY);
    };

    const initViewType = () => {
        const defaultValue = editorViewTypes.SOURCE_DOC;

        if (props.execution) {
            return editorViewTypes.TOPOLOGY;
        }

        const storedValue = loadViewType();
        if (storedValue) {
            return storedValue;
        }

        localStorage.setItem(editorViewTypes.STORAGE_KEY, defaultValue);
        return defaultValue;
    };

    const isHorizontalDefault = () => {
        return viewType.value === editorViewTypes.SOURCE_TOPOLOGY
            ? false
            : localStorage.getItem("topology-orientation") === "1";
    };

    flowStore.haveChange = props.isDirty;

    const editorDomElement = ref<any>(null);
    const editorWidth = useStorage("editor-size", 50);
    const isLoading = ref(false);
    const flowYaml = computed(() => flowStore.flowYaml);
    const flowYamlOrigin = computed(() => flowStore.flowYamlOrigin);
    const metadata = computed(() => flowStore.metadata);
    const viewType = ref(initViewType());
    const isHorizontal = ref(isHorizontalDefault());
    const updatedFromEditor = ref(false);
    const timer = ref<number>();
    const routeParams = router.currentRoute.value.params;

    const editorStore = useEditorStore();

    const onboarding = computed(() => editorStore.onboarding);
    watch(onboarding, (started) => {
        if(!started) return;

        editorWidth.value = 50;
        switchViewType(editorViewTypes.SOURCE_TOPOLOGY);
    });

    const toggleExplorer = ref<typeof ElTooltip>();
    const explorerVisible = computed(() => editorStore.explorerVisible);
    const toggleExplorerVisibility = () => {
        toggleExplorer.value?.hide();
        editorStore.toggleExplorerVisibility();
    };
    const currentTab = computed(() => editorStore.current);
    const openedTabs = computed(() => editorStore.tabs);

    const changeCurrentTab = (tab: EditorTabProps) => {
        editorStore.openTab(tab);
    };

    const persistViewType = (value: string) => {
        viewType.value = value;
        localStorage.setItem(editorViewTypes.STORAGE_KEY, value);
    };

    watch(
        () => props.expandedSubflows,
        (_, oldValue) => {
            fetchGraph().catch(() => {
                emit("expand-subflow", oldValue);
            });
        }
    );

    const pluginsStore = usePluginsStore();

    const stopTourLocal = () => {
        stopTour();
    }

    onMounted(async () => {
        if(guidedProperties.value?.tourStarted) {
            editorViewType.value = "YAML";
            switchViewType(editorViewTypes.SOURCE_TOPOLOGY, false);
        } else {
            editorViewType.value = props.isNamespace ? "YAML" : (localStorage.getItem(storageKeys.EDITOR_VIEW_TYPE) || "YAML");
        }

        if(!props.isNamespace) {
            initViewType()
            await flowStore.initYamlSource({viewType: viewType.value});
        } else {
            editorStore.closeAllTabs();
            switchViewType(editorViewTypes.SOURCE, false)
            editorStore.toggleExplorerVisibility(true);
        }

        // Save on ctrl+s in topology
        document.addEventListener("keydown", saveUsingKeyboard);

        // Guided tour
        setTimeout(() => {
            if (
                !guidedProperties?.value?.tourStarted &&
                localStorage.getItem("tourDoneOrSkip") !== "true" &&
                props.total === 0
            ) {
                tours["guidedTour"].start();
                persistViewType(editorViewTypes.SOURCE);
            }
        }, 200);
        window.addEventListener("popstate", stopTourLocal);

        if (props.isCreating) {
            editorStore.closeTabs();
        }
    });

    onBeforeUnmount(() => {
        pluginsStore.editorPlugin = undefined;
        document.removeEventListener("keydown", saveUsingKeyboard);
        document.removeEventListener("popstate", stopTourLocal);

        editorStore.closeAllTabs();

        document.removeEventListener("click", hideTabContextMenu);
    });

    const stopTour = () => {
        tours["guidedTour"].stop();
        coreStore.guidedProperties = {
            ...coreStore.guidedProperties,
            tourStarted: false
        };
    };

    const isAllowedEdit = computed(() => flowStore.isAllowedEdit);

    const fetchGraph = async () => {
        if(props.isNamespace) return;

        return flowStore.loadGraphFromSource({
            flow: flowYaml.value,
            config: {
                params: {
                    // due to usage of axios instance instead of $http which doesn't convert arrays
                    subflows: props.expandedSubflows.join(","),
                },
                validateStatus: (status: number) => {
                    return status === 200;
                },
            },
        });
    };

    const onEdit = (source: string, currentIsFlow = false) => {
        flowStore.flowYaml = source;
        return flowStore.onEdit({
            source,
            currentIsFlow,
            editorViewType: editorViewType.value,
            topologyVisible: [
                editorViewTypes.TOPOLOGY,
                editorViewTypes.SOURCE_TOPOLOGY,
            ].includes(viewType.value),
        }).then((value) => {
            return value;
        });
    };

    const editorUpdate = (source: string) => {
        const currentIsFlow = isFlow.value;

        updatedFromEditor.value = true;
        flowStore.flowYaml = source;

        clearTimeout(timer.value);
        timer.value = setTimeout(() => onEdit(source, currentIsFlow), 500) as any;
    };

    const switchViewType = (event: string, shouldPersist = true) => {
        if(shouldPersist) persistViewType(event)
        else viewType.value = event

        if (
            [editorViewTypes.TOPOLOGY, editorViewTypes.SOURCE_TOPOLOGY].includes(
                viewType.value
            )
        ) {
            isHorizontal.value = isHorizontalDefault();
            if (updatedFromEditor.value) {
                onEdit(flowYaml.value, true);
                updatedFromEditor.value = false;
            }
        }
        if (event === editorViewTypes.SOURCE && editorDomElement?.value?.$el) {
            editorDomElement.value.$el.style = null;
        }
    };

    const flowParsed = computed(() => flowStore.flowParsed);

    const saveUsingKeyboard = (e: KeyboardEvent) => {
        if (e.ctrlKey && e.key === "s") {
            e.preventDefault();
            return save();
        }
    };

    const save = async () => {
        clearTimeout(timer.value);
        const result = await flowStore.save({
            content: editorDomElement.value?.$refs.monacoEditor.value ?? flowYaml.value,
            namespace: props.namespace ?? route.params.namespace.toString(),
        })
        if(result === "redirect_to_update"){
            await router.push({
                name: "flows/update",
                params: {
                    id: flowParsed.value.id,
                    namespace: flowParsed.value.namespace,
                    tab: "edit",
                    tenant: routeParams.tenant,
                },
            });
        }
    };

    const execute = () => {
        flowStore.executeFlow = true;
    };

    const authStore = useAuthStore();

    const canDelete = () => {
        return authStore.user?.isAllowed(permission.FLOW, action.DELETE, props.namespace);
    };

    const deleteFlow = () => {
        flowStore.deleteFlowAndDependencies()
            .then(() => {
                return router.push({
                    name: "flows/list",
                    params: {
                        tenant: routeParams.tenant,
                    },
                });
            })
            .then(() => {
                toast.deleted(metadata.value?.id);
            });
    };

    const combinedEditor = computed(() =>
        [
            editorViewTypes.SOURCE_DOC,
            editorViewTypes.SOURCE_TOPOLOGY,
            editorViewTypes.SOURCE_BLUEPRINTS,
        ].includes(viewType.value)
    );

    const isActiveTab = (tab: EditorTabProps) => {
        if (!currentTab.value) {
            return false;
        }

        if (tab.path) {
            return tab.path === currentTab.value.path;
        }

        return tab.name === currentTab.value.name;
    }

    const draggedTabIndex = ref<number | null>(null);
    const dragOverTabIndex = ref<number | null>(null);

    const onDragStart = (event: DragEvent, index: number) => {
        draggedTabIndex.value = index;
        if (event.dataTransfer) {
            event.dataTransfer.effectAllowed = "move";
        }
    };

    const onDragOver = (event: DragEvent, index: number) => {
        event.preventDefault();
        if (index !== draggedTabIndex.value) {
            dragOverTabIndex.value = index;
        }
    };

    const onDrop = (event: DragEvent, to: number) => {
        event.preventDefault();
        const from = draggedTabIndex.value;
        if (from !== null && from !== to) {
            editorStore.reorderTabs({from, to});
        }
        draggedTabIndex.value = null;
        dragOverTabIndex.value = null;
    };

    async function loadFileAtPath(path: string){
        const content = await namespacesStore.readFile({
            path,
            namespace: props.namespace ?? route.params.namespace?.toString() ?? route.params.id?.toString() ?? "",
        })
        flowStore.flowYaml = content;
    }

    const dirtyBeforeLoad = ref(false);

    watch(currentTab, (current, previous) => {
        if(previous?.flow) persistViewType(viewType.value);

        dirtyBeforeLoad.value = current?.dirty ?? false;

        if(current?.flow){
            switchViewType(loadViewType() ?? editorViewTypes.SOURCE, false)
        }else {
            switchViewType(editorViewTypes.SOURCE, false)
            if(current?.path && !current.dirty) {
                loadFileAtPath(current.path)
            }
        }

        nextTick(() => {
            const activeTabElement = tabsScrollRef.value.wrapRef.querySelector(".tab-active");
            const rightMostCurrentTabPixel = activeTabElement?.offsetLeft + activeTabElement?.clientWidth;

            const tabsWrapper = tabsScrollRef.value.wrapRef;
            tabsScrollRef.value.setScrollLeft(rightMostCurrentTabPixel - tabsWrapper.clientWidth);
        });
    })

    function onTabLoaded(tab: any, source: string) {
        clearTimeout(timer.value);

        // once the tab is finished loading, restore the dirty state
        if(tab.path === currentTab.value?.path){
            flowStore.flowYaml = source;
            onEdit(source, tab.flow);
            if (currentTab.value) {
                currentTab.value.dirty = dirtyBeforeLoad.value;
            }
        }
    }

    const tabContextMenu = ref({
        visible: false,
        x: 0,
        y: 0,
        tab: undefined as undefined | EditorTabProps,
        index: undefined as undefined | number,
    });

    const onTabContextMenu = (event: MouseEvent, tab: EditorTabProps, index: number) => {
        tabContextMenu.value = {
            visible: true,
            x: event.clientX,
            y: event.clientY,
            tab: tab,
            index: index,
        };

        document.addEventListener("click", hideTabContextMenu);
    };

    const hideTabContextMenu = () => {
        tabContextMenu.value.visible = false;
        document.removeEventListener("click", hideTabContextMenu);
    };

    const FLOW_TAB = computed(() => editorStore.tabs?.find(tab => tab.name === "Flow"))

    const closeTab = (tab?: EditorTabProps, index?: number) => {
        editorStore.closeTab({...tab, index});
    };

    const closeTabs = (tabsToClose: EditorTabProps[], openTab?: EditorTabProps) => {
        tabsToClose?.forEach((tab: EditorTabProps) => {
            editorStore.closeTab(tab);
        });
        if(openTab){
            editorStore.openTab(openTab);
        }
        hideTabContextMenu();
    };

    const closeAllTabs = () => {
        if (FLOW_TAB.value) {
            closeTabs(openedTabs.value.filter(tab => tab !== FLOW_TAB.value), FLOW_TAB.value);
        }
    };

    const closeOtherTabs = (tab?: EditorTabProps) => {
        closeTabs(openedTabs.value.filter(t => t !== FLOW_TAB.value && t !== tab), tab);
    };

    const closeTabsToRight = (index: number = -1) => {
        closeTabs(openedTabs.value.slice(index + 1).filter(tab => tab !== FLOW_TAB.value), openedTabs.value[index]);
    };

    const dialog = ref({
        visible: false,
        type: "file" as "file" | "folder",
        name: undefined as string | undefined,
        folder: undefined as string | undefined,
    });

    const createFile = () => {
        dialog.value = {
            visible: true,
            type: "file",
            name: undefined,
            folder: undefined
        };
        editorStore.toggleExplorerVisibility(true);
    };

    const createFolder = () => {
        dialog.value = {
            visible: true,
            type: "folder",
            name: undefined,
            folder: undefined
        };
        editorStore.toggleExplorerVisibility(true);
    };

    interface TreeItem {
        fileName: string;
        type: "Directory" | "File";
        children?: TreeItem[];
    }

    const folders = computed(() => {
        function extractPaths(basePath = "", array: TreeItem[]): string[] {
            const paths: string[] = [];
            array?.forEach((item: TreeItem) => {
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
        return extractPaths(undefined, editorStore.treeData);
    });

    const dialogHandler = async () => {
        try {
            const path = (dialog.value.folder
                ? `${dialog.value.folder}/${dialog.value.name}`
                : dialog.value.name) ?? "";

            if (dialog.value.type === "file") {
                await namespacesStore.createFile({
                    namespace: props.namespace ?? route.params.namespace?.toString(),
                    path,
                    content: "",
                });
            } else {
                await namespacesStore.createDirectory({
                    namespace: props.namespace ?? route.params.namespace?.toString(),
                    path,
                });
            }
            dialog.value.visible = false;
            editorStore.refreshTree();
            if (dialog.value.type === "file") {
                editorStore.openTab({
                    name: dialog.value.name!,
                    path,
                    extension: dialog.value.name!.split(".").pop()
                });
            }
        } catch (error) {
            console.error(error);
            toast.error(t("namespace files.create.error"), "error");
        }
    };

    const handleFileImport = async (event: Event) => {
        const target = event.target as HTMLInputElement;
        const files = target.files;
        if (!files) return;

        for (const file of files) {
            const content = (await new Promise<ArrayBuffer>((resolve) => {
                const reader = new FileReader();
                reader.onload = (e) => resolve(e.target?.result as ArrayBuffer);
                reader.readAsArrayBuffer(file);
            })).toString() ?? "";
            const path = file.webkitRelativePath || file.name;

            await namespacesStore.importFileDirectory({
                namespace: props.namespace ?? route.params.namespace?.toString(),
                content,
                path
            });
        }
        editorStore.refreshTree();
        target.value = "";
    };
</script>

<style lang="scss" scoped>
@use "element-plus/theme-chalk/src/mixins/mixins" as *;
@import "@kestra-io/ui-libs/src/scss/variables";

.main-editor {
    padding: .5rem 0px;
    background: var(--ks-background-body);
    display: flex;
    height: calc(100% - 49px);
    min-height: 0;
    max-height: 100%;

    >* {
        flex: 1;
    }

    html.dark & {
        background-color: var(--bs-gray-100);
    }
}

.editor-combined {
    width: 50%;
    min-width: 0;
}

.vueflow {
    width: 100%;
}

html.dark .el-card :deep(.enhance-readability) {
    background-color: var(--bs-gray-500);
}

:deep(.combined-right-view),
.combined-right-view {
    flex: 1;
    position: relative;
    overflow-y: auto;
    height: 100%;

    &.enhance-readability {
        padding: 1.5rem;
        background-color: var(--bs-gray-100);
    }
}

.hide-view {
    width: 0;
    overflow: hidden;
}

.plugin-doc {
    overflow-x: scroll;
}

.slider {
    flex: 0 0 3px;
    border-radius: 0.15rem;
    margin: 0 4px;
    background-color: var(--ks-border-primary);
    border: none;
    cursor: col-resize;
    user-select: none;
    /* disable selection */

    &:hover {
        background-color: var(--ks-border-active);
    }
}

.vueflow {
    height: 100%;
}

.topology-display .el-alert {
    margin-top: 3rem;
}

.toggle-button {
    font-size: var(--el-font-size-small);
}

.tabs {
    flex: 1;
    width: 100px;
    white-space: nowrap;

    .tab-active {
        background: var(--bs-gray-200) !important;
        color: black;
        cursor: default;

        html.dark & {
            color: white;
        }

        .tab-name {
            font-weight: 600;
        }
    }

    .tab-name {
        font-family: "Public sans", sans-serif;
        font-size: 12px;
        font-style: normal;
        font-weight: 500;
    }
}

.no-tabs-opened {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    text-align: center;
    max-width: 800px;
    width: 100%;
    padding: 2rem;
    padding-bottom: 0;
    margin: 0 auto;
    height: 100%;

    .img {
        background: url("../../../assets/empty-ns-files.png") no-repeat center;
        background-size: contain;
        width: 180px;
        height: 180px;
    }

    h2 {
        line-height: 30px;
        font-size: 20px;
        font-weight: 600;
    }

    p {
        line-height: 22px;
        font-size: 14px;
        margin-bottom: 1rem;
        color: var(--ks-content-secondary);
    }

    .empty-state-actions {
        margin-bottom: 2.5rem;
        display: flex;
        justify-content: center;
        gap: 1rem;
        width: 100%;
    }

    :deep(.el-divider__text) {
        font-size: 12px;
        padding: 0 15px;
        color: var(--ks-content-secondary);
        background-color: #f9f9fa;

        html.dark & {
            background-color: #1C1E27;
        }
    }

    .video-container {
        width: 100%;
        margin-top: 1rem;
        border: 1px solid var(--ks-border-primary);
        border-radius: 0.5rem;

        iframe {
            width: 100%;
            min-height: 380px;
            height: auto;
        }
    }

    .hidden {
        display: none;
    }
}

ul.tabs-context {
    position: fixed;
    z-index: 9999;
    border-right: none;

    & li {
        height: 30px;
        padding: 16px;
        font-size: var(--el-font-size-small);
        color: var(--bs-gray-700);

        &:hover {
            color: var(--ks-content-secondary);
        }
    }
}
</style>

<style lang="scss">
    .tabs .el-scrollbar__bar.is-horizontal {
        height: 1px !important;
    }

    .cursor-pointer {
        cursor: pointer;
    }
</style>
