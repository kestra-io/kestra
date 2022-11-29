
export default class ExecutionUtils {
    static waitFor($http, execution, predicate) {
        return new Promise((resolve) => {
            let callback = () => {
                $http.get(`/api/v1/executions/${execution.id}`).then(response => {
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

    static waitForState($http, execution) {
        return ExecutionUtils.waitFor($http, execution, (current) => {
            return ExecutionUtils.statePredicate(execution, current);
        })
    }
}
