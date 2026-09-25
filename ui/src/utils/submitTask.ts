import {cloneDeep, dayjs} from "@kestra-io/design-system"
import {useExecutionsStore, type Execution} from "../stores/executions"
import {Router, type useRoute} from "vue-router"
import {Flow} from "../stores/flow"
import {flattenInputs} from "./inputs"
import {DEFAULT_EXECUTION_TAB, DEFAULT_TAB_STORAGE_KEY, EXECUTION_TAB_ROUTES} from "../components/executions/executionTabs"
import {resolveDefaultTab} from "./routeTabs"

export const normalizeInputValues = (
    inputsList: {id:string, type?: string}[] | undefined,
    values: Record<string, any>,
): Record<string, any> | undefined => {

    let inputValuesCloned = cloneDeep(values)

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
                normalized[inputName] = dayjs(inputValue).toISOString()
            } else if (input.type === "DATE" && inputValue) {
                normalized[inputName] = dayjs(inputValue).format("YYYY-MM-DD")
            } else if (input.type === "TIME") {
                normalized[inputName] = dayjs(inputValue).format("HH:mm:ss")
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
    inputsList: {id:string, type?: string}[] | undefined,
    values: Record<string, any>,
) => {
    const normalized = normalizeInputValues(inputsList, values)
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
    }, 
    flow: Flow, 
    values: Record<string, any>,
    options: Omit<Parameters<ReturnType<typeof useExecutionsStore>["triggerExecution"]>[0], "formData" | "kind"> & { redirect?: boolean; newTab?: boolean; query?: Record<string, any>; nextStep?: boolean },
): Promise<Execution> => {
    const formData = normalizeInputValues(flattenInputs(flow.inputs), values)
    const executionsStore = useExecutionsStore()

    return executionsStore
        .triggerExecution({
            ...options,
            kind: "NORMAL",
            formData,
        })
        .then(response => {
            executionsStore.applyLocalExecutionUpdate(response)
            if (options.redirect) {
                const tab = resolveDefaultTab(EXECUTION_TAB_ROUTES, localStorage.getItem(DEFAULT_TAB_STORAGE_KEY), DEFAULT_EXECUTION_TAB)
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
