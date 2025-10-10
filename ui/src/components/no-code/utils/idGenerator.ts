/* eslint-disable @typescript-eslint/no-unused-vars */
import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";

/**
 * Generates a unique ID for a task or trigger based on existing elements
 * 
 * @param flowYaml - The full flow YAML as string
 * @param parentPath - The path where the task/trigger will be added (e.g., "tasks", "triggers")
 * @returns A unique ID following the pattern "task1", "task2", "trigger1", etc.
 */
export function generateElementId(flowYaml: string, parentPath: string): string {
    if (!flowYaml) {
        return parentPath === "triggers" ? "trigger1" : "task1";
    }

    try {
        const flowObj = YAML_UTILS.parse(flowYaml);
        const elements = flowObj[parentPath] || [];
        
        if (!elements.length) {
            return parentPath === "triggers" ? "trigger1" : "task1";
        }
        
        const prefix = parentPath === "triggers" ? "trigger" : "task";
        const pattern = new RegExp(`^${prefix}(\\d+)$`);
        
        let maxNumber = 0;
        elements.forEach((element: any) => {
            if (element.id) {
                const match = element.id.match(pattern);
                if (match) {
                    const num = parseInt(match[1], 10);
                    if (num > maxNumber) {
                        maxNumber = num;
                    }
                }
            }
        });
        
        return `${prefix}${maxNumber + 1}`;
    } catch (e) {
        return parentPath === "triggers" ? "trigger1" : "task1";
    }
}
