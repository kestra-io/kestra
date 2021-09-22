import Vue from "vue"

export default class ExecutionUtils {
    static waitFor(execution, predicate) {
        return new Promise((resolve) => {
            let callback = () => {
                Vue.axios.get(`/api/v1/executions/${execution.id}`).then(response => {
                    const result = predicate(response.data)

                    if (result === true) {
                        resolve(response.data)
                    } else {
                        window.setTimeout(() => {
                            callback()
                        }, 300)
                    }
                })

            };

            window.setTimeout(() => {
                callback()
            }, 300)
        });
    }

    static statePredicate(execution, current) {
        return current.state.histories.length >= execution.state.histories.length
    }

    static waitForState(execution) {
        return ExecutionUtils.waitFor(execution, (current) => {
            return ExecutionUtils.statePredicate(execution, current);
        })
    }
}
