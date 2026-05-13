import {ElNotification} from "element-plus"
import type {NotificationHandle, NotificationOptions, NotificationParams} from "element-plus"
import {TYPE_ICONS, type FeedbackType} from "./typeIcons"

// KsNotification is the Kestra design-system abstraction over ElNotification from Element Plus.
// It mirrors the ElNotification API exactly so existing call sites can do a drop-in import replacement.

function notify(options: NotificationParams | string, forcedType?: FeedbackType): NotificationHandle {
    const opts = typeof options === "string" ? {message: options} : (options as NotificationOptions)
    const type = forcedType ?? (opts.type as FeedbackType | undefined)
    if (!type || !TYPE_ICONS[type]) return ElNotification(opts)
    const {type: _ignored, customClass, ...rest} = opts
    return ElNotification({
        ...rest,
        icon: TYPE_ICONS[type],
        customClass: [customClass, `kel-notification--${type}`].filter(Boolean).join(" "),
    })
}

export const KsNotification = Object.assign(
    (options: NotificationParams) => notify(options),
    {
        success: (options: NotificationParams | string) => notify(options, "success"),
        warning: (options: NotificationParams | string) => notify(options, "warning"),
        info: (options: NotificationParams | string) => notify(options, "info"),
        error: (options: NotificationParams | string) => notify(options, "error"),
        closeAll: (): void => ElNotification.closeAll(),
    },
)

export default KsNotification
