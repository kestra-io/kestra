export const executeTask = (submitor, flow, values, options) => {
    const formData = new FormData();
    for (let input of flow.inputs || []) {
        if (values[input.name] !== undefined) {
            if (input.type === "DATETIME") {
                formData.append(input.name, values[input.name].toISOString());
            } else if (input.type === "DATE") {
                formData.append(input.name, submitor.$moment(values[input.name]).format("YYYY-MM-DD"));
            } else if (input.type === "TIME") {
                formData.append(input.name, submitor.$moment(values[input.name]).format("hh:mm:ss"));
            } else if (input.type === "DURATION") {
                formData.append(input.name, submitor.$moment.duration(submitor.$moment(values[input.name]).format("hh:mm:ss")));
            } else if (input.type === "FILE") {
                formData.append("files", values[input.name], input.name);
            } else {
                formData.append(input.name, values[input.name]);
            }
        } else if (input.required) {
            submitor.$toast().error(
                submitor.$t("invalid field", {name: input.name}),
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
                submitor.$router.push({name: "executions/update", params: {...{namespace: response.data.namespace, flowId: response.data.flowId, id: response.data.id}, ...{tab: "gantt"}}})
            }

            return response.data;
        })
        .then((execution) => {
            submitor.$toast().success(submitor.$t("triggered done", {name: execution.id}));
        })
}
