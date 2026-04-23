import {ElMessage} from "element-plus"
import type {MessageHandler, MessageOptions, MessageParams, MessageParamsWithType} from "element-plus"

// KsMessage is the Kestra design-system abstraction over ElMessage from Element Plus.
// It mirrors the ElMessage API exactly so existing call sites can do a drop-in import replacement.

type KsMessageType = {
    (options: MessageParams): MessageHandler
    success(options: MessageParamsWithType | string): MessageHandler
    warning(options: MessageParamsWithType | string): MessageHandler
    info(options: MessageParamsWithType | string): MessageHandler
    error(options: MessageParamsWithType | string): MessageHandler
    closeAll(type?: MessageOptions["type"]): void
}

export const KsMessage: KsMessageType = Object.assign(
    (options: MessageParams): MessageHandler => ElMessage(options),
    {
        success: (options: MessageParamsWithType | string): MessageHandler =>
            ElMessage.success(options as MessageParamsWithType),
        warning: (options: MessageParamsWithType | string): MessageHandler =>
            ElMessage.warning(options as MessageParamsWithType),
        info: (options: MessageParamsWithType | string): MessageHandler =>
            ElMessage.info(options as MessageParamsWithType),
        error: (options: MessageParamsWithType | string): MessageHandler =>
            ElMessage.error(options as MessageParamsWithType),
        closeAll: (type?: MessageOptions["type"]): void => ElMessage.closeAll(type),
    },
)

export default KsMessage
