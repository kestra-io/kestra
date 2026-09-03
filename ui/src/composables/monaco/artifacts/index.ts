import {registerEditorArtifactProvider} from "./editorArtifacts"
import {webhookArtifactProvider} from "./webhookArtifact"

registerEditorArtifactProvider(webhookArtifactProvider)

export * from "./editorArtifacts"
