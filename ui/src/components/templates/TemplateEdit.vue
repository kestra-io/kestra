<template>
    <TopNavBar :title="routeInfo.title" :breadcrumb="routeInfo.breadcrumb">
        <template #additional-right v-if="canSave || canDelete">
            <ul>
                <li>
                    <el-button :icon="Delete" size="large" type="default" v-if="canDelete" @click="deleteFile">
                        {{ $t('delete') }}
                    </el-button>

                    <template v-if="canSave">
                        <el-button :icon="ContentSave" @click="save" type="primary" size="large">
                            {{ $t('save') }}
                        </el-button>
                    </template>
                </li>
            </ul>
        </template>
    </TopNavBar>
    <TemplatesDeprecated />
    <section class="container d-flex flex-fill">
        <Editor @save="save" v-model="content" schemaType="template" lang="yaml" @update:model-value="onChange" @cursor="updatePluginDocumentation" class="w-100 h-auto" />
    </section>
</template>

<script setup lang="ts">
    import {computed, getCurrentInstance, onBeforeUnmount, onMounted, ref, watch} from "vue";
    import {useRoute, useRouter} from "vue-router";
    import {useTemplateStore} from "../../stores/template";
    import {useCoreStore} from "../../stores/core";
    import {useApiStore} from "../../stores/api";
    import {usePluginsStore} from "../../stores/plugins";
    import {useAuthStore} from "override/stores/auth";
    import {useFlowStore} from "../../stores/flow";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import {canSaveFlowTemplate, saveFlowTemplate} from "../../utils/flowTemplate";
    import {pageFromRoute} from "../../utils/eventsRouter";

    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import TemplatesDeprecated from "./TemplatesDeprecated.vue";
    import TopNavBar from "../layout/TopNavBar.vue";
    import Editor from "../inputs/Editor.vue";

    const props = defineProps({
        embed: {
            type: Boolean,
            default: false
        }
    });

    const route = useRoute();
    const router = useRouter();

    const templateStore = useTemplateStore();
    const coreStore = useCoreStore();
    const apiStore = useApiStore();
    const pluginsStore = usePluginsStore();
    const authStore = useAuthStore();
    const flowStore = useFlowStore();

    const {$t, $toast, $tours} = (getCurrentInstance()?.appContext.config.globalProperties || {}) as any;

    const dataType = "template" as const;

    const content = ref<string>("");
    const previousContent = ref<string>("");
    const readOnlyEditFields = ref<Record<string, unknown>>({});

    const isEdit = computed(() => route.name === `${dataType}s/update`);

    const item = computed<any>(() => templateStore.template);

    const routeInfo = computed(() => {
        const info: any = {
            title: isEdit.value ? (route.params as any).id : $t?.(`${dataType}`),
            breadcrumb: [
                {
                    label: $t?.(`${dataType}s`),
                    link: {
                        name: `${dataType}s/list`,
                    }
                }
            ]
        };

        if (isEdit.value) {
            info.breadcrumb.push({
                label: (route.params as any).namespace,
                link: {
                    name: `${dataType}s/list`,
                    query: {
                        namespace: (route.params as any).namespace
                    }
                }
            });
        }

        return info;
    });

    const canSave = computed(() => {
        return canSaveFlowTemplate(true, authStore.user, item.value, dataType);
    });

    const canDelete = computed(() => {
        return (
            !!item.value &&
            isEdit.value &&
            authStore.user?.isAllowed(
                permission[dataType.toUpperCase() as keyof typeof permission],
                action.DELETE,
                item.value.namespace
            )
        );
    });

    function handleTitle() {
        if (!props.embed) {
            let baseTitle: string;
            if (document.title.lastIndexOf("|") > 0) {
                baseTitle = document.title.substring(document.title.lastIndexOf("|") + 1);
            } else {
                baseTitle = document.title;
            }
            document.title = routeInfo.value.title + " | " + baseTitle;
        }
    }

    function loadFile() {
        if ((route.query as any).copy) {
            if (item.value) {
                item.value.id = "";
                item.value.namespace = "";
                delete item.value.revision;
            }
        }

        content.value = YAML_UTILS.stringify(templateStore.template);
        previousContent.value = content.value;

        if (isEdit.value) {
            readOnlyEditFields.value = {
                id: item.value?.id,
            };
        }
    }

    function deleteConfirmMessage(): Promise<string> {
        return Promise.resolve($t?.("delete confirm", {name: item.value?.id}));
    }

    function deleteFile() {
        if (!item.value) return;
        const current = item.value;
        deleteConfirmMessage().then((message) => {
            $toast?.().confirm(message, () => {
                return templateStore
                    .deleteTemplate(current)
                    .then(() => {
                        content.value = "";
                        previousContent.value = "";
                        return router.push({
                            name: `${dataType}s/list`,
                            params: {
                                tenant: (route.params as any).tenant
                            }
                        });
                    })
                    .then(() => {
                        $toast?.().deleted(current.id);
                    });
            });
        });
    }

    function onChange() {
        coreStore.unsavedChange = previousContent.value !== content.value;
    }

    function updatePluginDocumentation(event: any) {
        const elementWrapper = YAML_UTILS.localizeElementAtIndex(event.model.getValue(), event.model.getOffsetAt(event.position));
        const element = elementWrapper?.value?.type !== undefined ? elementWrapper.value : elementWrapper?.parents?.findLast((p: any) => p.type !== undefined);
        pluginsStore.updateDocumentation(element);
    }

    function save() {
        if ($tours?.["guidedTour"]?.isRunning?.value && !coreStore.guidedProperties.saveFlow) {
            apiStore.events({
                type: "ONBOARDING",
                onboarding: {
                    step: $tours?.["guidedTour"]?.currentStep?._value,
                    action: "next",
                    template: coreStore.guidedProperties.template
                },
                page: pageFromRoute(router.currentRoute.value)
            });
            $tours?.["guidedTour"]?.nextStep();
            return;
        }

        if (item.value) {
            let parsed: any;
            try {
                parsed = YAML_UTILS.parse(content.value);
            } catch (err: any) {
                $toast?.().warning(
                    err.message,
                    $t?.("invalid yaml"),
                );
                return;
            }

            if (isEdit.value) {
                for (const key in readOnlyEditFields.value) {
                    if ((parsed as any)[key] !== (readOnlyEditFields.value as any)[key]) {
                        $toast?.().warning($t?.("read only fields have changed (id, namespace...)"));
                        return;
                    }
                }
            }

            previousContent.value = content.value;
            saveFlowTemplate({
                ...getCurrentInstance()?.proxy,
                templateStore,
                flowStore,
                apiStore,
                pluginsStore,
                coreStore,
                authStore,
            } as any, content.value, dataType)
                .then((saved: any) => {
                    previousContent.value = YAML_UTILS.stringify(saved);
                    content.value = YAML_UTILS.stringify(saved);
                    onChange();
                    loadFile();
                });
        } else {
            let parsed: any;
            try {
                parsed = YAML_UTILS.parse(content.value);
            } catch (err: any) {
                $toast?.().warning(
                    err.message,
                    $t?.("invalid yaml"),
                );
                return;
            }
            previousContent.value = YAML_UTILS.stringify(item.value);
            templateStore
                .createTemplate({template: content.value})
                .then((data: any) => {
                    previousContent.value = data.source ? data.source : YAML_UTILS.stringify(data);
                    content.value = data.source ? data.source : YAML_UTILS.stringify(data);
                    onChange();
                    router.push({
                        name: `${dataType}s/update`,
                        params: {
                            ...parsed,
                            tab: "source",
                            tenant: (route.params as any).tenant
                        }
                    });
                })
                .then(() => {
                    $toast?.().saved(parsed.id);
                });
        }
    }

    function reload() {
        if (route.name === "templates/update") {
            templateStore
                .loadTemplate(route.params as any)
                .then(loadFile);
        }
    }

    watch(() => route.params, () => reload());

    onMounted(() => {
        reload();
        handleTitle();
    });

    watch([routeInfo, () => route.fullPath], () => handleTitle());

    onBeforeUnmount(() => {
        templateStore.template = undefined as any;
    });

    defineExpose({
        save,
        deleteFile,
        onChange,
        updatePluginDocumentation,
        content,
        routeInfo,
        canSave,
        canDelete
    });
</script>
