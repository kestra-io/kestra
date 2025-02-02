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
                <component :is="explorerVisible ? MenuOpen : MenuClose" />
            </el-button>
        </el-tooltip>

        <el-scrollbar v-if="!isCreating" always ref="tabsScrollRef" class="ms-1 tabs">
            <el-button
                v-for="(tab, index) in openedTabs"
                :key="index"
                :class="{'tab-active': isActiveTab(tab)}"
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
                @change="changeEditorViewType"
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
                :flow-have-tasks="flowHaveTasks()"
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
                @export="exportYaml"
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
                <editor
                    v-if="isCreating || openedTabs.length"
                    ref="editorDomElement"
                    @save="save"
                    @execute="execute"
                    v-model="flowYaml"
                    :schema-type="isCurrentTabFlow? 'flow': undefined"
                    :lang="currentTab?.extension === undefined ? 'yaml' : undefined"
                    :extension="currentTab?.extension"
                    @update:model-value="editorUpdate"
                    @cursor="updatePluginDocumentation"
                    :creating="isCreating"
                    @restart-guided-tour="() => persistViewType(editorViewTypes.SOURCE)"
                    :read-only="isReadOnly"
                    :navbar="false"
                />
                <section v-else class="no-tabs-opened">
                    <div class="img" />

                    <h2>{{ $t("namespace_editor.empty.title") }}</h2>
                    <p><span>{{ $t("namespace_editor.empty.message") }}</span></p>

                    <iframe
                        width="60%"
                        height="400px"
                        src="https://www.youtube.com/embed/o-d-GaXUiKQ?si=TTjV8jgRg6-lj_cC"
                        frameborder="0"
                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                        allowfullscreen
                    />
                </section>
            </template>
            <NoCode
                v-else
                :flow="flowYaml"
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
                <Blueprints @loaded="blueprintsLoaded = true" embed kind="flow" />
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
                    @open-no-code="(params) => handleTopologyEditClick(params)"
                    :flow-graph="flowGraph"
                    :flow-id="flowId"
                    :namespace="namespace"
                    :execution="execution"
                    :is-read-only="isReadOnly"
                    :source="flowYaml"
                    :is-allowed-edit="isAllowedEdit"
                    :view-type="viewType"
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
            v-if="isNewErrorOpen"
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
                    :disabled="taskErrors"
                >
                    {{ $t("save") }}
                </el-button>
            </template>
        </drawer>
        <drawer
            v-if="isNewTriggerOpen"
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
                    :disabled="taskErrors"
                >
                    {{ $t("save") }}
                </el-button>
            </template>
        </drawer>
        <drawer v-if="isEditMetadataOpen" v-model="isEditMetadataOpen">
            <template #header>
                <code>flow metadata</code>
            </template>

            <el-form label-position="top">
                <metadata-editor
                    :metadata="getFlowMetadata()"
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
</template>

