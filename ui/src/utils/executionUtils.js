import {apiUrl} from "override/utils/route";

export default class ExecutionUtils {
    static waitFor($http, store, execution, predicate) {
        return new Promise((resolve) => {
            let callback = () => {
                $http.get(`${apiUrl(store)}/executions/${execution.id}`).then(response => {
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

    static findTaskRunsByState(execution, state)  {
        return execution.taskRunList.filter((taskRun) => taskRun.state.current === state);
    }

    static statePredicate(execution, current) {
        return current.state.histories.length >= execution.state.histories.length
    }

    static waitForState($http, store, execution) {
        return ExecutionUtils.waitFor($http, store, execution, (current) => {
            return ExecutionUtils.statePredicate(execution, current);
        })
    }
}
