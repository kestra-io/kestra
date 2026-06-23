export interface GanttTaskRun {
    id: string;
    parentTaskRunId?: string;
    state: {
        histories: Array<{
            date: string;
        }>;
    };
}

export interface GanttTaskWrapper<T extends GanttTaskRun> {
    task: T;
    depth: number;
    children?: Array<GanttTaskWrapper<T>>;
}

const taskStartDate = <T extends GanttTaskRun>(node: GanttTaskWrapper<T>): number =>
    new Date(node.task.state.histories[0].date).getTime()

const sortByStartDate = <T extends GanttTaskRun>(
    nodes: Array<GanttTaskWrapper<T>>,
    sortedTasks: Array<GanttTaskWrapper<T>>,
): void => {
    nodes.sort((n1, n2) => taskStartDate(n1) > taskStartDate(n2) ? 1 : -1)
    for (const node of nodes) {
        sortedTasks.push(node)
        if (node.children) {
            sortByStartDate(node.children, sortedTasks)
        }
    }
}

const applyDepth = <T extends GanttTaskRun>(nodes: Array<GanttTaskWrapper<T>>, depth: number): void => {
    for (const node of nodes) {
        node.depth = depth
        if (node.children) {
            applyDepth(node.children, depth + 1)
        }
    }
}

export const buildGanttTasks = <T extends GanttTaskRun>(taskRuns: T[]): Array<GanttTaskWrapper<T>> => {
    const taskWrappersById: Record<string, GanttTaskWrapper<T>> = {}

    for (const task of taskRuns) {
        taskWrappersById[task.id] = {task, depth: 0}
    }

    const rootTasks: Array<GanttTaskWrapper<T>> = []
    for (const taskWrapper of Object.values(taskWrappersById)) {
        const parentTask = taskWrapper.task.parentTaskRunId
            ? taskWrappersById[taskWrapper.task.parentTaskRunId]
            : undefined

        if (!parentTask) {
            rootTasks.push(taskWrapper)
            continue
        }

        parentTask.children ??= []
        parentTask.children.push(taskWrapper)
    }

    applyDepth(rootTasks, 0)

    const sortedTasks: Array<GanttTaskWrapper<T>> = []
    sortByStartDate(rootTasks, sortedTasks)
    return sortedTasks
}
