import type {useClient} from "@kestra-io/kestra-sdk"
import {apiUrl} from "override/utils/route"
import type {Execution} from "../stores/executions"

type KestraClient = ReturnType<typeof useClient>

export function waitFor($http: KestraClient, execution: {id: string}, predicate: (data: any) => boolean) {
    return new Promise((resolve) => {
        const callback = () => {
            $http.get(`${apiUrl()}/executions/${execution.id}`).then(response => {
                const result = predicate(response.data)

                if (result === true) {
                    resolve(response.data)
                } else {
                    window.setTimeout(() => {
                        callback()
                    }, 300)
                }
            })

        }

        window.setTimeout(() => {
            callback()
        }, 300)
    })
}

export function findTaskRunsByState(execution: Execution, state: string)  {
    return (execution.taskRunList ?? []).filter((taskRun) => taskRun.state?.current === state)
}

export function statePredicate(execution: Execution, current: {state: {histories?: any[]}}) {
    return (current.state.histories?.length ?? 0) >= (execution.state.histories?.length ?? 0)
}

export function waitForState($http: KestraClient, execution: Execution) {
    return waitFor($http, execution, (current) => {
        return statePredicate(execution, current)
    })
}
