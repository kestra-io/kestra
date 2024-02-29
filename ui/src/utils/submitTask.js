export const executeTask = (submitor, flow, values, options) => {
    const formData = new FormData();
    for (let input of flow.inputs || []) {
        const inputName = input.id;
        const inputValue = values[inputName];
        if (inputValue !== undefined) {
            if (input.type === "DATETIME") {
                formData.append(inputName, submitor.$moment(inputValue).toISOString());
            } else if (input.type === "DATE") {
                formData.append(inputName, submitor.$moment(inputValue).format("YYYY-MM-DD"));
            } else if (input.type === "TIME") {
                formData.append(inputName, submitor.$moment(inputValue).format("hh:mm:ss"));
            } else if (input.type === "DURATION") {
                formData.append(inputName, submitor.$moment.duration(submitor.$moment(inputValue).format("hh:mm:ss")));
            } else if (input.type === "FILE") {
                if(typeof(inputValue) === "string"){
                    formData.append(inputName, inputValue);
                }else {
                    formData.append("files", inputValue, inputName);
                }
            } else {
                formData.append(inputName, inputValue);
            }
        } else if (input.required) {
            submitor.$toast().error(
                submitor.$t("invalid field", {name: inputName}),
                submitor.$t("form error")
            )

            return;
        }
    }
    submitor.$store
        .dispatch("execution/triggerExecution", {
            ...options,
            formData
        })
        .then(response => {
            submitor.$store.commit("execution/setExecution", response.data)
            if (options.redirect) {
                if (options.newTab) {
                    const resolved = submitor.$router.resolve({
                        name: "executions/update",
                        params: {
                            namespace: response.data.namespace,
                            flowId: response.data.flowId,
                            id: response.data.id,
                            tab: "gantt",
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
                            tab: "gantt",
                            tenant: submitor.$route.params.tenant
                        }
                    })
                }
            }
            return response.data;
        })
        .then((execution) => {
            submitor.$toast().success(submitor.$t("triggered done", {name: execution.id}));
        })
}
