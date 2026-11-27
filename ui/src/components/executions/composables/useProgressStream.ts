import {onUnmounted, watch} from "vue"
import {State, levelToRequestParams} from "@kestra-io/design-system"
import {useExecutionsStore, type Execution} from "../../../stores/executions"

type Stream = {users: number; closed: boolean; sse?: EventSource}

const streams = new Map<string, Stream>()

function acquire(id: string, store: ReturnType<typeof useExecutionsStore>) {
    const shared = streams.get(id)
    if (shared) {
        shared.users++
        return
    }

    const stream: Stream = {users: 1, closed: false}
    streams.set(id, stream)
    store.followLogs({id, params: levelToRequestParams({value: "INFO", direction: "min"})}).then((sse: EventSource) => {
        if (stream.closed) {
            sse.close()
            return
        }
        stream.sse = sse
        sse.onmessage = (event: MessageEvent) => {
            if (event.lastEventId === "start") return
            const data = JSON.parse(event.data)
            if (!data.progress) return
            store.addProgressEvent({taskId: data.taskId, taskRunId: data.taskRunId, step: data.progress, timestamp: data.timestamp})
        }
        // No onerror: closing on a transient drop froze the stepper; EventSource reconnects and the replay is deduped by addProgressEvent.
    })
}

function release(id: string) {
    const stream = streams.get(id)
    if (!stream || --stream.users > 0) return
    stream.closed = true
    stream.sse?.close()
    streams.delete(id)
}

/** Holds the one shared follow-logs stream of the execution while it runs and the caller is mounted. */
export function useProgressStream(execution: () => Pick<Execution, "id" | "state"> | undefined) {
    const store = useExecutionsStore()
    let held: string | undefined

    const stop = () => {
        if (held) release(held)
        held = undefined
    }

    watch(
        () => {
            const current = execution()
            const state = current?.state?.current
            return current?.id && state && State.isRunning(state) ? current.id : undefined
        },
        (id) => {
            stop()
            if (!id) return
            acquire(id, store)
            held = id
        },
        {immediate: true},
    )

    onUnmounted(stop)
}
