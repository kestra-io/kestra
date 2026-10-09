import {dayjs, type Dayjs} from "@kestra-io/design-system"
import type {Execution} from "@kestra-io/kestra-sdk"
import type {RouteLocationRaw} from "vue-router"

const RELATIVE_TIME_RANGES = ["PT24H", "PT48H", "PT168H", "PT720H", "PT8760H"]

export type LoopParentExecution = Pick<Execution, "id"> & {
    state?: Partial<Pick<Execution["state"], "startDate" | "endDate">>;
}

export function loopIterationsRoute(
    parent: LoopParentExecution,
    taskId: string,
    extraQuery: Record<string, string> = {},
    now?: Dayjs,
): RouteLocationRaw {
    return {
        name: "executions/list",
        query: {
            "filters[parentId][EQUALS]": parent.id,
            "filters[kind][EQUALS]": "LOOP",
            "filters[taskId][EQUALS]": taskId,
            ...extraQuery,
            ...parentTimeBound(parent.state, now),
        },
    }
}

function parentTimeBound(state: LoopParentExecution["state"], now: Dayjs | undefined): Record<string, string> {
    if (!state?.startDate) {
        return {}
    }

    const startDate = dayjs(state.startDate).toISOString()
    if (state.endDate) {
        return {
            "filters[startDate][GREATER_THAN_OR_EQUAL_TO]": startDate,
            "filters[endDate][LESS_THAN_OR_EQUAL_TO]": dayjs(state.endDate).toISOString(),
        }
    }

    const age = (now ?? dayjs()).diff(startDate)
    const timeRange = RELATIVE_TIME_RANGES.find(range => dayjs.duration(range).asMilliseconds() > age)
    return timeRange
        ? {"filters[timeRange][EQUALS]": timeRange}
        : {"filters[startDate][GREATER_THAN_OR_EQUAL_TO]": startDate}
}