<script setup>
    import {computed, getCurrentInstance, h, nextTick, onBeforeUnmount, onMounted, ref, watch,} from "vue";
    import {useStore} from "vuex";
    import {useRoute, useRouter} from "vue-router";

    // Icons
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import MenuOpen from "vue-material-design-icons/MenuOpen.vue";
    import MenuClose from "vue-material-design-icons/MenuClose.vue";
    import Close from "vue-material-design-icons/Close.vue";
    import CircleMedium from "vue-material-design-icons/CircleMedium.vue";

    import TypeIcon from "../utils/icons/Type.vue"

    import ValidationError from "../flows/ValidationError.vue";
    import Blueprints from "override/components/flows/blueprints/Blueprints.vue";
    import SwitchView from "./SwitchView.vue";
    import PluginDocumentation from "../plugins/PluginDocumentation.vue";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import YamlUtils from "../../utils/yamlUtils";
    import TaskEditor from "../flows/TaskEditor.vue";
    import MetadataEditor from "../flows/MetadataEditor.vue";
    import Editor from "./Editor.vue";
    import {SECTIONS, storageKeys} from "../../utils/constants.js";
    import LowCodeEditor from "../inputs/LowCodeEditor.vue";
    import {editorViewTypes} from "../../utils/constants";
    import {Utils} from "@kestra-io/ui-libs";
    import {apiUrl} from "override/utils/route";
    import EditorButtons from "./EditorButtons.vue";
    import Drawer from "../Drawer.vue";
    import {ElMessageBox} from "element-plus";
    import NoCode from "../code/NoCode.vue";
    import localUtils from "../../utils/utils";

    const store = useStore();
    const router = useRouter();
    const route = useRoute();
    const emit = defineEmits(["follow", "expand-subflow"]);
    const toast = getCurrentInstance().appContext.config.globalProperties.$toast();
    const t = getCurrentInstance().appContext.config.globalProperties.$t;
    const http = getCurrentInstance().appContext.config.globalProperties.$http;
    const tours = getCurrentInstance().appContext.config.globalProperties.$tours;
    const lowCodeEditorRef = ref(null);
    const tabsScrollRef = ref();

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
        guidedProperties: {
            type: Object,
            default: () => {
                return {tourStarted: false};
            },
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

    const isCurrentTabFlow = computed(() => currentTab?.value?.extension === undefined)

    const isFlow = () => currentTab?.value?.flow || props.isCreating;

    const flowErrors = computed(() => {
        if (isFlow()) {
            const flowExistsError =
                props.flowValidation?.outdated && props.isCreating
                    ? [outdatedMessage.value]
                    : [];

            const constraintsError =
                props.flowValidation?.constraints?.split(/, ?/) ?? [];

            const errors = [...flowExistsError, ...constraintsError];

            return errors.length === 0 ? undefined : errors;
        }

        return undefined;
    });

    const baseOutdatedTranslationKey = computed(() => {
        const createOrUpdateKey = props.isCreating ? "create" : "update";
        return "outdated revision save confirmation." + createOrUpdateKey;
    });

    const outdatedMessage = computed(() => {
        return `${t(baseOutdatedTranslationKey.value + ".description")} ${t(
            baseOutdatedTranslationKey.value + ".details"
        )}`;
    });

    const flowWarnings = computed(() => {
        if (isFlow()) {
            const outdatedWarning =
                props.flowValidation?.outdated && !props.isCreating
                    ? [outdatedMessage.value]
                    : [];

            const deprecationWarnings =
                props.flowValidation?.deprecationPaths?.map(
                    (f) => `${f} ${t("is deprecated")}.`
                ) ?? [];

            const otherWarnings = props.flowValidation?.warnings ?? [];

            const warnings = [
                ...outdatedWarning,
                ...deprecationWarnings,
                ...otherWarnings,
            ];

            return warnings.length === 0 ? undefined : warnings;
        }

        return undefined;
    });

    const flowInfos = computed(() => {
        if (isFlow()) {
            const infos = props.flowValidation?.infos  ?? [];
            return infos.length === 0 ? undefined : infos;
        }

        return undefined;
    });

    const editorViewType = ref("YAML");
    const changeEditorViewType = (value) => {
        localStorage.setItem(storageKeys.EDITOR_VIEW_TYPE, value);

        if(value === "NO_CODE") {
            editorWidth.value = editorWidth.value > 33.3 ? 33.3 : editorWidth.value;
        }
    }

    const handleTopologyEditClick = (params) => {
        if (viewType.value === editorViewTypes.TOPOLOGY) {
            switchViewType(editorViewTypes.SOURCE_TOPOLOGY);
        }
        editorViewType.value = "NO_CODE";
        nextTick(() => router.replace({query: {...route.query, ...params}}))
    }

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

    const editorDomElement = ref(null);
    const editorWidthStorageKey = "editor-size";
    const localStorageStoredWidth = localStorage.getItem(editorWidthStorageKey);
    const editorWidth = ref(localStorageStoredWidth ?? 50);
    const validationDomElement = ref(null);
    const isLoading = ref(false);
    const haveChange = ref(props.isDirty);
    const flowYaml = ref("");
    const flowYamlOrigin = ref("");
    const newTrigger = ref(null);
    const isNewTriggerOpen = ref(false);
    const newError = ref(null);
    const isNewErrorOpen = ref(false);
    const isEditMetadataOpen = ref(false);
    const metadata = ref(null);
    const viewType = ref(initViewType());
    const isHorizontal = ref(isHorizontalDefault());
    const updatedFromEditor = ref(false);
    const timer = ref(null);
    const taskError = ref(store.getters["flow/taskError"]);
    const user = store.getters["auth/user"];
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
        store.commit("editor/changeOpenedTabs", {
            ...tab,
            action: "open",
        });
    };

    const persistViewType = (value) => {
        viewType.value = value;
        localStorage.setItem(editorViewTypes.STORAGE_KEY, value);
    };

    watch(
        () => store.getters["flow/taskError"],
        async () => {
            taskError.value = store.getters["flow/taskError"];
        }
    );

    const taskErrors = computed(() => {
        return taskError.value?.split(/, ?/);
    });

    watch(
        () => props.expandedSubflows,
        (_, oldValue) => {
            fetchGraph().catch(() => {
                emit("expand-subflow", oldValue);
            });
        }
    );

    const flowHaveTasks = (source) => {
        if (isFlow()) {
            const flow = props.isCreating ? props.flow.source : (source ? source : flowYaml.value);
            return flow ? YamlUtils.flowHaveTasks(flow) : false;
        } else return false;
    };

    const yamlWithNextRevision = computed(() => {
        return `revision: ${props.nextRevision}\n${flowYaml.value}`;
    });

    watch(flowYaml, (newYaml) => {
        store.commit("core/setAutocompletionSource", newYaml);
    });

    const initYamlSource = async () => {
        flowYaml.value = props.flow.source;
        flowYamlOrigin.value = props.flow.source;
        if (flowHaveTasks()) {
            if (
                [
                    editorViewTypes.TOPOLOGY,
                    editorViewTypes.SOURCE_TOPOLOGY,
                ].includes(viewType.value)
            ) {
                await fetchGraph();
            } else {
                fetchGraph();
            }
        }

        // validate flow on first load
        store
            .dispatch("flow/validateFlow", {flow: props.isCreating ? flowYaml.value : yamlWithNextRevision.value})
            .then((value) => {
                if (validationDomElement.value && editorDomElement.value) {
                    validationDomElement.value.onResize(
                        editorDomElement.value.$el.offsetWidth
                    );
                }

                return value;
            });
    };

    const persistEditorWidth = () => {
        if (editorWidth.value !== null) {
            localStorage.setItem(editorWidthStorageKey, editorWidth.value);
        }
    };

    const onResize = () => {
        if (validationDomElement.value && editorDomElement.value) {
            validationDomElement.value.onResize(
                editorDomElement.value.$el.offsetWidth
            );
        }
    };

    onMounted(async () => {
        editorViewType.value = props.isNamespace ? "YAML" : (localStorage.getItem(storageKeys.EDITOR_VIEW_TYPE) || "YAML");

        if(!props.isNamespace) {
            initViewType()
            await initYamlSource();
        } else {
            store.commit("editor/closeAllTabs");
            switchViewType(editorViewTypes.SOURCE, false)
            store.commit("editor/toggleExplorerVisibility", true);
        }

        // Save on ctrl+s in topology
        document.addEventListener("keydown", save);
        // Guided tour
        setTimeout(() => {
            if (
                !props.guidedProperties.tourStarted &&
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
        window.addEventListener("beforeunload", persistEditorWidth);
        window.addEventListener("resize", onResize);

        if (props.isCreating) {
            store.commit("editor/closeTabs");
        }
    });

    onBeforeUnmount(() => {
        store.commit("core/setAutocompletionSource", undefined);
        window.removeEventListener("resize", onResize);

        store.commit("plugin/setEditorPlugin", undefined);
        document.removeEventListener("keydown", save);
        document.removeEventListener("popstate", () => {
            stopTour();
        });

        window.removeEventListener("beforeunload", persistEditorWidth);
        persistEditorWidth();

        store.commit("editor/closeAllTabs");

        document.removeEventListener("click", hideTabContextMenu);
    });

    const stopTour = () => {
        tours["guidedTour"].stop();
        store.commit("core/setGuidedProperties", {tourStarted: false});
    };

    const isAllowedEdit = computed(() => {

        return (
            user && user.isAllowed(permission.FLOW, action.UPDATE, flowParsed.value?.namespace ?? props.namespace)
        );
    });

    const forwardEvent = (type, event) => {
        emit(type, event);
    };

    const updatePluginDocumentation = (event, task) => {
        const pluginSingleList = store.getters["plugin/getPluginSingleList"];
        const taskType = task !== undefined ? task : YamlUtils.getTaskType(
            event.model.getValue(),
            event.position,
            pluginSingleList
        );
        if (taskType) {
            store.dispatch("plugin/load", {cls: taskType}).then((plugin) => {
                store.commit("plugin/setEditorPlugin", {cls: taskType, ...plugin});
            });
        } else {
            store.commit("plugin/setEditorPlugin", undefined);
        }
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

    const onEdit = (event, currentIsFlow = false) => {
        flowYaml.value = event;

        if (currentIsFlow) {
            if (
                flowParsed.value &&
                !props.isCreating &&
                (routeParams.id !== flowParsed.value.id ||
                    routeParams.namespace !== flowParsed.value.namespace)
            ) {
                store.dispatch("core/showMessage", {
                    variant: "error",
                    title: t("readonly property"),
                    message: t("namespace and id readonly"),
                });
                flowYaml.value = YamlUtils.replaceIdAndNamespace(
                    flowYaml.value,
                    routeParams.id,
                    routeParams.namespace
                );
                return;
            }
        }

        haveChange.value = true;
        if(editorViewType.value === "YAML") store.dispatch("core/isUnsaved", true);
        
        if(!props.isCreating){
            store.commit("editor/changeOpenedTabs", {
                action: "dirty",
                ...currentTab.value,
                name: currentTab.value?.name ?? "Flow",
                path: currentTab.value?.path ?? "Flow.yaml",
                dirty: true
            });
        }

        clearTimeout(timer.value);

        if(!currentIsFlow) return;

        return store
            .dispatch("flow/validateFlow", {flow: props.isCreating ? flowYaml.value : yamlWithNextRevision.value})
            .then((value) => {
                if (
                    flowHaveTasks() &&
                    [
                        editorViewTypes.TOPOLOGY,
                        editorViewTypes.SOURCE_TOPOLOGY,
                    ].includes(viewType.value)
                ) {
                    if(!value.constraints) fetchGraph();
                }

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
        const existingTask = YamlUtils.checkTaskAlreadyExist(
            source,
            newTrigger.value
        );
        if (existingTask) {
            store.dispatch("core/showMessage", {
                variant: "error",
                title: "Trigger Id already exist",
                message: `Trigger Id ${existingTask} already exist in the flow.`,
            });
            return;
        }
        onEdit(YamlUtils.insertTrigger(source, newTrigger.value), true);
        newTrigger.value = null;
        isNewTriggerOpen.value = false;
        haveChange.value = true;
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
        const existingTask = YamlUtils.checkTaskAlreadyExist(
            source,
            newError.value
        );
        if (existingTask) {
            store.dispatch("core/showMessage", {
                variant: "error",
                title: "Task Id already exist",
                message: `Task Id ${existingTask} already exist in the flow.`,
            });
            return;
        }
        onEdit(YamlUtils.insertError(source, newError.value), true);
        newError.value = null;
        isNewErrorOpen.value = false;
    };

    const getFlowMetadata = () => {
        return YamlUtils.getMetadata(flowYaml.value);
    };

    const checkRequiredMetadata = () => {
        if (metadata.value) {
            return (
                metadata.value.id.length > 0 && metadata.value.namespace.length > 0
            );
        }
        return (
            getFlowMetadata().id.length > 0 &&
            getFlowMetadata().namespace.length > 0
        );
    };

    const validateFlow = (flow) => {
        if(!flow) return;

        return store
            .dispatch("flow/validateFlow", {flow})
            .then((value) => {
                if (validationDomElement.value && editorDomElement.value) {
                    validationDomElement.value.onResize(
                        editorDomElement.value.$el.offsetWidth
                    );
                }

                return value;
            });
    };

    const onUpdateMetadata = (event, shouldSave) => {
        metadata.value = event;

        if(shouldSave) {
            metadata.value = {...metadata.value, ...event};
            onSaveMetadata();
            validateFlow(flowYaml.value)

        } else {
            metadata.value = event;
        }
    };

    const onSaveMetadata = () => {
        const source = flowYaml.value;
        flowYaml.value = YamlUtils.updateMetadata(source, metadata.value);
        metadata.value = null;
        isEditMetadataOpen.value = false;
        haveChange.value = true;
    };

    const handleReorder = (yaml) => {
        flowYaml.value = yaml;
        haveChange.value = true;
        save()
    };

    const editorUpdate = (event) => {
        const currentIsFlow = isFlow();

        updatedFromEditor.value = true;
        flowYaml.value = event;

        clearTimeout(timer.value);
        timer.value = setTimeout(() => onEdit(event, currentIsFlow), 500);
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

    const flowParsed = computed(() => {
        try {
            return YamlUtils.parse(flowYaml.value);
        } catch {
            return undefined;
        }
    });

    const saveWithoutRevisionGuard = async () => {
        if (flowParsed.value === undefined) {
            store.dispatch("core/showMessage", {
                variant: "error",
                title: t("invalid flow"),
                message: t("invalid yaml"),
            });

            return;
        }
        const overrideFlow = ref(false);
        if (flowErrors.value) {
            if (props.flowValidation.outdated && props.isCreating) {
                overrideFlow.value = await ElMessageBox({
                    title: t("override.title"),
                    message: () => {
                        return h("div", null, [
                            h("p", null, t("override.details")),
                        ]);
                    },
                    showCancelButton: true,
                    confirmButtonText: t("ok"),
                    cancelButtonText: t("cancel"),
                    center: false,
                    showClose: false,
                })
                    .then(() => {
                        overrideFlow.value = true;
                        return true;
                    })
                    .catch(() => {
                        return false;
                    });
            }
        }

        if (props.isCreating && !overrideFlow.value) {
            await store
                .dispatch("flow/createFlow", {flow: flowYaml.value})
                .then((response) => {
                    toast.saved(response.id);
                    store.dispatch("core/isUnsaved", false);
                });
        } else {
            await store
                .dispatch("flow/saveFlow", {flow: flowYaml.value})
                .then((response) => {
                    toast.saved(response.id);
                    store.dispatch("core/isUnsaved", false);
                });
        }

        if (props.isCreating || overrideFlow.value) {
            router.push({
                name: "flows/update",
                params: {
                    id: flowParsed.value.id,
                    namespace: flowParsed.value.namespace,
                    tab: "edit",
                    tenant: routeParams.tenant,
                },
            });
        }

        haveChange.value = false;
        await store.dispatch("flow/validateFlow", {
            flow: props.isCreating ? flowYaml.value : yamlWithNextRevision.value
        });
    };

    const save = async (e) => {
        if (!haveChange.value && !props.isCreating) {
            return;
        }
        if (e) {
            if (e.type === "keydown") {
                if (!(e.keyCode === 83 && e.ctrlKey)) {
                    return;
                }
                e.preventDefault();
            }
        }

        if (isFlow()) {
            onEdit(flowYaml.value, true).then((validation) => {
                if (validation.outdated && !props.isCreating) {
                    confirmOutdatedSaveDialog.value = true;
                    return;
                }
                saveWithoutRevisionGuard();
                flowYamlOrigin.value = flowYaml.value;

                if (currentTab.value && currentTab.value.name) {
                    store.commit("editor/changeOpenedTabs", {
                        action: "dirty",
                        name: "Flow",
                        path: "Flow.yaml",
                        dirty: false,
                        flow: true,
                    });
                }
            });
        } else {
            if(!currentTab.value.dirty) return;

            await store.dispatch("namespace/createFile", {
                namespace: props.namespace ?? routeParams.id,
                path: currentTab.value.path ?? currentTab.value.name,
                content: editorDomElement.value.$refs.monacoEditor.value,
            });
            store.commit("editor/changeOpenedTabs", {
                action: "dirty",
                path: currentTab.value.path,
                name: currentTab.value.name,
                dirty: false
            });

            store.dispatch("core/isUnsaved", false);
        }
    };

    const execute = (_) => {
        store.commit("flow/executeFlow", true);
    };

    const canDelete = () => {
        return user.isAllowed(permission.FLOW, action.DELETE, props.namespace);
    };

    const deleteFlow = () => {
        const metadata = getFlowMetadata();

        return http
            .get(
                `${apiUrl(store)}/flows/${metadata.namespace}/${
                    metadata.id
                }/dependencies`,
                {params: {destinationOnly: true}}
            )
            .then((response) => {
                let warning = "";

                if (response.data && response.data.nodes) {
                    const deps = response.data.nodes
                        .filter(
                            (n) =>
                                !(
                                    n.namespace === metadata.namespace &&
                                    n.id === metadata.id
                                )
                        )
                        .map(
                            (n) =>
                                "<li>" +
                                n.namespace +
                                ".<code>" +
                                n.id +
                                "</code></li>"
                        )
                        .join("\n");

                    if(deps.length){
                        warning =
                            "<div class=\"el-alert el-alert--warning is-light mt-3\" role=\"alert\">\n" +
                            "<div class=\"el-alert__content\">\n" +
                            "<p class=\"el-alert__description\">\n" +
                            t("dependencies delete flow") +
                            "<ul>\n" +
                            deps +
                            "</ul>\n" +
                            "</p>\n" +
                            "</div>\n" +
                            "</div>";
                    }
                }

                return t("delete confirm", {name: metadata.id}) + warning;
            })
            .then((message) => {
                toast.confirm(message, () => {
                    return store
                        .dispatch("flow/deleteFlow", metadata)
                        .then(() => {
                            return router.push({
                                name: "flows/list",
                                params: {
                                    tenant: routeParams.tenant,
                                },
                            });
                        })
                        .then(() => {
                            toast.deleted(metadata.id);
                        });
                });
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

    watch(currentTab, (current, previous) => {
        const isCurrentFlow = current?.name === "Flow";
        const isPreviousFlow = previous?.name === "Flow";

        if(isPreviousFlow) persistViewType(viewType.value);
        switchViewType(isCurrentFlow ? loadViewType() : editorViewTypes.SOURCE, false)

        nextTick(() => {
            const activeTabElement = tabsScrollRef.value.wrapRef.querySelector(".tab-active");
            const rightMostCurrentTabPixel = activeTabElement?.offsetLeft + activeTabElement?.clientWidth;

            const tabsWrapper = tabsScrollRef.value.wrapRef;
            tabsScrollRef.value.setScrollLeft(rightMostCurrentTabPixel - tabsWrapper.clientWidth);
        });
    })

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
        store.commit("editor/changeOpenedTabs", {action: "close", ...tab, index});
    };

    const closeTabs = (tabsToClose, openTab) => {
        tabsToClose.forEach(tab => {
            store.commit("editor/changeOpenedTabs", {action: "close", ...tab});
        });
        store.commit("editor/changeOpenedTabs", {action: "open", ...openTab});
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

    const exportYaml = () => {
        const blob = new Blob([flowYaml.value], {type: "text/yaml"});
        localUtils.downloadUrl(window.URL.createObjectURL(blob), "flow.yaml");
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

        &::-webkit-scrollbar {
            width: 10px;
            height: 2px;
        }

        &::-webkit-scrollbar-track {
            background: var(--ks-background-card);
        }

        &::-webkit-scrollbar-thumb {
            background: var(--ks-button-background-primary);
            border-radius: 20px;
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
        margin-top: 5em 10em;
        text-align: center;

        .img {
            background: url("../../assets/errors/kestra-error.png") no-repeat center;
            background-size: contain;
        }

        h2 {
            line-height: 30px;
            font-size: 20px;
            font-weight: 600;
        }

        p {
            line-height: 22px;
            font-size: 14px;
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