export const executeTask = (submitor, flow, options) => {
    const formData = new FormData();
    for (let input of flow.inputs || []) {
        if (input.value !== undefined) {
            if (input.type === "DATETIME") {
                formData.append(input.name, input.value.toISOString());
            } else if (input.type === "DATE") {
                formData.append(input.name, submitor.$moment(input.value).format("YYYY-MM-DD"));
            } else if (input.type === "TIME") {
                formData.append(input.name, submitor.$moment(input.value).format("hh:mm:ss"));
            } else if (input.type === "DURATION") {
                formData.append(input.name, submitor.$moment.duration(submitor.$moment(input.value).format("hh:mm:ss")));
            } else if (input.type === "FILE") {
                formData.append("files", input.value, input.name);
            } else {
                formData.append(input.name, input.value);
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
                submitor.$router.push({name: "executions/update", params: {...response.data, ...{tab: "gantt"}}})
            }

            return response.data;
        })
        .then((execution) => {
            submitor.$store.dispatch("core/showMessage", {
                title: submitor.$t("success"),
                message: submitor.$t("triggered done", {name: execution.id}),
                variant: "success",
                timeout: 5000
            })
        })
}
