export type DisplayContext = Record<string, unknown>

type DisplayFlow = {
    id?: string
    namespace?: string
    revision?: number
    tenantId?: string
    variables?: Record<string, unknown>
}

type DisplayTaskRun = {
    id?: string
    taskId?: string
    parentTaskRunId?: string
    value?: string
    outputs?: Record<string, unknown>
}

type DisplayExecution = {
    id?: string
    originalId?: string
    namespace?: string
    flowId?: string
    flowRevision?: number
    tenantId?: string
    state?: {current?: string, startDate?: string | null, endDate?: string | null}
    inputs?: Record<string, unknown>
    variables?: Record<string, unknown>
    labels?: Array<{key: string, value: string}>
    trigger?: {variables?: Record<string, unknown>}
    taskRunList?: DisplayTaskRun[]
}

// A value carries embedded expressions rather than a whole one ("prefix-{{ vars.x }}/{{ vars.y }}"),
// so each print block is matched and substituted on its own.
const PRINT_BLOCK = /\{\{([\s\S]*?)\}\}/g
const SECRET_CALL = /^secret\(\s*(['"])(.+?)\1\s*\)$/
const DOTTED_PATH = /^[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*$/

const valueAt = (context: DisplayContext, path: string): unknown =>
    path.split(".").reduce<unknown>((current, segment) => {
        if (current === null || typeof current !== "object") return undefined
        return Object.prototype.hasOwnProperty.call(current, segment)
            ? (current as Record<string, unknown>)[segment]
            : undefined
    }, context)

const asDisplayString = (value: unknown): string | undefined => {
    if (typeof value === "string") return value
    if (typeof value === "number" || typeof value === "boolean") return String(value)
    if (value === null || value === undefined) return undefined
    return JSON.stringify(value)
}

/**
 * Substitutes what `context` resolves in a Pebble template, for display only — anything but a plain
 * dotted path or a `secret()` call is left raw, since a wrong value is worse than a visible `{{ }}`.
 */
export const resolveForDisplay = (value: string, context: DisplayContext): string => {
    if (typeof value !== "string" || !value.includes("{{")) return value
    // A statement block can drive the print blocks around it, so nothing in the value is resolvable.
    if (value.includes("{%")) return value

    return value.replace(PRINT_BLOCK, (raw, inner: string) => {
        const expression = inner.trim()

        const secret = SECRET_CALL.exec(expression)
        if (secret) return `[secret: ${secret[2]}]`

        if (!DOTTED_PATH.test(expression)) return raw
        return asDisplayString(valueAt(context, expression)) ?? raw
    })
}

// Outputs of the runs the executor keys by task id alone. An iteration's outputs nest under its
// value there, so resolving one of several runs would display another iteration's value.
const outputsOf = (execution: DisplayExecution): Record<string, unknown> => {
    const runs = execution.taskRunList ?? []
    const byId = new Map(runs.map((run) => [run.id, run]))

    const isIteration = (run: DisplayTaskRun | undefined): boolean => {
        while (run) {
            if (run.value !== undefined && run.value !== null) return true
            run = run.parentTaskRunId ? byId.get(run.parentTaskRunId) : undefined
        }
        return false
    }

    const outputs: Record<string, unknown> = {}
    runs.forEach((run) => {
        if (run.taskId && run.outputs && !isIteration(run)) outputs[run.taskId] = run.outputs
    })
    return outputs
}

/**
 * Builds the variable map `resolveForDisplay` reads. Without an execution it holds flow-level
 * entries only, so `inputs`/`outputs`/`execution`/`trigger` stay raw before a run.
 */
export const buildDisplayContext = (flow?: DisplayFlow, execution?: DisplayExecution): DisplayContext => {
    const context: DisplayContext = {
        flow: {
            id: flow?.id ?? execution?.flowId,
            namespace: flow?.namespace ?? execution?.namespace,
            revision: flow?.revision ?? execution?.flowRevision,
            tenantId: flow?.tenantId ?? execution?.tenantId,
        },
    }

    const variables = execution?.variables ?? flow?.variables
    if (variables) context.vars = variables

    if (!execution) return context

    context.execution = {
        id: execution.id,
        originalId: execution.originalId,
        state: execution.state?.current,
        startDate: execution.state?.startDate,
        endDate: execution.state?.endDate,
    }
    context.outputs = outputsOf(execution)
    if (execution.inputs) context.inputs = execution.inputs
    if (execution.trigger?.variables) context.trigger = execution.trigger.variables
    if (execution.labels) {
        context.labels = Object.fromEntries(execution.labels.map(({key, value}) => [key, value]))
    }

    return context
}
