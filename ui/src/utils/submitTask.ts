import _cloneDeep from "lodash/cloneDeep"
import {useExecutionsStore, type Execution} from "../stores/executions"
import {useProductTourStore} from "../stores/productTour"
import {Router, type useRoute} from "vue-router"
import {Flow} from "../stores/flow"
import {flattenInputs} from "./inputs"
import {EXECUTION_TAB_ROUTES} from "../components/executions/executionTabs"
import {resolveDefaultTab} from "./routeTabs"

export const normalizeInputValues = (
    submitor: { $moment: (date: any) => { toISOString: () => string; format: (format: string) => string } },
    inputsList: {id:string, type?: string}[] | undefined,
    values: Record<string, any>,
): Record<string, any> | undefined => {

    let inputValuesCloned = _cloneDeep(values)

    for (const input of inputsList || []) {
        if (inputValuesCloned[input.id] === undefined || inputValuesCloned[input.id] === null || inputValuesCloned[input.id] === "") {
            delete inputValuesCloned[input.id]
        }
    }

    if (Object.keys(inputValuesCloned).length === 0) {
        return undefined
    }

    const normalized: Record<string, any> = {}

    for (let input of inputsList || []) {
        const inputName = input.id
        const inputValue = inputValuesCloned[inputName]
        if (inputValue !== undefined) {
            if (input.type === "DATETIME" && inputValue) {
                normalized[inputName] = submitor.$moment(inputValue).toISOString()
            } else if (input.type === "DATE" && inputValue) {
                normalized[inputName] = submitor.$moment(inputValue).format("YYYY-MM-DD")
            } else if (input.type === "TIME") {
                normalized[inputName] = submitor.$moment(inputValue).format("hh:mm:ss")
            } else {
                normalized[inputName] = inputValue
            }
        }
    }

    return normalized
}

// Only Resume.vue/ReplayWithInputs.vue need an actual FormData instance (they post it via raw
// axios without the SDK's own multipart serialization); executeTask() below uses
// normalizeInputValues() directly since triggerExecution() now builds its own FormData via
// ExecutionsAPI.createExecution()'s multipart body serializer.
export const inputsToFormData = (
    submitor: { $moment: (date: any) => { toISOString: () => string; format: (format: string) => string } },
    inputsList: {id:string, type?: string}[] | undefined,
    values: Record<string, any>,
) => {
    const normalized = normalizeInputValues(submitor, inputsList, values)
    if (!normalized) {
        return undefined
    }

    const formData = new FormData()
    for (const [key, value] of Object.entries(normalized)) {
        formData.append(key, value)
    }
    return formData
}

export const executeTask = (
    submitor: { 
        $router: Router, 
        $route: ReturnType<typeof useRoute>, 
        $toast: () => { success: (message: string) => void }, 
        $t: (key: string, params?: Record<string, any>) => string, 
        $moment: (date: any) => { toISOString: () => string; format: (format: string) => string } 
    }, 
    flow: Flow, 
    values: Record<string, any>,
    options: Omit<Parameters<ReturnType<typeof useExecutionsStore>["triggerExecution"]>[0], "formData" | "kind"> & { redirect?: boolean; newTab?: boolean; query?: Record<string, any>; nextStep?: boolean },
): Promise<Execution> => {
    const formData = normalizeInputValues(submitor, flattenInputs(flow.inputs), values)
    const executionsStore = useExecutionsStore()
    const productTourStore = useProductTourStore()

    return executionsStore
        .triggerExecution({
            ...options,
            kind: "NORMAL",
            formData,
        })
        .then(response => {
            executionsStore.execution = response
            productTourStore.recordExecution()
            if (options.redirect) {
                const tab = resolveDefaultTab(EXECUTION_TAB_ROUTES, localStorage.getItem("executeDefaultTab"), "gantt")
                if (options.newTab) {
                    const resolved = submitor.$router.resolve({
                        name: `executions/update/${tab}`,
                        params: {
                            namespace: response.namespace,
                            flowId: response.flowId,
                            id: response.id,
                            tenant: submitor.$route.params.tenant,
                        },
                        query: options.query,
                    })
                    window.open(resolved.href, "_blank")
                } else {
                    submitor.$router.push({
                        name: `executions/update/${tab}`,
                        params: {
                            namespace: response.namespace,
                            flowId: response.flowId,
                            id: response.id,
                            tenant: submitor.$route.params.tenant,
                        },
                        query: options.query,
                    })
                }
            }
            return response
        })
        .then((execution) => {
            if (!options.nextStep) {
                submitor.$toast().success(submitor.$t("triggered done", {name: execution.id}))
            }
            return execution
        })
}
