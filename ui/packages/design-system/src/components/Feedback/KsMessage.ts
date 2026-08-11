import {ElMessage} from "element-plus"
import type {MessageHandler, MessageOptions, MessageParams, MessageParamsWithType} from "element-plus"
import type {Component} from "vue"
import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline.vue"
import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
import AlertCircleOutline from "vue-material-design-icons/AlertCircleOutline.vue"
import AlertOutline from "vue-material-design-icons/AlertOutline.vue"

// KsMessage is the Kestra design-system abstraction over ElMessage from Element Plus.
// It mirrors the ElMessage API exactly so existing call sites can do a drop-in import replacement.

const TYPE_ICONS: Partial<Record<NonNullable<MessageOptions["type"]>, Component>> = {
    success: CheckCircleOutline,
    info: InformationOutline,
    warning: AlertCircleOutline,
    error: AlertOutline,
}

function injectIcon(options: MessageParamsWithType | string, type: NonNullable<MessageOptions["type"]>): MessageParamsWithType {
    const opts = (typeof options === "string" ? {message: options} : {...options}) as MessageOptions

    opts.plain = true

    if (!opts.icon) {
        opts.icon = TYPE_ICONS[type]
    }

    return opts as MessageParamsWithType
}

type KsMessageType = {
    (options: MessageParams): MessageHandler
    success(options: MessageParamsWithType | string): MessageHandler
    warning(options: MessageParamsWithType | string): MessageHandler
    info(options: MessageParamsWithType | string): MessageHandler
    error(options: MessageParamsWithType | string): MessageHandler
    closeAll(type?: MessageOptions["type"]): void
}

export const KsMessage: KsMessageType = Object.assign(
    (options: MessageParams): MessageHandler => {
        const opts = options as MessageOptions
        if (typeof options !== "string" && opts.type) {
            return ElMessage(injectIcon(options as MessageParamsWithType, opts.type))
        }

        return ElMessage(injectIcon(options as MessageParamsWithType, "info"))
    },
    {
        success: (options: MessageParamsWithType | string): MessageHandler =>
            ElMessage.success(injectIcon(options, "success")),
        warning: (options: MessageParamsWithType | string): MessageHandler =>
            ElMessage.warning(injectIcon(options, "warning")),
        info: (options: MessageParamsWithType | string): MessageHandler =>
            ElMessage.info(injectIcon(options, "info")),
        error: (options: MessageParamsWithType | string): MessageHandler =>
            ElMessage.error(injectIcon(options, "error")),
        closeAll: (type?: MessageOptions["type"]): void => ElMessage.closeAll(type),
    },
)

export default KsMessage
