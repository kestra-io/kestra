import {getRandomFlowID} from "./flow";

type Node = {
    id: string;
    namespace: string;
    revision: number;
    state: string;
};

type Edge = {
    source: string;
    target: string;
};

type Element = {
    data: {
        id: string;
        source?: string;
        target?: string;
        namespace?: string;
        revision?: number;
        state?: string;
    };
};

export interface DependencyOptions {
    roots?: number;
    depth?: number;
    childrenRange?: [number, number];
    total?: number;
}

const namespaces = ["company", "team", "github", "qa", "system", "dev", "test", "data", "infra", "cloud"];

const states = ["CANCELLED", "CREATED", "FAILED", "KILLED", "KILLING", "PAUSED", "QUEUED", "RESTARTED", "RETRIED", "RETRYING", "RUNNING", "SKIPPED", "SUCCESS", "WARNING"];

/**
 * Returns a random integer between the given minimum and maximum values (inclusive).
 *
 * @param min - The minimum value.
 * @param max - The maximum value.
 * @returns A random integer between `min` and `max`.
 */
function getRandomNumber(min: number, max: number): number {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

/**
 * Generates a random hierarchical namespace string with a depth of 1–4 levels.
 *
 * Example output: `"company.team.github"`
 *
 * @returns A dot-separated namespace string.
 */
function getRandomNamespace(): string {
    const depth = getRandomNumber(1, 4);

    const parts: string[] = [];

    for (let i = 0; i < depth; i++) {
        parts.push(namespaces[getRandomNumber(0, namespaces.length - 1)]);
    }

    return parts.join(".");
}

/**
 * Generates a synthetic dependency graph as an array of Cytoscape-compatible elements.
 *
 * The graph starts with the specified number of root nodes and grows hierarchically
 * according to depth and children range. Each node contains a namespace, revision,
 * and state.
 *
 * @param options - Graph configuration options.
 * @returns An array of Cytoscape-compatible elements (nodes and edges).
 *
 * @throws If the total number of nodes is less than the number of roots.
 */
export function getDependencies(options: DependencyOptions): Element[] {
    const {roots = 1, depth = 5, childrenRange = [2, 20], total = 100} = options;

    if (total < roots) {
        throw new Error("Total must be greater than or equal to the number of roots.");
    }

    const nodes: Node[] = [];
    const edges: Edge[] = [];

    // Create root nodes
    const rootNodes: Node[] = Array.from({length: roots}, () => {
        const node: Node = {
            id: getRandomFlowID(),
            namespace: getRandomNamespace(),
            revision: getRandomNumber(1, 100),
            state: states[getRandomNumber(0, states.length - 1)],
        };
        nodes.push(node);
        return node;
    });

    let currentLevelNodes = rootNodes;
    let createdCount = roots;

    // Generate child nodes for each level
    for (let level = 1; level <= depth; level++) {
        const nextLevelNodes: Node[] = [];

        for (const parent of currentLevelNodes) {
            if (createdCount >= total) break;

            const childrenCount = Math.min(getRandomNumber(childrenRange[0], childrenRange[1]), total - createdCount);

            for (let i = 0; i < childrenCount; i++) {
                const child: Node = {
                    id: getRandomFlowID(),
                    namespace: getRandomNamespace(),
                    revision: getRandomNumber(1, 100),
                    state: states[getRandomNumber(0, states.length - 1)],
                };

                nodes.push(child);
                edges.push({source: parent.id, target: child.id});

                nextLevelNodes.push(child);
                createdCount++;

                if (createdCount >= total) break;
            }
        }

        currentLevelNodes = nextLevelNodes;
        if (!currentLevelNodes.length || createdCount >= total) break;
    }

    // Convert nodes and edges into Cytoscape elements
    return [
        ...nodes.map((node) => ({
            data: {
                id: node.id,
                namespace: node.namespace,
                revision: node.revision,
                state: node.state,
            },
        })),
        ...edges.map((edge, i) => ({
            data: {
                id: `e${i}`,
                source: edge.source,
                target: edge.target,
            },
        })),
    ];
}
