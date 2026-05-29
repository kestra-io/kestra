import {ElMessageBox} from "element-plus"
import type {ElMessageBoxOptions, ElMessageBoxShortcutMethod} from "element-plus"

// KsMessageBox is the Kestra design-system abstraction over ElMessageBox from Element Plus.
// It mirrors the ElMessageBox API exactly so existing call sites can do a drop-in import replacement.

export const KsMessageBox = Object.assign(
    (options: ElMessageBoxOptions): ReturnType<typeof ElMessageBox.confirm> => ElMessageBox(options),
    {
        alert: (...args: Parameters<ElMessageBoxShortcutMethod>) => ElMessageBox.alert(...args),
        confirm: (...args: Parameters<ElMessageBoxShortcutMethod>) => ElMessageBox.confirm(...args),
        prompt: (...args: Parameters<ElMessageBoxShortcutMethod>) => ElMessageBox.prompt(...args),
        close: () => ElMessageBox.close(),
    },
)

export default KsMessageBox
