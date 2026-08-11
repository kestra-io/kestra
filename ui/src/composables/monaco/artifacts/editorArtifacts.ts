export const ARTIFACT_COPY_COMMAND = "kestra.editorArtifact.copyToClipboard"

export interface ArtifactBlock {
    type: string;
    value: Record<string, any>;
    range: [number, number, number];
    path: string;
}

export interface EditorArtifactContext {
    namespace?: string;
    id?: string;
    t: (key: string, named?: Record<string, unknown>) => string;
}

export interface EditorArtifactLens {
    title: string;
    command: {id: string; arguments?: unknown[]};
}

export interface EditorArtifactProvider {
    type: string;
    provide(block: ArtifactBlock, context: EditorArtifactContext): EditorArtifactLens[];
}

export interface ResolvedArtifact {
    range: [number, number, number];
    lens: EditorArtifactLens;
}

const providers: EditorArtifactProvider[] = []

export function registerEditorArtifactProvider(provider: EditorArtifactProvider): void {
    if (!providers.some((existing) => existing.type === provider.type)) {
        providers.push(provider)
    }
}

export function provideEditorArtifacts(
    blocks: ArtifactBlock[],
    context: EditorArtifactContext,
): ResolvedArtifact[] {
    const resolved: ResolvedArtifact[] = []
    for (const block of blocks) {
        for (const provider of providers) {
            if (provider.type !== block.type) {
                continue
            }
            for (const lens of provider.provide(block, context)) {
                resolved.push({range: block.range, lens})
            }
        }
    }
    return resolved
}
