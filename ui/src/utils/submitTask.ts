import _cloneDeep from "lodash/cloneDeep"
import {useExecutionsStore} from "../stores/executions"
import {useOnboardingV2Store} from "../stores/onboardingV2"
import {Router, type useRoute} from "vue-router"
import {Flow} from "../stores/flow"

export const inputsToFormData = (
    submitor: { $moment: (date: any) => { toISOString: () => string; format: (format: string) => string } }, 
    inputsList: {id:string, type?: string}[] | undefined, 
    values: Record<string, any>,
) => {

    let inputValuesCloned = _cloneDeep(values)

    for (const input of inputsList || []) {
        if (inputValuesCloned[input.id] === undefined || inputValuesCloned[input.id] === null || inputValuesCloned[input.id] === "") {
            delete inputValuesCloned[input.id]
        }
    }

    if (Object.keys(inputValuesCloned).length === 0) {
        return
    }

    const formData = new FormData()

    for (let input of inputsList || []) {
        const inputName = input.id
        const inputValue = inputValuesCloned[inputName]
        if (inputValue !== undefined) {
            if (input.type === "DATETIME" && inputValue) {
                formData.append(inputName, submitor.$moment(inputValue).toISOString())
            } else if (input.type === "DATE" && inputValue) {
                formData.append(inputName, submitor.$moment(inputValue).format("YYYY-MM-DD"))
            } else if (input.type === "TIME") {
                formData.append(inputName, submitor.$moment(inputValue).format("hh:mm:ss"))
            } else {
                formData.append(inputName, inputValue)
            }
        }
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
    options: Omit<Parameters<ReturnType<typeof useExecutionsStore>["triggerExecution"]>[0], "formData"> & { redirect?: boolean; newTab?: boolean; query?: Record<string, any>; nextStep?: boolean },
) => {
    const formData = inputsToFormData(submitor, flow.inputs, values)
    const executionsStore = useExecutionsStore()
    const onboardingV2Store = useOnboardingV2Store()

    executionsStore
        .triggerExecution({
            ...options,
            kind: "NORMAL",
            formData,
        })
        .then(response => {
            executionsStore.execution = response.data
            onboardingV2Store.recordExecution()
            if (options.redirect) {
                if (options.newTab) {
                    const resolved = submitor.$router.resolve({
                        name: "executions/update",
                        params: {
                            namespace: response.data.namespace,
                            flowId: response.data.flowId,
                            id: response.data.id,
                            tab: localStorage.getItem("executeDefaultTab") || "gantt",
                            tenant: submitor.$route.params.tenant,
                        },
                        query: options.query,
                    })
                    window.open(resolved.href, "_blank")
                } else {
                    submitor.$router.push({
                        name: "executions/update",
                        params: {
                            namespace: response.data.namespace,
                            flowId: response.data.flowId,
                            id: response.data.id,
                            tab: localStorage.getItem("executeDefaultTab") || "gantt",
                            tenant: submitor.$route.params.tenant,
                        },
                        query: options.query,
                    })
                }
            }
            return response.data
        })
        .then((execution) => {
            if(!options.nextStep){
                submitor.$toast().success(submitor.$t("triggered done", {name: execution.id}))
            }
        })
}
