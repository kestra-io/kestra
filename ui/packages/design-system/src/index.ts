import type {App} from "vue"
import ElementPlus, {INSTALLED_KEY} from "element-plus"
import type {I18n} from "vue-i18n"
import {registerDesignSystemI18n} from "./i18n"

import KsAlert from "./components/Feedback/KsAlert.vue"
import KsEchart from "./components/Charts/KsEchart.vue"
import KsGraph from "./components/Charts/KsGraph.vue"
import KsLine from "./components/Charts/KsLine.vue"
import KsBar from "./components/Charts/KsBar.vue"
import KsPie from "./components/Charts/KsPie.vue"
import KsAutocomplete from "./components/Form/KsAutocomplete.vue"
import KsAvatar from "./components/Data/KsAvatar.vue"
import KsBadge from "./components/Data/KsBadge.vue"
import KsBreadcrumb from "./components/Navigation/KsBreadcrumb/KsBreadcrumb.vue"
import KsBreadcrumbItem from "./components/Navigation/KsBreadcrumb/KsBreadcrumbItem.vue"
import KsButton from "./components/Basic/KsButton/KsButton.vue"
import KsButtonGroup from "./components/Basic/KsButton/KsButtonGroup.vue"
import KsCard from "./components/Data/KsCard.vue"
import KsDateAgo from "./components/Data/KsDateAgo.vue"
import KsDataTable from "./components/Data/KsDataTable/KsDataTable.vue"
import KsCascaderPanel from "./components/Form/KsCascaderPanel.vue"
import KsCheckbox from "./components/Form/KsCheckbox/KsCheckbox.vue"
import KsCheckboxButton from "./components/Form/KsCheckbox/KsCheckboxButton.vue"
import KsCheckboxGroup from "./components/Form/KsCheckbox/KsCheckboxGroup.vue"
import KsCheckTag from "./components/Data/KsTag/KsCheckTag.vue"
import KsCol from "./components/Basic/KsRow/KsCol.vue"
import KsCollapse from "./components/Data/KsCollapse/KsCollapse.vue"
import KsCollapseItem from "./components/Data/KsCollapse/KsCollapseItem.vue"
import KsColorPicker from "./components/Form/KsColorPicker.vue"
import KsContainer from "./components/Basic/KsContainer/KsContainer.vue"
import KsHeader from "./components/Basic/KsContainer/KsHeader.vue"
import KsMain from "./components/Basic/KsContainer/KsMain.vue"
import KsDatePicker from "./components/Form/KsDatePicker.vue"
import KsDialog from "./components/Feedback/KsDialog.vue"
import KsDivider from "./components/Others/KsDivider.vue"
import KsDrawer from "./components/Feedback/KsDrawer.vue"
import KsDurationPicker from "./components/Form/KsDurationPicker.vue"
import KsDropdown from "./components/Navigation/KsDropdown/KsDropdown.vue"
import KsDropdownItem from "./components/Navigation/KsDropdown/KsDropdownItem.vue"
import KsDropdownMenu from "./components/Navigation/KsDropdown/KsDropdownMenu.vue"
import KsEmpty from "./components/Data/KsEmpty.vue"
import KsExecutionStatus from "./components/Data/KsExecutionStatus/KsExecutionStatus.vue"
import KsFilter from "./components/Data/KsDataTable/KsFilter.vue"
import KsForm from "./components/Form/KsForm/KsForm.vue"
import KsFormItem from "./components/Form/KsForm/KsFormItem.vue"
import KsId from "./components/Data/KsId.vue"
import KsIcon from "./components/Basic/KsIcon.vue"
import KsIconButton from "./components/Basic/KsIconButton/KsIconButton.vue"
import KsInput from "./components/Form/KsInput.vue"
import KsPassword from "./components/Form/KsPassword.vue"
import KsInputNumber from "./components/Form/KsInputNumber.vue"
import KsLink from "./components/Basic/KsLink.vue"
import KsMarkdown from "./components/Data/KsMarkdown/KsMarkdown.vue"
import KsMenu from "./components/Navigation/KsMenu/KsMenu.vue"
import KsMenuItem from "./components/Navigation/KsMenu/KsMenuItem.vue"
import KsOption from "./components/Form/KsSelect/KsOption.vue"
import KsOptionGroup from "./components/Form/KsOptionGroup.vue"
import KsPagination from "./components/Data/KsPagination.vue"
import KsPopover from "./components/Feedback/KsPopover.vue"
import KsProgress from "./components/Data/KsProgress.vue"
import KsRadio from "./components/Form/KsRadio/KsRadio.vue"
import KsRadioButton from "./components/Form/KsRadio/KsRadioButton.vue"
import KsRadioGroup from "./components/Form/KsRadio/KsRadioGroup.vue"
import KsRow from "./components/Basic/KsRow/KsRow.vue"
import KsScrollbar from "./components/Basic/KsScrollbar.vue"
import KsSegmented from "./components/Data/KsSegmented.vue"
import KsSelect from "./components/Form/KsSelect/KsSelect.vue"
import KsSkeleton from "./components/Data/KsSkeleton.vue"
import KsSplitter from "./components/Basic/KsSplitter/KsSplitter.vue"
import KsSplitterPanel from "./components/Basic/KsSplitter/KsSplitterPanel.vue"
import KsStep from "./components/Navigation/KsSteps/KsStep.vue"
import KsSteps from "./components/Navigation/KsSteps/KsSteps.vue"
import KsSwitch from "./components/Form/KsSwitch.vue"
import KsTabPane from "./components/Navigation/KsTabs/KsTabPane.vue"
import KsTabs from "./components/Navigation/KsTabs/KsTabs.vue"
import KsRouterTab from "./components/Navigation/KsTabs/KsRouterTab.vue"
import KsTable from "./components/Data/KsTable/KsTable.vue"
import KsTableColumn from "./components/Data/KsTable/KsTableColumn.vue"
import KsTag from "./components/Data/KsTag/KsTag.vue"
import KsText from "./components/Basic/KsText.vue"
import KsTimeline from "./components/Data/KsTimeline/KsTimeline.vue"
import KsTimelineItem from "./components/Data/KsTimeline/KsTimelineItem.vue"
import KsTimePicker from "./components/Form/KsTimePicker.vue"
import KsTooltip from "./components/Feedback/KsTooltip.vue"
import KsTopNavBar from "./components/Navigation/KsTopNavBar/KsTopNavBar.vue"
import KsTaskIcon from "./components/Kestra/KsTaskIcon.vue"
import KsTree from "./components/Data/KsTree.vue"
import KsUpload from "./components/Form/KsUpload.vue"

