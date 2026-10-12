import type {useClient} from "@kestra-io/kestra-sdk"
import {apiUrl} from "override/utils/route"
import type {Execution} from "../stores/executions"

type KestraClient = ReturnType<typeof useClient>

const POLL_INTERVAL_MS = 300
const MAX_POLLS = 100

/**
 * Resolves with the execution once `predicate` accepts it, or with the last state seen after
 * {@link MAX_POLLS} polls so a caller never waits forever, and rejects when a poll request fails.
 */
export function waitFor($http: KestraClient, execution: {id: string}, predicate: (data: any) => boolean) {
    return new Promise((resolve, reject) => {
        let remaining = MAX_POLLS
        const poll = () => {
            $http.get(`${apiUrl()}/executions/${execution.id}`).then((response) => {
                remaining--
                if (predicate(response.data) === true || remaining <= 0) {
                    resolve(response.data)
                } else {
                    window.setTimeout(poll, POLL_INTERVAL_MS)
                }
            }, reject)
        }

        window.setTimeout(poll, POLL_INTERVAL_MS)
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
