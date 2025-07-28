import _cloneDeep from "lodash/cloneDeep"
import {useExecutionsStore} from "../stores/executions"

export const inputsToFormData = (submitor, inputsList, values) => {
    let inputValuesCloned = _cloneDeep(values)

    for (const input of inputsList || []) {
        if (inputValuesCloned[input.id] === undefined || inputValuesCloned[input.id] === "") {
            delete inputValuesCloned[input.id];
        }

        // Required to have "undefined" value for boolean
        if (input.type === "BOOLEAN" && inputValuesCloned[input.id] === "undefined") {
            inputValuesCloned[input.id] = undefined;
        }
    }

    if (Object.keys(inputValuesCloned).length === 0) {
        return;
    }

    const formData = new FormData();

    for (let input of inputsList || []) {
        const inputName = input.id;
        const inputValue = inputValuesCloned[inputName];
        if (inputValue !== undefined) {
            if (input.type === "DATETIME" && inputValue) {
                formData.append(inputName, submitor.$moment(inputValue).toISOString());
            } else if (input.type === "DATE" && inputValue) {
                formData.append(inputName, submitor.$moment(inputValue).format("YYYY-MM-DD"));
            } else if (input.type === "TIME") {
                formData.append(inputName, submitor.$moment(inputValue).format("hh:mm:ss"));
            } else {
                formData.append(inputName, inputValue);
            }
        }
    }

    return formData;
}

export const executeTask = (submitor, flow, values, options) => {
    const formData = inputsToFormData(submitor, flow.inputs, values);
    const executionsStore = useExecutionsStore();

    executionsStore
        .triggerExecution({
            ...options,
            formData
        })
        .then(response => {
            executionsStore.execution = response.data;
            if (options.redirect) {
                if (options.newTab) {
                    const resolved = submitor.$router.resolve({
                        name: "executions/update",
                        params: {
                            namespace: response.data.namespace,
                            flowId: response.data.flowId,
                            id: response.data.id,
                            tab: localStorage.getItem("executeDefaultTab") || "gantt",
                            tenant: submitor.$route.params.tenant
                        }
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
                            tenant: submitor.$route.params.tenant
                        }
                    })
                }
            }

            if(options.nextStep) submitor.$tours["guidedTour"]?.nextStep();

            return response.data;
        })
        .then((execution) => {
            if(!options.nextStep){
                submitor.$toast().success(submitor.$t("triggered done", {name: execution.id}));
            }
        })
}
