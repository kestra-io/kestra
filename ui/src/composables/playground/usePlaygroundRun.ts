import {usePlaygroundStore} from "../../stores/playground"

export function usePlaygroundRun() {
    const playgroundStore = usePlaygroundStore()

    function runTask(taskId?: string, downstream = false) {
        if (!taskId) return
        playgroundStore.runUntilTask(taskId, downstream)
    }

    return {runTask, playgroundStore}
}
