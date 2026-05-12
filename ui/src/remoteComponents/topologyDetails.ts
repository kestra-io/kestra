import {Execution, MetricEntry, Task} from "@kestra-io/kestra-sdk"

export const propNames = ["taskType", "task", "execution", "namespace", "flowId", "metrics"] as const

export interface Props extends Partial<Record<typeof propNames[number], any>> {
    taskType: string;
    task: Task;
    execution?: Execution;
    namespace?: string;
    flowId?: string;
    metrics: MetricEntry[];
}
