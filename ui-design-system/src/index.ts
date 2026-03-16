import type {App} from "vue"
import ElementPlus, {INSTALLED_KEY} from "element-plus"

import KsAlert from "./components/Feedback/KsAlert.vue"
import KsConfigProvider from "./components/Configuration/KsConfigProvider.vue"
import KsAutocomplete from "./components/Form/KsAutocomplete.vue"
import KsAvatar from "./components/Data/KsAvatar.vue"
import KsBadge from "./components/Data/KsBadge.vue"
import KsBreadcrumb from "./components/Navigation/KsBreadcrumb/KsBreadcrumb.vue"
import KsBreadcrumbItem from "./components/Navigation/KsBreadcrumb/KsBreadcrumbItem.vue"
import KsButton from "./components/Basic/KsButton/KsButton.vue"
import KsButtonGroup from "./components/Basic/KsButton/KsButtonGroup.vue"
import KsCard from "./components/Data/KsCard.vue"
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
import KsDropdown from "./components/Navigation/KsDropdown/KsDropdown.vue"
import KsDropdownItem from "./components/Navigation/KsDropdown/KsDropdownItem.vue"
import KsDropdownMenu from "./components/Navigation/KsDropdown/KsDropdownMenu.vue"
import KsEmpty from "./components/Data/KsEmpty.vue"
import KsForm from "./components/Form/KsForm/KsForm.vue"
import KsFormItem from "./components/Form/KsForm/KsFormItem.vue"
import KsIcon from "./components/Basic/KsIcon.vue"
import KsInput from "./components/Form/KsInput.vue"
import KsInputNumber from "./components/Form/KsInputNumber.vue"
import KsLink from "./components/Basic/KsLink.vue"
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
import KsTable from "./components/Data/KsTable/KsTable.vue"
import KsTableColumn from "./components/Data/KsTable/KsTableColumn.vue"
import KsTag from "./components/Data/KsTag/KsTag.vue"
import KsText from "./components/Basic/KsText.vue"
import KsTimeline from "./components/Data/KsTimeline/KsTimeline.vue"
import KsTimelineItem from "./components/Data/KsTimeline/KsTimelineItem.vue"
import KsTimePicker from "./components/Form/KsTimePicker.vue"
import KsTooltip from "./components/Feedback/KsTooltip.vue"
import KsTree from "./components/Data/KsTree.vue"
import KsUpload from "./components/Form/KsUpload.vue"

// ─── Named exports (tree-shakeable) ──────────────────────────────────────────
export {
    KsAlert,
    KsAutocomplete,
    KsConfigProvider,
    KsAvatar,
    KsBadge,
    KsBreadcrumb,
    KsBreadcrumbItem,
    KsButton,
    KsButtonGroup,
    KsCard,
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
    KsDropdown,
    KsDropdownItem,
    KsDropdownMenu,
    KsEmpty,
    KsForm,
    KsFormItem,
    KsIcon,
    KsInput,
    KsInputNumber,
    KsLink,
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
    KsTable,
    KsTableColumn,
    KsTag,
    KsText,
    KsTimeline,
    KsTimelineItem,
    KsTimePicker,
    KsTooltip,
    KsTree,
    KsUpload,
}

