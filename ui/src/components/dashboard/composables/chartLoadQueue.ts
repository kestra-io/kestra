export const MAX_CONCURRENT_CHART_LOADS = 3

/**
 * Creates a FIFO queue that limits how many chart data loads run at the same time;
 * loads submitted above the limit wait and start as running ones settle.
 */
export function createChartLoadQueue(maxConcurrent: number) {
    let active = 0
    const pending: (() => void)[] = []

    function onSettled() {
        active--
        pending.shift()?.()
    }

    function enqueue<T>(task: () => Promise<T>): Promise<T> {
        return new Promise<T>((resolve, reject) => {
            const start = () => {
                active++
                Promise.resolve().then(task).then(resolve, reject).finally(onSettled)
            }

            if (active < maxConcurrent) start()
            else pending.push(start)
        })
    }

    return {enqueue}
}

/** Shared queue for every chart load in the app, so dashboards with many charts do not fire all their requests at once. */
export const chartLoadQueue = createChartLoadQueue(MAX_CONCURRENT_CHART_LOADS)
