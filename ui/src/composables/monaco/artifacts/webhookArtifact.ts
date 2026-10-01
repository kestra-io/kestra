import {webhookUrl, WEBHOOK_TRIGGER_TYPE} from "../../../utils/webhook"
import {ARTIFACT_COPY_COMMAND, type EditorArtifactProvider} from "./editorArtifacts"

export const webhookArtifactProvider: EditorArtifactProvider = {
    type: WEBHOOK_TRIGGER_TYPE,
    provide(block, context) {
        const rawKey = block.value?.key
        const key = typeof rawKey === "string" || typeof rawKey === "number" ? String(rawKey) : undefined
        if (block.path !== "triggers" || !key || !context.namespace || !context.id) {
            return []
        }

        const url = webhookUrl({namespace: context.namespace, id: context.id, key})

        return [
            {
                title: context.t("webhook.copy_url"),
                command: {
                    id: ARTIFACT_COPY_COMMAND,
                    arguments: [{text: url, message: context.t("webhook link copied")}],
                },
            },
        ]
    },
}
