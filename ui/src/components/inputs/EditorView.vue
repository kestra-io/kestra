<template>
    <div class="button-top">
        <el-tooltip
            effect="light"
            v-if="!isCreating"
            ref="toggleExplorer"
            :content="
                $t(
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
            <el-menu-item :disabled="tabContextMenu.tab.persistent" @click="closeTab(tabContextMenu.tab, tabContextMenu.index)">
                {{ $t("namespace_editor.close.tab") }}
            </el-menu-item>
            <el-menu-item @click="closeAllTabs">
                {{ $t("namespace_editor.close.all") }}
            </el-menu-item>
            <el-menu-item @click="closeOtherTabs(tabContextMenu.tab)">
                {{ $t("namespace_editor.close.other") }}
            </el-menu-item>
            <el-menu-item @click="closeTabsToRight(tabContextMenu.index)">
                {{ $t("namespace_editor.close.right") }}
            </el-menu-item>
        </el-menu>

        <div class="d-inline-flex align-items-center">
            <el-switch
                v-if="!isNamespace"
                v-model="editorViewType"
                @change="(val) => editorViewType = val"
                active-value="NO_CODE"
                inactive-value="YAML"
                :inactive-text="$t('no_code.labels.no_code')"
                size="small"
                class="me-2"
            />

            <switch-view
                v-if="!isNamespace"
                :type="viewType"
                class="to-topology-button"
                @switch-view="switchViewType"
            />

            <ValidationError
                v-if="!isNamespace"
                ref="validationDomElement"
                class="validation"
                tooltip-placement="bottom-start"
                :errors="flowErrors"
                :warnings="flowWarnings"
                :infos="flowInfos"
            />

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
                            query: {copy: true},
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
                    <editor
                        class="position-relative"
                        ref="editorDomElement"
                        @save="save"
                        @execute="execute"
                        :path="currentTab?.path"
                        :diff-overview-bar="false"
                        :model-value="draftSource === undefined ? flowYaml : draftSource"
                        :schema-type="isCurrentTabFlow? 'flow': undefined"
                        :lang="currentTab?.extension === undefined ? 'yaml' : undefined"
                        :extension="currentTab?.extension"
                        @update:model-value="editorUpdate"
                        @cursor="updatePluginDocumentation"
                        :creating="isCreating"
                        @restart-guided-tour="() => persistViewType(editorViewTypes.SOURCE)"
                        @tab-loaded="onTabLoaded"
                        :read-only="isReadOnly"
                        :navbar="false"
                        :original="draftSource === undefined ? undefined : flowYaml"
                        :diff-side-by-side="false"
                    >
                        <template #absolute>
                            <div class="d-flex flex-column align-items-end gap-2" v-if="isCurrentTabFlow">
                                <el-button v-if="!aiAgentOpened" class="rounded-pill" :icon="AiIcon" @click="draftSource = undefined; aiAgentOpened = true">
                                    {{ $t("ai.flow.title") }}
                                </el-button>
                                <span>
                                    <KeyShortcuts />
                                </span>
                            </div>
                        </template>
                    </editor>
                    <transition name="el-zoom-in-center">
                        <AiAgent
                            v-if="aiAgentOpened"
                            class="position-absolute prompt"
                            @close="aiAgentOpened = false"
                            :flow="editorContent"
                            @generated-yaml="yaml => {draftSource = yaml; aiAgentOpened = false}"
                        />
                    </transition>
                    <AcceptDecline
                        v-if="draftSource !== undefined"
                        class="position-absolute prompt"
                        @accept="acceptDraft"
                        @decline="declineDraft"
                    />
                </template>
                <div v-else class="no-tabs-opened">
                    <div class="img mb-1" />

                    <div>
                        <h5 class="mb-0 fw-bold">
                            {{ $t("namespace_editor.empty.title") }}
                        </h5>
                        <p>
                            {{ $t("namespace_editor.empty.create_message") }}
                        </p>
                    </div>

                    <div class="empty-state-actions mt-1">
                        <el-dropdown>
                            <el-button :icon="Plus" type="primary">
                                {{ $t("create") }}
                            </el-button>
                            <template #dropdown>
                                <el-dropdown-menu>
                                    <el-dropdown-item @click="createFile">
                                        <FilePlus class="me-2" />
                                        {{ $t("namespace files.create.file") }}
                                    </el-dropdown-item>
                                    <el-dropdown-item @click="createFolder">
                                        <FolderPlus class="me-2" />
                                        {{ $t("namespace files.create.folder") }}
                                    </el-dropdown-item>
                                </el-dropdown-menu>
                            </template>
                        </el-dropdown>
                        <input
                            ref="filePicker"
                            type="file"
                            multiple
                            class="hidden"
                            @change="handleFileImport"
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
                            @change="handleFileImport"
                        >
                        <el-dropdown>
                            <el-button :icon="Download" type="primary">
                                {{ $t("import") }}
                            </el-button>
                            <template #dropdown>
                                <el-dropdown-menu>
                                    <el-dropdown-item @click="$refs.filePicker.click()">
                                        <File class="me-2" />
                                        {{ $t("namespace files.import.files") }}
                                    </el-dropdown-item>
                                    <el-dropdown-item @click="$refs.folderPicker.click()">
                                        <Folder class="me-2" />
                                        {{ $t("namespace files.import.folder") }}
                                    </el-dropdown-item>
                                </el-dropdown-menu>
                            </template>
                        </el-dropdown>
                    </div>
                    <el-divider>{{ $t("namespace_editor.empty.video_message") }}</el-divider>

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
            <NoCode
                v-else-if="isFlow"
                :flow="flowYaml"
                :section="route.query.section?.toString()"
                :task-id="route.query.identifier?.toString()"
                :position="route.query.position === 'before' ? 'before' : 'after'"
                @update-metadata="(e) => onUpdateMetadata(e, true)"
                @update-task="(e) => editorUpdate(e)"
                @reorder="(yaml) => handleReorder(yaml)"
                @update-documentation="(task) => updatePluginDocumentation(undefined, task)"
            />
        </div>
        <div class="slider" @mousedown.prevent.stop="dragEditor" v-if="combinedEditor" />
        <div :class="{'d-flex': combinedEditor}" :style="viewType === editorViewTypes.SOURCE ? `display: none` : combinedEditor ? `flex: 0 0 calc(${100 - editorWidth}% - 11px)` : 'flex: 1 0 0%'">
            <div
                v-if="viewType === editorViewTypes.SOURCE_BLUEPRINTS"
                class="combined-right-view enhance-readability"
            >
                <Blueprints @loaded="blueprintsLoaded = true" embed kind="flow" combined-view />
            </div>

            <div
                v-else-if="viewType === editorViewTypes.SOURCE_TOPOLOGY || viewType === editorViewTypes.TOPOLOGY"
                :class="viewType === editorViewTypes.SOURCE_TOPOLOGY ? 'combined-right-view' : 'vueflow'"
                class="topology-display"
            >
                <LowCodeEditor
                    v-if="flowGraph"
                    ref="lowCodeEditorRef"
                    @follow="forwardEvent('follow', $event)"
                    @on-edit="(event, isFlow) => onEdit(event, isFlow)"
                    @loading="loadingState"
                    @expand-subflow="onExpandSubflow"
                    @swapped-task="onSwappedTask"
                    :flow-graph="flowGraph"
                    :flow-id="flowId"
                    :namespace="namespace"
                    :execution="execution"
                    :is-read-only="isReadOnly"
                    :source="flowYaml"
                    :is-allowed-edit="isAllowedEdit"
                    :horizontal-default="viewType === editorViewTypes.SOURCE_TOPOLOGY
                        ? false
                        : viewType === editorViewTypes.SOURCE_BLUEPRINTS
                            ? true
                            : undefined"
                    :expanded-subflows="props.expandedSubflows"
                />
                <el-alert v-else type="warning" :closable="false">
                    {{ $t("unable to generate graph") }}
                </el-alert>
            </div>

            <PluginDocumentation
                v-else-if="viewType === editorViewTypes.SOURCE_DOC"
                class="plugin-doc combined-right-view enhance-readability"
            />
        </div>

        <drawer
            v-model="isNewErrorOpen"
            title="Add a global error handler"
        >
            <el-form label-position="top">
                <task-editor
                    :section="SECTIONS.TASKS"
                    @update:model-value="onUpdateNewError"
                />
            </el-form>
            <template #footer>
                <ValidationError :errors="taskErrors" />
                <el-button
                    :icon="ContentSave"
                    @click="onSaveNewError()"
                    type="primary"
                    :disabled="Boolean(taskErrors)"
                >
                    {{ $t("save") }}
                </el-button>
            </template>
        </drawer>
        <drawer
            v-model="isNewTriggerOpen"
            title="Add a trigger"
        >
            <el-form label-position="top">
                <task-editor
                    :section="SECTIONS.TRIGGERS"
                    @update:model-value="onUpdateNewTrigger"
                />
            </el-form>
            <template #footer>
                <ValidationError :errors="taskErrors" />
                <el-button
                    :icon="ContentSave"
                    @click="onSaveNewTrigger()"
                    type="primary"
                    :disabled="Boolean(taskErrors)"
                >
                    {{ $t("save") }}
                </el-button>
            </template>
        </drawer>
        <drawer
            v-if="isEditMetadataOpen"
            v-model="isEditMetadataOpen"
        >
            <template #header>
                <code>flow metadata</code>
            </template>

            <el-form label-position="top">
                <metadata-editor
                    :metadata="store.getters['flow/flowYamlMetadata']"
                    @update:model-value="onUpdateMetadata"
                    :editing="!props.isCreating"
                />
            </el-form>
            <template #footer>
                <el-button
                    :icon="ContentSave"
                    @click="onSaveMetadata()"
                    type="primary"
                    :disabled="!checkRequiredMetadata()"
                    class="edit-flow-save-button"
                >
                    {{ $t("save") }}
                </el-button>
            </template>
        </drawer>
    </div>
    <el-dialog
        v-if="confirmOutdatedSaveDialog"
        v-model="confirmOutdatedSaveDialog"
        destroy-on-close
        :append-to-body="true"
    >
        <template #header>
            <h5>{{ $t(`${baseOutdatedTranslationKey}.title`) }}</h5>
        </template>
        {{ $t(`${baseOutdatedTranslationKey}.description`) }}
        {{ $t(`${baseOutdatedTranslationKey}.details`) }}
        <template #footer>
            <el-button @click="confirmOutdatedSaveDialog = false">
                {{ $t("cancel") }}
            </el-button>
            <el-button
                type="warning"
                @click="
                    saveWithoutRevisionGuard();
                    confirmOutdatedSaveDialog = false;
                "
            >
                {{ $t("ok") }}
            </el-button>
        </template>
    </el-dialog>
    <el-dialog
        v-model="dialog.visible"
        :title="dialog.type === 'file' ? $t('namespace files.create.file') : $t('namespace files.create.folder')"
        width="500"
        @keydown.enter.prevent="dialog.name ? dialogHandler() : undefined"
    >
        <div class="pb-1">
            <span>{{ $t(`namespace files.dialog.name.${dialog.type}`) }}</span>
        </div>
        <el-input
            ref="creation_name"
            v-model="dialog.name"
            size="large"
            class="mb-3"
        />
        <div class="py-1">
            <span>{{ $t("namespace files.dialog.parent_folder") }}</span>
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
</template>

<script setup>
    import {computed, getCurrentInstance, nextTick, onBeforeUnmount, onMounted, ref, watch,} from "vue";
    import {useStore} from "vuex";
    import {useRoute, useRouter} from "vue-router";
    import {useStorage} from "@vueuse/core";

    // Icons
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
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

    import TypeIcon from "../utils/icons/Type.vue"
    import SwitchView from "./SwitchView.vue";
    import KeyShortcuts from "./KeyShortcuts.vue";

    import permission from "../../models/permission";
    import action from "../../models/action";
    import {storageKeys, editorViewTypes} from "../../utils/constants";
    import {Utils, YamlUtils as YAML_UTILS, SECTIONS} from "@kestra-io/ui-libs";

    // editor components
    import Editor from "./Editor.vue";
    import NoCode from "../code/NoCode.vue";
    import Blueprints from "override/components/flows/blueprints/Blueprints.vue";
    import LowCodeEditor from "./LowCodeEditor.vue";
    import Drawer from "../Drawer.vue";
    import PluginDocumentation from "../plugins/PluginDocumentation.vue";
    import TaskEditor from "../flows/TaskEditor.vue";
    import ValidationError from "../flows/ValidationError.vue";
    import EditorButtons from "./EditorButtons.vue";
    import MetadataEditor from "../flows/MetadataEditor.vue";
    import {useFlowOutdatedErrors} from "./flowOutdatedErrors";
    import {usePluginsStore} from "../../stores/plugins";
    import AiAgent from "../ai/AiAgent.vue";
    import AiIcon from "../ai/AiIcon.vue";
    import AcceptDecline from "./AcceptDecline.vue";

    const store = useStore();
    const router = useRouter();
    const route = useRoute();
    const emit = defineEmits(["follow", "expand-subflow"]);
    const toast = getCurrentInstance().appContext.config.globalProperties.$toast();
    const t = getCurrentInstance().appContext.config.globalProperties.$t;
    const tours = getCurrentInstance().appContext.config.globalProperties.$tours;
    const lowCodeEditorRef = ref(null);
    const tabsScrollRef = ref();

    const toggleAiShortcut = (event) => {
        if (event.altKey && event.key === "k" && isCurrentTabFlow.value) {
            event.preventDefault();
            draftSource.value = undefined;
            aiAgentOpened.value = !aiAgentOpened.value;
        }
    };
    const aiAgentOpened = ref(false);
    const draftSource = ref(undefined);

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

    store.commit("flow/setIsCreating", props.isCreating);
    const guidedProperties = ref(store.getters["core/guidedProperties"]);

    const isCurrentTabFlow = computed(() => currentTab?.value?.extension === undefined)
    const isFlow = computed(() => currentTab?.value?.flow || props.isCreating);

    const {translateError, translateErrorWithKey} = useFlowOutdatedErrors()

    const baseOutdatedTranslationKey = computed(() => store.getters["flow/baseOutdatedTranslationKey"]);
    const flowErrors = computed(() => store.getters["flow/flowErrors"]?.map(translateError));
    const flowWarnings = computed(() => {
        if (isFlow.value) {
            const outdatedWarning =
                store.state.flow.flowValidation?.outdated && !store.state.flow.isCreating
                    ? [translateErrorWithKey(store.getters["flow/baseOutdatedTranslationKey"])]
                    : [];

            const deprecationWarnings =
                store.state.flow.flowValidation?.deprecationPaths?.map(
                    (f) => `${f} ${t("is deprecated")}.`
                ) ?? [];

            const otherWarnings = store.state.flow.flowValidation?.warnings ?? [];

            const warnings = [
                ...outdatedWarning,
                ...deprecationWarnings,
                ...otherWarnings,
            ];

            return warnings.length === 0 ? undefined : warnings;
        }

        return undefined;
    });
    const flowInfos = computed(() => store.getters["flow/flowInfos"]);
    const flowHaveTasks = computed(() => Boolean(store.getters["flow/flowHaveTasks"]));

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

    store.commit("flow/setHaveChange", props.isDirty);

    const editorDomElement = ref(null);
    const editorWidth = useStorage("editor-size", 50);
    const validationDomElement = ref(null);
    const isLoading = ref(false);
    const flowYaml = computed(() => store.getters["flow/flowYaml"]);
    const flowYamlOrigin = computed(() => store.state.flow.flowYamlOrigin);
    const user = computed(() => store.getters["auth/user"]);
    const metadata = computed(() => store.state.flow.metadata);
    const newTrigger = ref(null);
    const isNewTriggerOpen = ref(false);
    const newError = ref(null);
    const isNewErrorOpen = ref(false);
    const isEditMetadataOpen = ref(false);
    const viewType = ref(initViewType());
    const isHorizontal = ref(isHorizontalDefault());
    const updatedFromEditor = ref(false);
    const timer = ref(null);
    const routeParams = router.currentRoute.value.params;
    const blueprintsLoaded = ref(false);
    const confirmOutdatedSaveDialog = ref(false);

    const onboarding = computed(() => store.state.editor.onboarding);
    watch(onboarding, (started) => {
        if(!started) return;

        editorWidth.value = 50;
        switchViewType(editorViewTypes.SOURCE_TOPOLOGY);
    });

    const toggleExplorer = ref(null);
    const explorerVisible = computed(() => store.state.editor.explorerVisible);
    const toggleExplorerVisibility = () => {
        toggleExplorer.value.hide();
        store.commit("editor/toggleExplorerVisibility");
    };
    const currentTab = computed(() => store.state.editor.current);
    const openedTabs = computed(() => store.state.editor.tabs);

    const changeCurrentTab = (tab) => {
        store.dispatch("editor/openTab", tab);
    };

    const persistViewType = (value) => {
        viewType.value = value;
        localStorage.setItem(editorViewTypes.STORAGE_KEY, value);
    };

    const taskErrors = computed(() => {
        return store.getters["flow/taskError"]?.split(/, ?/);
    });

    watch(
        () => props.expandedSubflows,
        (_, oldValue) => {
            fetchGraph().catch(() => {
                emit("expand-subflow", oldValue);
            });
        }
    );

    const onResize = () => {
        if (validationDomElement.value && editorDomElement.value) {
            validationDomElement.value.onResize(
                editorDomElement.value.$el.offsetWidth
            );
        }
    };

    const pluginsStore = usePluginsStore();

    onMounted(async () => {
        if(guidedProperties.value?.tourStarted) {
            editorViewType.value = "YAML";
            switchViewType(editorViewTypes.SOURCE_TOPOLOGY, false);
        } else {
            editorViewType.value = props.isNamespace ? "YAML" : (localStorage.getItem(storageKeys.EDITOR_VIEW_TYPE) || "YAML");
        }

        if(!props.isNamespace) {
            initViewType()
            await store.dispatch("flow/initYamlSource", {viewType: viewType.value});
        } else {
            store.commit("editor/closeAllTabs");
            switchViewType(editorViewTypes.SOURCE, false)
            store.commit("editor/toggleExplorerVisibility", true);
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
        window.addEventListener("popstate", () => {
            stopTour();
        });
        window.addEventListener("resize", onResize);

        if (props.isCreating) {
            store.commit("editor/closeTabs");
        }

        window.addEventListener("keydown", toggleAiShortcut);
    });

    onBeforeUnmount(() => {
        window.removeEventListener("resize", onResize);

        pluginsStore.editorPlugin = undefined;
        document.removeEventListener("keydown", saveUsingKeyboard);
        document.removeEventListener("popstate", () => {
            stopTour();
        });

        store.commit("editor/closeAllTabs");

        document.removeEventListener("click", hideTabContextMenu);
        window.removeEventListener("keydown", toggleAiShortcut);
    });

    const stopTour = () => {
        tours["guidedTour"].stop();
        store.commit("core/setGuidedProperties", {tourStarted: false});
    };

    const isAllowedEdit = computed(() => store.getters["flow/isAllowedEdit"]);

    const forwardEvent = (type, event) => {
        emit(type, event);
    };

    const updatePluginDocumentation = (event, task) => {
        pluginsStore.updateDocumentation({event,task});
    };

    const fetchGraph = () => {
        if(props.isNamespace) return;

        return store.dispatch("flow/loadGraphFromSource", {
            flow: flowYaml.value,
            config: {
                params: {
                    // due to usage of axios instance instead of $http which doesn't convert arrays
                    subflows: props.expandedSubflows.join(","),
                },
                validateStatus: (status) => {
                    return status === 200;
                },
            },
        });
    };

    const onEdit = (source, currentIsFlow = false) => {
        if (draftSource.value !== undefined) {
            draftSource.value = source;
        } else {
            store.commit("flow/setFlowYaml", source);
        }
        return store.dispatch("flow/onEdit", {
            source,
            currentIsFlow,
            editorViewType: editorViewType.value,
            topologyVisible: [
                editorViewTypes.TOPOLOGY,
                editorViewTypes.SOURCE_TOPOLOGY,
            ].includes(viewType.value),
        }).then((value) => {

            if (validationDomElement.value && editorDomElement.value?.$el?.offsetWidth) {
                validationDomElement.value.onResize(editorDomElement.value.$el.offsetWidth);
            }

            return value;
        });
    };

    const loadingState = (value) => {
        isLoading.value = value;
    };

    const onUpdateNewTrigger = (event) => {
        clearTimeout(timer.value);
        timer.value = setTimeout(
            () =>
                store.dispatch("flow/validateTask", {
                    task: event,
                    section: SECTIONS.TRIGGERS,
                }),
            500
        );
        newTrigger.value = event;
    };

    const onSaveNewTrigger = () => {
        const source = flowYaml.value;
        const existingTask = YAML_UTILS.checkTaskAlreadyExist(
            source,
            newTrigger.value
        );
        if (existingTask) {
            store.dispatch("core/showMessage", {
                variant: "error",
                title: t("trigger_id_exists"),
                message: t("trigger_id_message", {existingTrigger: existingTask}),
            });
            return;
        }
        onEdit(YAML_UTILS.insertSection("triggers", source, newTrigger.value), true);
        newTrigger.value = null;
        isNewTriggerOpen.value = false;
        store.commit("flow/setHaveChange", true)
    };

    const onUpdateNewError = (event) => {
        clearTimeout(timer.value);
        timer.value = setTimeout(
            () =>
                store.dispatch("flow/validateTask", {
                    task: event,
                    section: SECTIONS.TASKS,
                }),
            500
        );

        newError.value = event;
    };

    const onSaveNewError = () => {
        const source = flowYaml.value;
        const existingTask = YAML_UTILS.checkTaskAlreadyExist(
            source,
            newError.value
        );
        if (existingTask) {
            store.dispatch("core/showMessage", {
                variant: "error",
                title: t("task_id_exists"),
                message: t("task_id_message", {existingTask}),
            });
            return;
        }
        onEdit(YAML_UTILS.insertSection("errors", source, newError.value), true);
        newError.value = null;
        isNewErrorOpen.value = false;
    };

    const checkRequiredMetadata = () => {
        const md = metadata.value ?? store.getters["flow/flowYamlMetadata"];;

        return md.id.length > 0 && md.namespace.length > 0;
    };

    const onUpdateMetadata = (event, shouldSave) => {
        if(shouldSave) {
            store.commit("flow/setMetadata", {...metadata.value, ...(event.concurrency?.limit === 0 ? {concurrency: null} : event)});
            store.dispatch("flow/onSaveMetadata");
            store.dispatch("flow/validateFlow", {flow: flowYaml.value});
        } else {
            store.commit("flow/setMetadata", event.concurrency?.limit === 0 ?  {concurrency: null} : event);
        }
    };

    const onSaveMetadata = () => {
        store.dispatch("flow/onSaveMetadata");
        isEditMetadataOpen.value = false;
    };

    const handleReorder = (yaml) => {
        store.commit("flow/setFlowYaml", yaml);
        store.commit("flow/setHaveChange", true)
        save()
    };

    const editorUpdate = (source) => {
        const currentIsFlow = isFlow.value;

        updatedFromEditor.value = true;
        store.commit("flow/setFlowYaml", source);

        clearTimeout(timer.value);
        timer.value = setTimeout(() => onEdit(source, currentIsFlow), 500);
    };

    const switchViewType = (event, shouldPersist = true) => {
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

    const flowParsed = computed(() => store.getters["flow/flowParsed"]);

    const saveWithoutRevisionGuard = async () => {
        clearTimeout(timer.value);
        const result = await store.dispatch("flow/saveWithoutRevisionGuard");
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

    const saveUsingKeyboard = (e) => {
        if (e.ctrlKey && e.key === "s") {
            e.preventDefault();
            return save();
        }
    };

    const save = async () => {
        clearTimeout(timer.value);
        const result = await store.dispatch("flow/save", {
            content: editorDomElement.value?.$refs.monacoEditor.value ?? flowYaml.value,
            namespace: props.namespace ?? route.params.namespace,
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

    const execute = (_) => {
        store.commit("flow/executeFlow", true);
    };

    const canDelete = () => {
        return user.value?.isAllowed(permission.FLOW, action.DELETE, props.namespace);
    };

    const deleteFlow = () => {
        store.dispatch("flow/deleteFlowAndDependencies")
            .then(() => {
                return router.push({
                    name: "flows/list",
                    params: {
                        tenant: routeParams.tenant,
                    },
                });
            })
            .then(() => {
                toast.deleted(metadata.value.id);
            });
    };

    const combinedEditor = computed(() =>
        [
            editorViewTypes.SOURCE_DOC,
            editorViewTypes.SOURCE_TOPOLOGY,
            editorViewTypes.SOURCE_BLUEPRINTS,
        ].includes(viewType.value)
    );

    const dragEditor = (e) => {
        let dragX = e.clientX;

        const {offsetWidth, parentNode} = document.getElementById("editorWrapper");
        let blockWidthPercent = (offsetWidth / parentNode.offsetWidth) * 100;

        const isNoCode = localStorage.getItem(storageKeys.EDITOR_VIEW_TYPE) === "NO_CODE";
        const maxWidth = isNoCode ? 33.3 : 75;

        document.onmousemove = function onMouseMove(e) {
            let percent = blockWidthPercent + ((e.clientX - dragX) / parentNode.offsetWidth) * 100;

            editorWidth.value = percent > maxWidth ? maxWidth : percent < 25 ? 25 : percent;
            validationDomElement.value.onResize((percent * parentNode.offsetWidth) / 100);
        };

        document.onmouseup = () => {
            document.onmousemove = document.onmouseup = null;
        };
    };

    const onExpandSubflow = (e) => {
        emit("expand-subflow", e);
    };

    const onSwappedTask = (swappedTasks) => {
        emit(
            "expand-subflow",
            props.expandedSubflows.map((expandedSubflow) => {
                let swappedTaskSplit;
                if (expandedSubflow === swappedTasks[0]) {
                    swappedTaskSplit = swappedTasks[1].split(".");
                    swappedTaskSplit.pop();

                    return (
                        swappedTaskSplit.join(".") +
                        "." +
                        Utils.afterLastDot(expandedSubflow)
                    );
                }
                if (expandedSubflow === swappedTasks[1]) {
                    swappedTaskSplit = swappedTasks[0].split(".");
                    swappedTaskSplit.pop();

                    return (
                        swappedTaskSplit.join(".") +
                        "." +
                        Utils.afterLastDot(expandedSubflow)
                    );
                }

                return expandedSubflow;
            })
        );
    };

    const isActiveTab = (tab) => {
        if (!currentTab.value) {
            return false;
        }

        if (tab.path) {
            return tab.path === currentTab.value.path;
        }

        return tab.name === currentTab.value.name;
    }

    const draggedTabIndex = ref(null);
    const dragOverTabIndex = ref(null);

    const onDragStart = (event, index) => {
        draggedTabIndex.value = index;
        event.dataTransfer.effectAllowed = "move";
    };
    const onDragOver = (event, index) => {
        event.preventDefault();
        if (index !== draggedTabIndex.value) {
            dragOverTabIndex.value = index;
        }
    };
    const onDrop = (event, to) => {
        event.preventDefault();
        const from = draggedTabIndex.value;
        if (from !== to) {
            store.commit("editor/reorderTabs", {from, to});
        }
        draggedTabIndex.value = null;
        dragOverTabIndex.value = null;
    };

    async function loadFileAtPath(path){
        const content = await store.dispatch("namespace/readFile", {
            path,
            namespace: props.namespace ?? route.params.namespace ?? route.params.id,
        })
        store.commit("flow/setFlowYaml", content);
    }

    const dirtyBeforeLoad = ref(false);

    watch(currentTab, (current, previous) => {
        if(previous?.flow) persistViewType(viewType.value);

        dirtyBeforeLoad.value = current?.dirty;

        if(current?.flow){
            switchViewType(loadViewType(), false)
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

    function onTabLoaded(tab, source){
        clearTimeout(timer.value);

        // once the tab is finished loading, restore the dirty state
        if(tab.path === currentTab.value.path){
            flowYaml.value = source;
            onEdit(source, tab.flow);
            currentTab.value.dirty = dirtyBeforeLoad.value
        }
    }

    const tabContextMenu = ref({
        visible: false,
        x: 0,
        y: 0,
        tab: null,
        index: null,
    });

    const onTabContextMenu = (event, tab, index) => {
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

    const FLOW_TAB = computed(() => store.state.editor?.tabs?.find(tab => tab.name === "Flow"))

    const closeTab = (tab, index) => {
        store.dispatch("editor/closeTab", {...tab, index});
    };

    const closeTabs = (tabsToClose, openTab) => {
        tabsToClose.forEach(tab => {
            store.dispatch("editor/closeTab", tab);
        });
        store.dispatch("editor/openTab", openTab);
        hideTabContextMenu();
    };

    const closeAllTabs = () => {
        closeTabs(openedTabs.value.filter(tab => tab !== FLOW_TAB.value), FLOW_TAB.value);
    };

    const closeOtherTabs = (tab) => {
        closeTabs(openedTabs.value.filter(t => t !== FLOW_TAB.value && t !== tab), tab);
    };

    const closeTabsToRight = (index) => {
        closeTabs(openedTabs.value.slice(index + 1).filter(tab => tab !== FLOW_TAB.value), openedTabs.value[index]);
    };

    const dialog = ref({
        visible: false,
        type: "file",
        name: undefined,
        folder: undefined,
    });
    const createFile = () => {
        dialog.value = {
            visible: true,
            type: "file",
            name: undefined,
            folder: undefined
        };
        store.commit("editor/toggleExplorerVisibility", true);
    };
    const createFolder = () => {
        dialog.value = {
            visible: true,
            type: "folder",
            name: undefined,
            folder: undefined
        };
        store.commit("editor/toggleExplorerVisibility", true);
    };
    const folders = computed(() => {
        function extractPaths(basePath = "", array) {
            const paths = [];
            array?.forEach((item) => {
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
        return extractPaths(undefined, store.state.editor.treeData);
    });
    const dialogHandler = async () => {
        try {
            const path = dialog.value.folder
                ? `${dialog.value.folder}/${dialog.value.name}`
                : dialog.value.name;

            if (dialog.value.type === "file") {
                await store.dispatch("namespace/createFile", {
                    namespace: props.namespace ?? route.params.namespace,
                    path,
                    content: "",
                });
            } else {
                await store.dispatch("namespace/createDirectory", {
                    namespace: props.namespace ?? route.params.namespace,
                    path,
                });
            }
            dialog.value.visible = false;
            store.commit("editor/refreshTree");
            if (dialog.value.type === "file") {
                store.dispatch("editor/openTab", {
                    name: dialog.value.name,
                    path,
                    extension: dialog.value.name.split(".").pop()
                });
            }
        } catch (error) {
            console.error(error);
            toast().error(t("namespace files.create.error"));
        }
    };
    const handleFileImport = async (event) => {
        const files = event.target.files;
        for (const file of files) {
            const content = await new Promise((resolve) => {
                const reader = new FileReader();
                reader.onload = (e) => resolve(e.target.result);
                reader.readAsArrayBuffer(file);
            });
            const path = file.webkitRelativePath || file.name;

            await store.dispatch("namespace/importFileDirectory", {
                namespace: props.namespace ?? route.params.namespace,
                content,
                path
            });
        }
        store.commit("editor/refreshTree");
        event.target.value = "";
    };

    function acceptDraft() {
        const accepted = draftSource.value;
        draftSource.value = undefined;
        editorUpdate(accepted);
    }

    function declineDraft() {
        draftSource.value = undefined;
        aiAgentOpened.value = true;
    }
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

        > * {
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
        user-select: none; /* disable selection */

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
            background: url("../../assets/empty-ns-files.png") no-repeat center;
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

    .prompt {
        bottom: 10%;
        width: calc(100% - 4rem);
        left: 2rem;
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
