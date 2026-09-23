export interface OutputProperty {
    type?: string;
}

export function resolveDeclaredOutputProperties(
    candidates: Array<Record<string, OutputProperty> | undefined>,
): Record<string, OutputProperty> | undefined {
    return candidates.find((candidate): candidate is Record<string, OutputProperty> => Boolean(candidate) && typeof candidate === "object")
}

export function hasDeclaredOutputs(properties?: Record<string, OutputProperty>): boolean {
    return properties !== undefined && Object.keys(properties).length > 0
}
