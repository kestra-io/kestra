import {useI18n} from "vue-i18n"
import {useToast} from "../../utils/toast"
import * as FlowsAPI from "@kestra-io/kestra-sdk/flows"
import * as Utils from "../../utils/utils"
import {computed, Ref} from "vue"
import {useFlowStore} from "../../stores/flow"
import {routeQueryToQueryFilters} from "@kestra-io/design-system"
import {IdWithNamespace} from "@kestra-io/kestra-sdk"
import {LocationQuery} from "vue-router"


export default function useFlowsBulkActions(options: {
    loadQuery: () => LocationQuery,
    dataTable: Ref<{
        selection: (IdWithNamespace & {enabled: boolean})[],
        queryBulkAction: boolean,
        toggleAllUnselected: () => void,
        reload: () => void,
    } | null>,
    file: Ref<HTMLInputElement | null>,

}) {
    const {loadQuery, dataTable, file} = options
    const selection = computed<(IdWithNamespace & {enabled: boolean})[]>(() => dataTable.value?.selection ?? [])
    const toast = useToast()
    const flowStore = useFlowStore()

    const queryBulkAction = computed(() => dataTable.value?.queryBulkAction ?? false)
    const toggleAllUnselected = () => dataTable.value?.toggleAllUnselected()

    const selectionIds = computed(() => selection.value.map((flow: any) => ({id: flow.id, namespace: flow.namespace})))

    const {t} = useI18n()

    function exportFlows() {
        toast.confirm(
            t("flow export", {flowCount: queryBulkAction.value ? flowStore.total : selection.value.length}),
            () => {
                const flowCount = queryBulkAction.value ? flowStore.total : selection.value.length
                if (queryBulkAction.value) {
                    return FlowsAPI.exportFlowsByQuery({filters: routeQueryToQueryFilters(loadQuery())}, {responseType: "blob"}).then(data => {
                        const blob = new Blob([data], {type: "application/octet-stream"})
                        const url = window.URL.createObjectURL(blob)
                        Utils.downloadUrl(url, "flows.zip")
                    }).then(() => {
                        toast.success(t("flows exported", {count: flowCount}))
                        toggleAllUnselected()
                    })
                } else {
                    return FlowsAPI.exportFlowsByIds({body: selection.value}, {responseType: "blob"}).then(data => {
                        const blob = new Blob([data], {type: "application/octet-stream"})
                        const url = window.URL.createObjectURL(blob)
                        Utils.downloadUrl(url, "flows.zip")
                    }).then(() => {
                        toast.success(t("flows exported", {count: flowCount}))
                        toggleAllUnselected()
                    })
                }
            },
        )
    }

    function disableFlows() {
        toast.confirm(
            t("flow disable", {flowCount: queryBulkAction.value ? flowStore.total : selection.value.length}),
            () => {
                if (queryBulkAction.value) {
                    return FlowsAPI.disableFlowsByQuery({filters: routeQueryToQueryFilters(loadQuery())}).then((r) => {
                        toast.success(t("flows disabled", {count: r.count}))
                        toggleAllUnselected()
                        dataTable.value?.reload()
                    })
                } else {
                    return FlowsAPI.disableFlowsByIds({body: selectionIds.value}).then((r) => {
                        toast.success(t("flows disabled", {count: r.count}))
                        toggleAllUnselected()
                        dataTable.value?.reload()
                    })
                }
            },
        )
    }

    const anyFlowDisabled = computed(() => {
        return selection.value.some((flow) => !flow.enabled)
    })
    const anyFlowEnabled = computed(() => {
        return selection.value.some((flow) => flow.enabled)
    })

    function enableFlows() {

        toast.confirm(
            t("flow enable", {flowCount: queryBulkAction.value ? flowStore.total : selection.value.length}),
            () => {
                if (queryBulkAction.value) {
                    return FlowsAPI.enableFlowsByQuery({filters: routeQueryToQueryFilters(loadQuery())}).then((r) => {
                        toast.success(t("flows enabled", {count: r.count}))
                        toggleAllUnselected()
                        dataTable.value?.reload()
                    })
                } else {
                    return FlowsAPI.enableFlowsByIds({body: selectionIds.value}).then((r) => {
                        toast.success(t("flows enabled", {count: r.count}))
                        toggleAllUnselected()
                        dataTable.value?.reload()
                    })
                }
            },
        )
    }

    function deleteFlows() {
        toast.confirm(
            t("flow delete", {flowCount: queryBulkAction.value ? flowStore.total : selection.value.length}),
            () => {
                if (queryBulkAction.value) {
                    return FlowsAPI.deleteFlowsByQuery({filters: routeQueryToQueryFilters(loadQuery())}).then((r) => {
                        toast.success(t("flows deleted", {count: r.count}))
                        toggleAllUnselected()
                        dataTable.value?.reload()
                    })
                } else {
                    return FlowsAPI.deleteFlowsByIds({body: selectionIds.value}).then((r) => {
                        toast.success(t("flows deleted", {count: r.count}))
                        toggleAllUnselected()
                        dataTable.value?.reload()
                    })
                }
            },
        )
    }

    function importFlows() {
        const formData = new FormData()
        if (file.value && file.value.files && file.value.files[0]) {
            formData.append("fileUpload", file.value.files[0])
            FlowsAPI.importFlows({fileUpload: file.value.files[0]}).then((data) => {
                if (data.length > 0) {
                    toast.warning(t("flows not imported") + ": " + data.join(", "))
                } else {
                    toast.success(t("flows imported"))
                }
                if (file.value) file.value.value = ""
                dataTable.value?.reload()
            })
        }
    }
    
    async function exportFlowsAsStream(query: LocationQuery) {
        const data = await FlowsAPI.exportFlowsByQuery(
            {filters: routeQueryToQueryFilters(query)},
            {headers: {Accept: "text/csv"}},
        )
        const url = window.URL.createObjectURL(new Blob([data]))
        const link = document.createElement("a")
        link.href = url
        link.setAttribute("download", "flows.csv")
        document.body.appendChild(link)
        link.click()
        link.remove()
        window.URL.revokeObjectURL(url)
    }

    return {
        exportFlows,
        exportFlowsAsStream,
        disableFlows,
        enableFlows,
        deleteFlows,
        importFlows,
        anyFlowDisabled,
        anyFlowEnabled,
    }
}