// ─── Vue plugin (auto-registers all components) ──────────────────────────────
const KestraDesignSystem = {
    install(app: App) {
        if (!(app as any)[INSTALLED_KEY]) {
            app.use(ElementPlus)
        }

        app.component("KsAlert", KsAlert)
        app.component("KsAutocomplete", KsAutocomplete)
        app.component("KsConfigProvider", KsConfigProvider)
        app.component("KsAvatar", KsAvatar)
        app.component("KsBadge", KsBadge)
        app.component("KsBreadcrumb", KsBreadcrumb)
        app.component("KsBreadcrumbItem", KsBreadcrumbItem)
        app.component("KsButton", KsButton)
        app.component("KsButtonGroup", KsButtonGroup)
        app.component("KsCard", KsCard)
        app.component("KsCascaderPanel", KsCascaderPanel)
        app.component("KsCheckbox", KsCheckbox)
        app.component("KsCheckboxButton", KsCheckboxButton)
        app.component("KsCheckboxGroup", KsCheckboxGroup)
        app.component("KsCheckTag", KsCheckTag)
        app.component("KsCol", KsCol)
        app.component("KsCollapse", KsCollapse)
        app.component("KsCollapseItem", KsCollapseItem)
        app.component("KsColorPicker", KsColorPicker)
        app.component("KsContainer", KsContainer)
        app.component("KsHeader", KsHeader)
        app.component("KsMain", KsMain)
        app.component("KsDatePicker", KsDatePicker)
        app.component("KsDialog", KsDialog)
        app.component("KsDivider", KsDivider)
        app.component("KsDrawer", KsDrawer)
        app.component("KsDropdown", KsDropdown)
        app.component("KsDropdownItem", KsDropdownItem)
        app.component("KsDropdownMenu", KsDropdownMenu)
        app.component("KsEmpty", KsEmpty)
        app.component("KsForm", KsForm)
        app.component("KsFormItem", KsFormItem)
        app.component("KsIcon", KsIcon)
        app.component("KsInput", KsInput)
        app.component("KsInputNumber", KsInputNumber)
        app.component("KsLink", KsLink)
        app.component("KsMenu", KsMenu)
        app.component("KsMenuItem", KsMenuItem)
        app.component("KsOption", KsOption)
        app.component("KsOptionGroup", KsOptionGroup)
        app.component("KsPagination", KsPagination)
        app.component("KsPopover", KsPopover)
        app.component("KsProgress", KsProgress)
        app.component("KsRadio", KsRadio)
        app.component("KsRadioButton", KsRadioButton)
        app.component("KsRadioGroup", KsRadioGroup)
        app.component("KsRow", KsRow)
        app.component("KsScrollbar", KsScrollbar)
        app.component("KsSegmented", KsSegmented)
        app.component("KsSelect", KsSelect)
        app.component("KsSkeleton", KsSkeleton)
        app.component("KsSplitter", KsSplitter)
        app.component("KsSplitterPanel", KsSplitterPanel)
        app.component("KsStep", KsStep)
        app.component("KsSteps", KsSteps)
        app.component("KsSwitch", KsSwitch)
        app.component("KsTabPane", KsTabPane)
        app.component("KsTabs", KsTabs)
        app.component("KsTable", KsTable)
        app.component("KsTableColumn", KsTableColumn)
        app.component("KsTag", KsTag)
        app.component("KsText", KsText)
        app.component("KsTimeline", KsTimeline)
        app.component("KsTimelineItem", KsTimelineItem)
        app.component("KsTimePicker", KsTimePicker)
        app.component("KsTooltip", KsTooltip)
        app.component("KsTree", KsTree)
        app.component("KsUpload", KsUpload)
    },
}

export default KestraDesignSystem

// ─── Global component type augmentation (Volar / IntelliJ IDE support) ───────
declare module "vue" {
    export interface GlobalComponents {
        KsAlert: typeof KsAlert
        KsAutocomplete: typeof KsAutocomplete
        KsConfigProvider: typeof KsConfigProvider
        KsAvatar: typeof KsAvatar
        KsBadge: typeof KsBadge
        KsBreadcrumb: typeof KsBreadcrumb
        KsBreadcrumbItem: typeof KsBreadcrumbItem
        KsButton: typeof KsButton
        KsButtonGroup: typeof KsButtonGroup
        KsCard: typeof KsCard
        KsCascaderPanel: typeof KsCascaderPanel
        KsCheckbox: typeof KsCheckbox
        KsCheckboxButton: typeof KsCheckboxButton
        KsCheckboxGroup: typeof KsCheckboxGroup
        KsCheckTag: typeof KsCheckTag
        KsCol: typeof KsCol
        KsCollapse: typeof KsCollapse
        KsCollapseItem: typeof KsCollapseItem
        KsColorPicker: typeof KsColorPicker
        KsContainer: typeof KsContainer
        KsHeader: typeof KsHeader
        KsMain: typeof KsMain
        KsDatePicker: typeof KsDatePicker
        KsDialog: typeof KsDialog
        KsDivider: typeof KsDivider
        KsDrawer: typeof KsDrawer
        KsDropdown: typeof KsDropdown
        KsDropdownItem: typeof KsDropdownItem
        KsDropdownMenu: typeof KsDropdownMenu
        KsEmpty: typeof KsEmpty
        KsForm: typeof KsForm
        KsFormItem: typeof KsFormItem
        KsIcon: typeof KsIcon
        KsInput: typeof KsInput
        KsInputNumber: typeof KsInputNumber
        KsLink: typeof KsLink
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
        KsSegmented: typeof KsSegmented
        KsSelect: typeof KsSelect
        KsSkeleton: typeof KsSkeleton
        KsSplitter: typeof KsSplitter
        KsSplitterPanel: typeof KsSplitterPanel
        KsStep: typeof KsStep
        KsSteps: typeof KsSteps
        KsSwitch: typeof KsSwitch
        KsTabPane: typeof KsTabPane
        KsTabs: typeof KsTabs
        KsTable: typeof KsTable
        KsTableColumn: typeof KsTableColumn
        KsTag: typeof KsTag
        KsText: typeof KsText
        KsTimeline: typeof KsTimeline
        KsTimelineItem: typeof KsTimelineItem
        KsTimePicker: typeof KsTimePicker
        KsTooltip: typeof KsTooltip
        KsTree: typeof KsTree
        KsUpload: typeof KsUpload
    }
}
