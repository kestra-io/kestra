import type {Ref} from "vue"
import {useI18n} from "vue-i18n"
import {asProblem} from "@kestra-io/kestra-sdk"
import {useToast} from "../utils/toast"
import {isReportedCentrally, type KestraHttpError} from "../utils/kestraHttp"
import {problemBulkBody, problemTitle} from "../utils/problem"

/** Runs a list page's bulk action on every match or on the ticked rows, then reports, unselects and refreshes; a refusal is listed per item. Pass `report` instead of `success` when the outcome is not always a success. */
export function useBulkAction(
    table: Readonly<Ref<{queryBulkAction?: boolean; toggleAllUnselected: () => void; reload: () => void} | null | undefined>>,
    options: {refresh?: () => void} = {},
) {
    const toast = useToast()
    const {t, te} = useI18n()
    const refresh = options.refresh ?? (() => table.value?.reload())

    function runBulkAction<Q, I>(action: {
        byQuery: () => Promise<Q>;
        byIds: () => Promise<I>;
    } & ({success: (response: Q | I) => string} | {report: (response: Q | I) => void})): Promise<void> {
        const call: () => Promise<Q | I> = table.value?.queryBulkAction ? action.byQuery : action.byIds
        return call()
            .then((response) => {
                if ("report" in action) {
                    action.report(response)
                } else {
                    toast.success(action.success(response))
                }
                table.value?.toggleAllUnselected()
                refresh()
            })
            .catch((error: unknown) => {
                if (isReportedCentrally(error as KestraHttpError)) {
                    return
                }
                const problem = asProblem(error)
                toast.error(problemBulkBody(problem, t, te), problemTitle(problem, t, te))
            })
    }

    return {runBulkAction}
}