import {vKsLoading} from "./components/Feedback/KsLoading"
export {vKsLoading} from "./components/Feedback/KsLoading"

export {KsMessage} from "./components/Feedback/KsMessage"
export {KsMessageBox} from "./components/Feedback/KsMessageBox"
export {KsNotification} from "./components/Feedback/KsNotification"

export {cssVar} from "./utils/css"
export * as dateUtils from "./utils/date"
export * as stringUtils from "./utils/string"
export * as durationUtils from "./utils/duration"
export * as flowYamlUtils from "./utils/flowYamlUtils"
export type {YamlElement} from "./utils/flowYamlUtils"
export * as State from "./utils/state"
export {LOG_LEVELS, STATES} from "./utils/state"
export {SECTIONS, CLUSTER_PREFIX} from "./utils/constants"
export {setMomentInstance, setDateFormatter} from "./date/index"
export type {KsChartSeriesItem} from "./components/Charts/KsEchart.vue"
export type {KsGraphNode, KsGraphEdge} from "./components/Charts/KsGraph.vue"
export type {RouterTab} from "./components/Navigation/KsTabs/KsRouterTab.vue"
export {Comparators} from "./components/Data/KsDataTable/filter/utils/filterTypes"
export type {InputInstance, FormItemRule, FormRules, FormInstance} from "element-plus"
export {TooltipType, ChartRenderer, ChartFeature} from "./components/Charts/ksChartUtils"
export {designSystemLocale, setDesignSystemLocale, registerDesignSystemI18n} from "./i18n"
export type {FilterContext} from "./components/Data/KsDataTable/filter/utils/filterInjectionKeys"
export {applyDefaultFilters} from "./components/Data/KsDataTable/filter/composables/useDefaultFilter"
export {useRouteFilterPolicy} from "./components/Data/KsDataTable/filter/composables/useRouteFilterPolicy"
export {EXECUTION_STATUSES, type ExecutionStatus, type ExecutionStatusModel} from "./components/Data/KsExecutionStatus/types"
export {
    decodeSearchParams,
    encodeFiltersToQuery,
    getUniqueFilters,
    isValidFilter,
    keyOfComparator,
    getComparator,
    clearFilterQueryParams,
    isSearchPath,
} from "./components/Data/KsDataTable/filter/utils/helpers"
export {
    readRouteLevelFilter,
    hasUnsupportedRouteLevelComparator,
    readAppliedLevelFilter,
    normalizeRouteLevelFilter,
} from "./components/Data/KsDataTable/filter/utils/logLevelQuery"
export type {
    FilterConfiguration,
    AppliedFilter,
    SavedFilter,
    TableOptions,
    TableProperties,
    FilterKeyConfig,
    FilterValue,
} from "./components/Data/KsDataTable/filter/utils/filterTypes"

