import {ElMessageBox} from "element-plus"
import type {ElMessageBoxOptions} from "element-plus"

// KsMessageBox is the Kestra design-system abstraction over ElMessageBox from Element Plus.
// It mirrors the ElMessageBox API exactly so existing call sites can do a drop-in import replacement.

export const KsMessageBox = Object.assign(
    (options: ElMessageBoxOptions) => ElMessageBox(options),
    {
        alert: (...args: Parameters<typeof ElMessageBox.alert>) => ElMessageBox.alert(...args),
        confirm: (...args: Parameters<typeof ElMessageBox.confirm>) => ElMessageBox.confirm(...args),
        prompt: (...args: Parameters<typeof ElMessageBox.prompt>) => ElMessageBox.prompt(...args),
        close: () => ElMessageBox.close(),
    },
)

export default KsMessageBox
