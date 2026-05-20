import {computed, ComputedRef} from "vue"
import {FilterConfiguration} from "@kestra-io/design-system"
import {useI18n} from "vue-i18n"

/**
 * Filter configuration for the MCP server "Tool Flows" table. The table itself isn't
 * server-paginated yet, so there's no need for filterable keys — this exists only to
 * give the shared `<KsFilter>` chrome (refresh button + column visibility menu) a valid
 * configuration object to render against.
 */
export const useMcpToolsFilter = (): ComputedRef<FilterConfiguration> => {
    const {t} = useI18n()

    return computed(() => ({
        title: t("filter.titles.mcp_tools_filters"),
        keys: [],
    }))
}
