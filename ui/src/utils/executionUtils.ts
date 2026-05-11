import {AxiosInstance} from "axios"
import {apiUrl} from "override/utils/route"

interface Execution {
    id: string,
    state: {
        histories?: any[]
    },
    taskRunList: Array<{
        state?: {
            current: string
        }
    }>
}

export function waitFor($http: AxiosInstance, execution: {id: string}, predicate: (data: any) => boolean) {
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
    return execution.taskRunList.filter((taskRun) => taskRun.state?.current === state)
}

export function statePredicate(execution: Execution, current: {state: {histories?: any[]}}) {
    return (current.state.histories?.length ?? 0) >= (execution.state.histories?.length ?? 0)
}

export function waitForState($http: AxiosInstance, execution: Execution) {
    return waitFor($http, execution, (current) => {
        return statePredicate(execution, current)
    })
}
