import {ref, computed} from "vue"
import {canSaveFlowTemplate, saveFlowTemplate} from "../utils/flowTemplate"
import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils"
import action from "../models/action"
import permission from "../models/permission"
import {pageFromRoute} from "../utils/eventsRouter"
import {apiUrl} from "override/utils/route"
import {useApiStore} from "../stores/api"
import {usePluginsStore} from "../stores/plugins"
import {useCoreStore} from "../stores/core"
import {useTemplateStore} from "../stores/template"
import {useAuthStore} from "override/stores/auth"
import {useFlowStore} from "../stores/flow"

type LinkType = {
    name: string
    query?: Record<string, string>
}

export function useFlowTemplateEdit(dataType: string, route: any, router: any, toast: any, t: any, http: any, tours: any) {
    const apiStore = useApiStore()
    const pluginsStore = usePluginsStore()
    const coreStore = useCoreStore()
    const templateStore = useTemplateStore()
    const authStore = useAuthStore()
    const flowStore = useFlowStore()

    const content = ref("")
    const previousContent = ref("")
    const readOnlyEditFields = ref<Record<string, any>>({})

    const guidedProperties = computed(() => coreStore.guidedProperties)

    const isEdit = computed(() => (
        route.name === `${dataType}s/update` &&
        (dataType === "template" || route.params.tab === "source")
    ))

    const canSave = computed(() => canSaveFlowTemplate(true, authStore.user, item.value, dataType))

    const canCreate = computed(() => dataType === "flow" && authStore.user.isAllowed(permission.FLOW, action.CREATE, item.value?.namespace))

    const canExecute = computed(() => dataType === "flow" && authStore.user.isAllowed(permission.EXECUTION, action.CREATE, item.value?.namespace))

    const routeInfo = computed(() => {
        const routeInfo = {
            title: isEdit.value ? route.params.id : t(`${dataType}`),
            breadcrumb: [
                {
                    label: t(`${dataType}s`),
                    link: {
                        name: `${dataType}s/list`
                    } as LinkType
                }
            ]
        }

        if (isEdit.value) {
            routeInfo.breadcrumb.push(
                {
                    label: route.params.namespace,
                    link: {
                        name: `${dataType}s/list`,
                        query: {
                            namespace: route.params.namespace
                        }
                    } as LinkType
                }
            )
        }

        return routeInfo
    })

    const item = computed(() => dataType === "template" ? templateStore.template : flowStore.flow)

    const canDelete = computed(() => (
        item.value &&
        isEdit.value &&
        authStore.user?.isAllowed(
            permission[dataType.toUpperCase() as keyof typeof permission],
            action.DELETE,
            item.value.namespace
        )
    ))

    const loadFile = () => {
        if (route.query.copy) {
            item.value!.id = ""
            item.value!.namespace = ""
            delete item.value!.revision
        }

        if (dataType === "template") {
            content.value = YAML_UTILS.stringify(templateStore.template)
            previousContent.value = content.value
        } else {
            if (flowStore.flow) {
                content.value = flowStore.flow.source
                previousContent.value = content.value
            } else {
                content.value = ""
                previousContent.value = ""
            }
        }

        if (isEdit.value) {
            readOnlyEditFields.value = {
                id: item.value!.id
            }
        }
    }

    const deleteConfirmMessage = () => {
        if (dataType === "template") {
            return Promise.resolve(t("delete confirm", {name: item.value!.id}))
        }

        return http
            .get(`${apiUrl()}/flows/${flowStore.flow!.namespace}/${flowStore.flow!.id}/dependencies`, {params: {destinationOnly: true}})
            .then((response: any) => {
                let warning = ""

                if (response.data && response.data.nodes) {
                    const deps = response.data.nodes
                        .filter((n: any) => !(n.namespace === flowStore.flow!.namespace && n.id === flowStore.flow!.id))
                        .map((n: any) => "<li>" + n.namespace + ".<code>" + n.id + "</code></li>")
                        .join("\n")

                    warning = "<div class=\"el-alert el-alert--warning is-light mt-3\" role=\"alert\">\n" +
                        "<div class=\"el-alert__content\">\n" +
                        "<p class=\"el-alert__description\">\n" +
                        t("dependencies delete flow") +
                        "<ul>\n" +
                        deps +
                        "</ul>\n" +
                        "</p>\n" +
                        "</div>\n" +
                        "</div>"
                }

                return t("delete confirm", {name: item.value!.id}) + warning
            })
    }

    const deleteFile = () => {
        if (item.value) {
            const itemToDelete = item.value

            deleteConfirmMessage()
                .then((message: any) => {
                    toast()
                        .confirm(message, () => {
                            const deletePromise = dataType === "template"
                                ? templateStore.deleteTemplate(itemToDelete)
                                : dataType === "flow"
                                    ? flowStore.deleteFlow(itemToDelete)
                                    : undefined

                            return deletePromise
                                ?.then(() => {
                                    content.value = ""
                                    previousContent.value = ""
                                    return router.push({
                                        name: dataType + "s/list",
                                        params: {
                                            tenant: route.params.tenant
                                        }
                                    })
                                })
                                .then(() => {
                                    toast().deleted(itemToDelete.id)
                                })
                        })
                })
        }
    }

    const save = () => {
        if (tours["guidedTour"]?.isRunning?.value && !guidedProperties.value.saveFlow) {
            apiStore.events({
                type: "ONBOARDING",
                onboarding: {
                    step: tours["guidedTour"]?.currentStep?._value,
                    action: "next",
                    template: guidedProperties.value.template
                },
                page: pageFromRoute(router.currentRoute.value)
            })
            tours["guidedTour"]?.nextStep()
            return
        }

        if (item.value) {
            let parsedItem
            try {
                parsedItem = YAML_UTILS.parse(content.value)
            } catch (err: any) {
                toast().warning(
                    err.message,
                    t("invalid yaml")
                )
                return
            }
            if (isEdit.value) {
                for (const key in readOnlyEditFields.value) {
                    if (parsedItem[key] !== readOnlyEditFields.value[key]) {
                        toast().warning(t("read only fields have changed (id, namespace...)"))
                        return
                    }
                }
            }
            previousContent.value = content.value
            saveFlowTemplate({templateStore, flowStore, $toast: toast}, content.value, dataType)
                .then((flow: any) => {
                    previousContent.value = YAML_UTILS.stringify(flow)
                    content.value = YAML_UTILS.stringify(flow)
                    onChange()
                    loadFile()
                })
        } else {
            let parsedItem
            try {
                parsedItem = YAML_UTILS.parse(content.value)
            } catch (err: any) {
                toast().warning(
                    err.message,
                    t("invalid yaml")
                )
                return
            }
            previousContent.value = YAML_UTILS.stringify(item.value)
            const createPromise = dataType === "template"
                ? templateStore.createTemplate({template: content.value})
                : dataType === "flow"
                    ? flowStore.createFlow({flow: content.value})
                    : undefined

            createPromise
                ?.then((data: any) => {
                    previousContent.value = data.source ? data.source : YAML_UTILS.stringify(data)
                    content.value = data.source ? data.source : YAML_UTILS.stringify(data)
                    onChange()
                    router.push({
                        name: `${dataType}s/update`,
                        params: {
                            ...parsedItem,
                            tab: "source",
                            tenant: route.params.tenant
                        }
                    })
                })
                .then(() => {
                    toast().saved(parsedItem.id)
                })
        }
    }

    const updatePluginDocumentation = (event: any) => {
        const elementWrapper = YAML_UTILS.localizeElementAtIndex(event.model.getValue(), event.model.getOffsetAt(event.position))
        const element = elementWrapper?.value?.type !== undefined ? elementWrapper.value : elementWrapper?.parents?.findLast((p: any) => p.type !== undefined)
        pluginsStore.updateDocumentation(element as any)
    }

    const onChange = () => { }

    return {
        content,
        previousContent,
        readOnlyEditFields,
        guidedProperties,
        isEdit,
        canSave,
        canCreate,
        canExecute,
        routeInfo,
        item,
        canDelete,
        loadFile,
        deleteConfirmMessage,
        deleteFile,
        save,
        updatePluginDocumentation
    }
}