import {ElNotification} from "element-plus"
import type {NotificationHandle, NotificationOptions, NotificationParams} from "element-plus"
import type {Component} from "vue"
import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline.vue"
import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
import AlertCircleOutline from "vue-material-design-icons/AlertCircleOutline.vue"
import AlertOutline from "vue-material-design-icons/AlertOutline.vue"

// KsNotification is the Kestra design-system abstraction over ElNotification from Element Plus.
// It mirrors the ElNotification API exactly so existing call sites can do a drop-in import replacement.

const TYPE_ICONS: Partial<Record<NonNullable<NotificationOptions["type"]>, Component>> = {
    success: CheckCircleOutline,
    info: InformationOutline,
    warning: AlertCircleOutline,
    error: AlertOutline,
}

// ElNotification resolves iconComponent as TypeComponentsMap[type] || props.icon,
// so props.icon is only used when type is absent. We strip type from the options,
// inject our icon, and restore the type CSS class via customClass.
function injectIcon(options: NotificationOptions | string, type: NonNullable<NotificationOptions["type"]>): NotificationOptions {
    const {type: _stripped, ...rest} = typeof options === "string" ? {message: options} : options

    const opts = rest as NotificationOptions
    if (!opts.icon) {
        opts.icon = TYPE_ICONS[type]
    }
    opts.customClass = [opts.customClass, `kel-notification--${type}`].filter(Boolean).join(" ")

    return opts
}

type KsNotificationType = {
    (options: NotificationParams): NotificationHandle
    success(options: NotificationOptions | string): NotificationHandle
    warning(options: NotificationOptions | string): NotificationHandle
    info(options: NotificationOptions | string): NotificationHandle
    error(options: NotificationOptions | string): NotificationHandle
    closeAll(): void
}

export const KsNotification: KsNotificationType = Object.assign(
    (options: NotificationParams): NotificationHandle => {
        const opts = options as NotificationOptions
        if (typeof options !== "string" && opts.type) {
            return ElNotification(injectIcon(options as NotificationOptions, opts.type))
        }

        return ElNotification(options)
    },
    {
        success: (options: NotificationOptions | string): NotificationHandle =>
            ElNotification(injectIcon(options, "success")),
        warning: (options: NotificationOptions | string): NotificationHandle =>
            ElNotification(injectIcon(options, "warning")),
        info: (options: NotificationOptions | string): NotificationHandle =>
            ElNotification(injectIcon(options, "info")),
        error: (options: NotificationOptions | string): NotificationHandle =>
            ElNotification(injectIcon(options, "error")),
        closeAll: (): void => ElNotification.closeAll(),
    },
)

export default KsNotification
