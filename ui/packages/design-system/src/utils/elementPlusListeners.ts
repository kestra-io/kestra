import {
    ElDatePicker as UntypedDatePicker,
    ElForm as UntypedForm,
    ElPopover as UntypedPopover,
    ElRadioButton as UntypedRadioButton,
    ElTimePicker as UntypedTimePicker,
    type DatePickerPropsPublic,
    type TimePickerDefaultPropsPublic,
} from "element-plus"

// Element Plus 2.14 publishes its components without emits, so the listeners the wrappers bind are typed here.
type WithListeners<C, L> = (new () => {$props: L}) & C

export const ElDatePicker = UntypedDatePicker as WithListeners<typeof UntypedDatePicker, {onChange?: (value: DatePickerPropsPublic["modelValue"]) => void}>
export const ElTimePicker = UntypedTimePicker as WithListeners<typeof UntypedTimePicker, {onChange?: (value: TimePickerDefaultPropsPublic["modelValue"]) => void}>
export const ElRadioButton = UntypedRadioButton as WithListeners<typeof UntypedRadioButton, {onChange?: (event: Event) => void}>
export const ElPopover = UntypedPopover as WithListeners<typeof UntypedPopover, {onHide?: () => void}>
export const ElForm = UntypedForm as WithListeners<typeof UntypedForm, {onSubmit?: (event: SubmitEvent) => void}>
