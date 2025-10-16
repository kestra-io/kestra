import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";

/**
 * Generates a unique ID for a task or trigger based on existing elements
 * @param type The type of element ("task" or "trigger")
 * @param source The flow source YAML
 * @param parentPath The parent path where to look for existing IDs
 * @returns A unique ID like "task1", "task2", "trigger1", etc.
 */
export function generateUniqueId(type: string, source: string, parentPath: string): string {
    const baseId = type === "triggers" ? "trigger" : "task";
    
    let elements: Record<string, any>[] = [];
    try {
        const flowObj = YAML_UTILS.parse(source);
        elements = flowObj[parentPath] || [];
    } catch (e) {
        console.error("Error parsing YAML for ID generation", e);
        return `${baseId}1`;
    }
    
    const existingIds = elements
        .map(element => element.id || "")
        .filter(Boolean);
    
    let highestNumber = 0;
    existingIds.forEach(id => {
        if (id.startsWith(baseId)) {
            const numberPart = id.substring(baseId.length);
            if (/^\d+$/.test(numberPart)) {
                const num = parseInt(numberPart);
                if (num > highestNumber) {
                    highestNumber = num;
                }
            }
        }
    });
    
    return `${baseId}${highestNumber + 1}`;
}