const components = {
    KsAlert,
    KsEchart,
    KsGraph,
    KsLine,
    KsBar,
    KsPie,
    KsAutocomplete,
    KsAvatar,
    KsBadge,
    KsBreadcrumb,
    KsBreadcrumbItem,
    KsButton,
    KsButtonGroup,
    KsCard,
    KsDateAgo,
    KsDataTable,
    KsCascaderPanel,
    KsCheckbox,
    KsCheckboxButton,
    KsCheckboxGroup,
    KsCheckTag,
    KsCol,
    KsCollapse,
    KsCollapseItem,
    KsColorPicker,
    KsContainer,
    KsHeader,
    KsMain,
    KsDatePicker,
    KsDialog,
    KsDivider,
    KsDrawer,
    KsDurationPicker,
    KsDropdown,
    KsDropdownItem,
    KsDropdownMenu,
    KsEmpty,
    KsExecutionStatus,
    KsFilter,
    KsForm,
    KsFormItem,
    KsId,
    KsIcon,
    KsIconButton,
    KsInput,
    KsInputNumber,
    KsPassword,
    KsLink,
    KsMarkdown,
    KsMenu,
    KsMenuItem,
    KsOption,
    KsOptionGroup,
    KsPagination,
    KsPopover,
    KsProgress,
    KsRadio,
    KsRadioButton,
    KsRadioGroup,
    KsRow,
    KsScrollbar,
    KsSegmented,
    KsSelect,
    KsSkeleton,
    KsSplitter,
    KsSplitterPanel,
    KsStep,
    KsSteps,
    KsSwitch,
    KsTabPane,
    KsTabs,
    KsRouterTab,
    KsTable,
    KsTableColumn,
    KsTag,
    KsTaskIcon,
    KsText,
    KsTimeline,
    KsTimelineItem,
    KsTimePicker,
    KsTooltip,
    KsTopNavBar,
    KsTree,
    KsUpload,
}

export {
    KsAlert,
    KsEchart,
    KsGraph,
    KsLine,
    KsBar,
    KsPie,
    KsAutocomplete,
    KsAvatar,
    KsBadge,
    KsBreadcrumb,
    KsBreadcrumbItem,
    KsButton,
    KsButtonGroup,
    KsCard,
    KsDateAgo,
    KsDataTable,
    KsCascaderPanel,
    KsCheckbox,
    KsCheckboxButton,
    KsCheckboxGroup,
    KsCheckTag,
    KsCol,
    KsCollapse,
    KsCollapseItem,
    KsColorPicker,
    KsContainer,
    KsHeader,
    KsMain,
    KsDatePicker,
    KsDialog,
    KsDivider,
    KsDrawer,
    KsDurationPicker,
    KsDropdown,
    KsDropdownItem,
    KsDropdownMenu,
    KsEmpty,
    KsExecutionStatus,
    KsFilter,
    KsForm,
    KsFormItem,
    KsId,
    KsIcon,
    KsIconButton,
    KsInput,
    KsInputNumber,
    KsPassword,
    KsLink,
    KsMarkdown,
    KsMenu,
    KsMenuItem,
    KsOption,
    KsOptionGroup,
    KsPagination,
    KsPopover,
    KsProgress,
    KsRadio,
    KsRadioButton,
    KsRadioGroup,
    KsRow,
    KsScrollbar,
    KsSegmented,
    KsSelect,
    KsSkeleton,
    KsSplitter,
    KsSplitterPanel,
    KsStep,
    KsSteps,
    KsSwitch,
    KsTabPane,
    KsTabs,
    KsRouterTab,
    KsTable,
    KsTableColumn,
    KsTag,
    KsTaskIcon,
    KsText,
    KsTimeline,
    KsTimelineItem,
    KsTimePicker,
    KsTooltip,
    KsTopNavBar,
    KsTree,
    KsUpload,
}

const KestraDesignSystem = {
    install(app: App) {
        if (!(app as any)[INSTALLED_KEY]) {
            app.use(ElementPlus, {namespace: "kel"})
        }
        for (const [name, component] of Object.entries(components)) {
            app.component(name, component)
        }
        app.directive("ks-loading", vKsLoading)

        const symbol = (app as unknown as {__VUE_I18N_SYMBOL__?: symbol}).__VUE_I18N_SYMBOL__
        // oxlint-disable-next-line no-underscore-dangle
        const i18n = symbol ? (app._context.provides[symbol] as I18n | undefined) : undefined
        if (i18n) registerDesignSystemI18n(i18n)
    },
}

export default KestraDesignSystem

type KestraGlobalComponents = typeof components

declare module "vue" {
    interface GlobalComponents extends KestraGlobalComponents {}
}
