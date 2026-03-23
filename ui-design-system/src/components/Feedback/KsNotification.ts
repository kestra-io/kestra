import {ElNotification} from "element-plus"
import type {NotificationHandle, NotificationOptions, NotificationParams} from "element-plus"

// KsNotification is the Kestra design-system abstraction over ElNotification from Element Plus.
// It mirrors the ElNotification API exactly so existing call sites can do a drop-in import replacement.

type KsNotificationType = {
    (options: NotificationParams): NotificationHandle
    success(options: NotificationParams | string): NotificationHandle
    warning(options: NotificationParams | string): NotificationHandle
    info(options: NotificationParams | string): NotificationHandle
    error(options: NotificationParams | string): NotificationHandle
    closeAll(): void
}

export const KsNotification: KsNotificationType = Object.assign(
    (options: NotificationParams): NotificationHandle => ElNotification(options),
    {
        success: (options: NotificationParams | string): NotificationHandle =>
            ElNotification.success(options as NotificationOptions),
        warning: (options: NotificationParams | string): NotificationHandle =>
            ElNotification.warning(options as NotificationOptions),
        info: (options: NotificationParams | string): NotificationHandle =>
            ElNotification.info(options as NotificationOptions),
        error: (options: NotificationParams | string): NotificationHandle =>
            ElNotification.error(options as NotificationOptions),
        closeAll: (): void => ElNotification.closeAll(),
    },
)

export default KsNotification
