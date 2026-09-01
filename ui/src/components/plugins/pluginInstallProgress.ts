import type {ArtifactProgress, PluginArtifact} from "../../stores/plugins"

function escapeRegExp(value: string): string {
    return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
}

/**
 * Finds the artifact's own progress entry by matching its file segment
 * ("…/plugin-aws-1.2.3.jar") — a plain includes() would also match "plugin-aws"
 * against a "plugin-aws-s3" resource.
 */
export function progressFor(progress: Record<string, ArtifactProgress>, artifact: PluginArtifact): ArtifactProgress | undefined {
    const pattern = new RegExp(`(^|/)${escapeRegExp(artifact.artifactId)}-\\d`)
    const key = Object.keys(progress).find(k => pattern.test(k))
    return key ? progress[key] : undefined
}

export function artifactPercentage(progress: Record<string, ArtifactProgress>, artifact: PluginArtifact): number {
    const p = progressFor(progress, artifact)
    if (!p || p.total <= 0) return 0
    return Math.round((p.transferred / p.total) * 100)
}

export function humanBytes(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
