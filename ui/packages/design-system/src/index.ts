import type {App, Component} from "vue"
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
import KsCodeStatus from "./components/Data/KsCodeStatus.vue"
import KsCol from "./components/Basic/KsRow/KsCol.vue"
import KsCollapse from "./components/Data/KsCollapse/KsCollapse.vue"
import KsCollapseItem from "./components/Data/KsCollapse/KsCollapseItem.vue"
import KsColorPicker from "./components/Form/KsColorPicker.vue"
import KsContainer from "./components/Basic/KsContainer/KsContainer.vue"
import KsHeader from "./components/Basic/KsContainer/KsHeader.vue"
import KsListingPage from "./components/Basic/KsContainer/KsListingPage.vue"
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
import KsEmptyState from "./components/Data/KsEmptyState.vue"
import KsExecutionStatus from "./components/Data/KsExecutionStatus/KsExecutionStatus.vue"
import KsFilter from "./components/Data/KsDataTable/KsFilter.vue"
import KsForm from "./components/Form/KsForm/KsForm.vue"
import KsFormItem from "./components/Form/KsForm/KsFormItem.vue"
import KsId from "./components/Data/KsId.vue"
import KsIcon from "./components/Basic/KsIcon.vue"
import KsIconButton from "./components/Basic/KsIconButton/KsIconButton.vue"
import KsInput from "./components/Form/KsInput.vue"
import KsSearch from "./components/Form/KsSearch.vue"
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
import KsSideBar from "./components/Navigation/KsSideBar/KsSideBar.vue"
import KsSideBarItem from "./components/Navigation/KsSideBar/KsSideBarItem.vue"
import KsSideBarSection from "./components/Navigation/KsSideBar/KsSideBarSection.vue"
import KsSkeleton from "./components/Data/KsSkeleton.vue"
import KsSplitter from "./components/Basic/KsSplitter/KsSplitter.vue"
import KsSplitterPanel from "./components/Basic/KsSplitter/KsSplitterPanel.vue"
import KsStep from "./components/Navigation/KsSteps/KsStep.vue"
import KsSteps from "./components/Navigation/KsSteps/KsSteps.vue"
import KsSwitch from "./components/Form/KsSwitch.vue"
import KsTabPane from "./components/Navigation/KsTabs/KsTabPane.vue"
import KsTabs from "./components/Navigation/KsTabs/KsTabs.vue"
import KsRouterTab from "./components/Navigation/KsTabs/KsRouterTab.vue"
import KsTabsToggle from "./components/Navigation/KsTabs/KsTabsToggle.vue"
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
import KsSubMenu from "./components/Navigation/KsMenu/KsSubMenu.vue"
import KsPageHeader from "./components/Data/KsPageHeader.vue"
import KsDescriptions from "./components/Data/KsDescriptions.vue"
import KsDescriptionsItem from "./components/Data/KsDescriptionsItem.vue"
import KsCarousel from "./components/Data/KsCarousel.vue"
import KsCarouselItem from "./components/Data/KsCarouselItem.vue"
import KsResult from "./components/Feedback/KsResult.vue"
import KsBacktop from "./components/Others/KsBacktop.vue"
import KsFilterChip from "./components/Data/KsDataTable/filter/layout/FilterChip.vue"

import {vKsLoading} from "./components/Feedback/KsLoading"
export {vKsLoading} from "./components/Feedback/KsLoading"

export {KsMessage} from "./components/Feedback/KsMessage"
export {KsMessageBox} from "./components/Feedback/KsMessageBox"
export {KsNotification} from "./components/Feedback/KsNotification"

export {cssVar} from "./utils/css"
export * as dateUtils from "./utils/date"
export * as stringUtils from "./utils/string"
export * as durationUtils from "./utils/duration"
export * as State from "./utils/state"
export {LOG_LEVELS, STATES} from "./utils/state"
export {SECTIONS, CLUSTER_PREFIX} from "./utils/constants"
export {setMomentInstance, setDateFormatter} from "./date/index"
export type {KsChartSeriesItem} from "./components/Charts/KsEchart.vue"
export type {KsGraphNode, KsGraphEdge} from "./components/Charts/KsGraph.vue"
export type {RouterTab} from "./components/Navigation/KsTabs/KsRouterTab.vue"
export type {KsBreadcrumbItem} from "./components/Navigation/KsBreadcrumb/types"
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

