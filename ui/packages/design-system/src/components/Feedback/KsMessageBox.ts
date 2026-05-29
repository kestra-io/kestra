import {ElMessageBox} from "element-plus"
import type {ElMessageBoxOptions} from "element-plus"

// KsMessageBox is the Kestra design-system abstraction over ElMessageBox from Element Plus.
// It mirrors the ElMessageBox API exactly so existing call sites can do a drop-in import replacement.

export const KsMessageBox: {
    (options: ElMessageBoxOptions): ReturnType<typeof ElMessageBox.confirm>;
    alert: typeof ElMessageBox.alert;
    confirm: typeof ElMessageBox.confirm;
    prompt: typeof ElMessageBox.prompt;
    close: () => void;
} = Object.assign(
    (options: ElMessageBoxOptions): ReturnType<typeof ElMessageBox.confirm> => ElMessageBox(options),
    {
        alert: (...args: Parameters<typeof ElMessageBox.alert>): ReturnType<typeof ElMessageBox.alert> => ElMessageBox.alert(...args),
        confirm: (...args: Parameters<typeof ElMessageBox.confirm>): ReturnType<typeof ElMessageBox.confirm> => ElMessageBox.confirm(...args),
        prompt: (...args: Parameters<typeof ElMessageBox.prompt>): ReturnType<typeof ElMessageBox.prompt> => ElMessageBox.prompt(...args),
        close: (): void => ElMessageBox.close(),
    },
)

export default KsMessageBox
