import {computed} from "vue"
import {useI18n} from "vue-i18n"
import {dayjs} from "@kestra-io/design-system"
import type {Execution} from "../stores/executions"
import * as Utils from "../utils/utils"
import {useToast} from "../utils/toast"

export function useExecutionSummary(execution: () => Execution) {
    const {t} = useI18n({useScope: "global"})
    const toast = useToast()

    const statusLabel = computed(() => {
        const status = execution().state.current
        return status.charAt(0).toUpperCase() + status.slice(1).toLowerCase()
    })

    const matchesStatus = (type: "replay" | "replayed" | "restarted") =>
        execution().labels?.some((label) => label.key === `system.${type}` && label.value === "true") ?? false

    const replayed = computed(() => matchesStatus("replay") || matchesStatus("replayed"))
    const restarted = computed(() => matchesStatus("restarted"))

    const createdDate = computed(() => dayjs(execution().state.histories?.[0]?.date).toDate())

    const replaysLink = computed(() => ({
        name: "executions/list",
        query: {
            "filters[parentId][EQUALS]": matchesStatus("replay") ? execution().parentId ?? execution().id : execution().id,
            sort: "state.endDate:desc",
        },
    }))

    const copyId = () => {
        Utils.copy(execution().id)
        toast.success(t("copied"))
    }

    return {statusLabel, replayed, restarted, createdDate, replaysLink, copyId}
}