const components: Record<string, Component> = {
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
    KsCodeStatus,
    KsCol,
    KsCollapse,
    KsCollapseItem,
    KsColorPicker,
    KsContainer,
    KsHeader,
    KsListingPage,
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
    KsEmptyState,
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
    KsSearch,
    KsSegmented,
    KsSelect,
    KsSideBar,
    KsSideBarItem,
    KsSideBarSection,
    KsSkeleton,
    KsSplitter,
    KsSplitterPanel,
    KsStep,
    KsSteps,
    KsSwitch,
    KsTabPane,
    KsTabs,
    KsRouterTab,
    KsTabsToggle,
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
    KsSubMenu,
    KsPageHeader,
    KsDescriptions,
    KsDescriptionsItem,
    KsCarousel,
    KsCarouselItem,
    KsResult,
    KsBacktop,
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
    KsCodeStatus,
    KsCol,
    KsCollapse,
    KsCollapseItem,
    KsColorPicker,
    KsContainer,
    KsHeader,
    KsListingPage,
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
    KsEmptyState,
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
    KsSideBar,
    KsSideBarItem,
    KsSideBarSection,
    KsSkeleton,
    KsSplitter,
    KsSplitterPanel,
    KsStep,
    KsSteps,
    KsSwitch,
    KsTabPane,
    KsTabs,
    KsRouterTab,
    KsTabsToggle,
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
    KsSubMenu,
    KsPageHeader,
    KsDescriptions,
    KsDescriptionsItem,
    KsCarousel,
    KsCarouselItem,
    KsResult,
    KsBacktop,
    KsFilterChip,
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
        if (i18n) void registerDesignSystemI18n(i18n)
    },
}

export default KestraDesignSystem

declare module "vue" {
    interface GlobalComponents {
        KsAlert: typeof KsAlert
        KsEchart: typeof KsEchart
        KsGraph: typeof KsGraph
        KsLine: typeof KsLine
        KsBar: typeof KsBar
        KsPie: typeof KsPie
        KsAutocomplete: typeof KsAutocomplete
        KsAvatar: typeof KsAvatar
        KsBadge: typeof KsBadge
        KsBreadcrumb: typeof KsBreadcrumb
        KsButton: typeof KsButton
        KsButtonGroup: typeof KsButtonGroup
        KsCard: typeof KsCard
        KsDateAgo: typeof KsDateAgo
        KsDataTable: typeof KsDataTable
        KsCascaderPanel: typeof KsCascaderPanel
        KsCheckbox: typeof KsCheckbox
        KsCheckboxButton: typeof KsCheckboxButton
        KsCheckboxGroup: typeof KsCheckboxGroup
        KsCheckTag: typeof KsCheckTag
        KsCodeStatus: typeof KsCodeStatus
        KsCol: typeof KsCol
        KsCollapse: typeof KsCollapse
        KsCollapseItem: typeof KsCollapseItem
        KsColorPicker: typeof KsColorPicker
        KsContainer: typeof KsContainer
        KsHeader: typeof KsHeader
        KsListingPage: typeof KsListingPage
        KsMain: typeof KsMain
        KsDatePicker: typeof KsDatePicker
        KsDialog: typeof KsDialog
        KsDivider: typeof KsDivider
        KsDrawer: typeof KsDrawer
        KsDurationPicker: typeof KsDurationPicker
        KsDropdown: typeof KsDropdown
        KsDropdownItem: typeof KsDropdownItem
        KsDropdownMenu: typeof KsDropdownMenu
        KsEmpty: typeof KsEmpty
        KsEmptyState: typeof KsEmptyState
        KsExecutionStatus: typeof KsExecutionStatus
        KsFilter: typeof KsFilter
        KsForm: typeof KsForm
        KsFormItem: typeof KsFormItem
        KsId: typeof KsId
        KsIcon: typeof KsIcon
        KsIconButton: typeof KsIconButton
        KsInput: typeof KsInput
        KsInputNumber: typeof KsInputNumber
        KsPassword: typeof KsPassword
        KsLink: typeof KsLink
        KsMarkdown: typeof KsMarkdown
        KsMenu: typeof KsMenu
        KsMenuItem: typeof KsMenuItem
        KsOption: typeof KsOption
        KsOptionGroup: typeof KsOptionGroup
        KsPagination: typeof KsPagination
        KsPopover: typeof KsPopover
        KsProgress: typeof KsProgress
        KsRadio: typeof KsRadio
        KsRadioButton: typeof KsRadioButton
        KsRadioGroup: typeof KsRadioGroup
        KsRow: typeof KsRow
        KsScrollbar: typeof KsScrollbar
        KsSearch: typeof KsSearch
        KsSegmented: typeof KsSegmented
        KsSelect: typeof KsSelect
        KsSideBar: typeof KsSideBar
        KsSideBarItem: typeof KsSideBarItem
        KsSideBarSection: typeof KsSideBarSection
        KsSkeleton: typeof KsSkeleton
        KsSplitter: typeof KsSplitter
        KsSplitterPanel: typeof KsSplitterPanel
        KsStep: typeof KsStep
        KsSteps: typeof KsSteps
        KsSwitch: typeof KsSwitch
        KsTabPane: typeof KsTabPane
        KsTabs: typeof KsTabs
        KsRouterTab: typeof KsRouterTab
        KsTabsToggle: typeof KsTabsToggle
        KsTable: typeof KsTable
        KsTableColumn: typeof KsTableColumn
        KsTag: typeof KsTag
        KsTaskIcon: typeof KsTaskIcon
        KsText: typeof KsText
        KsTimeline: typeof KsTimeline
        KsTimelineItem: typeof KsTimelineItem
        KsTimePicker: typeof KsTimePicker
        KsTooltip: typeof KsTooltip
        KsTopNavBar: typeof KsTopNavBar
        KsTree: typeof KsTree
        KsUpload: typeof KsUpload
    }
}
