import {v4 as uuid} from "uuid";

import {getRandomFlowID} from "./flow";

type Node = {
    id: string;
    type: "NODE";
    flow: string;
    namespace: string;
    revision: number;
    state: string;
};

type Edge = {
    id: string;
    type: "EDGE";
    source: string;
    target: string;
};

export type Element = { data: Node } | { data: Edge };

type DependencyOptions = {
    roots?: number;
    depth?: number;
    childrenRange?: [number, number];
    total?: number;
}

const namespaces = ["company", "team", "github", "qa", "system", "dev", "test", "data", "infra", "cloud", "backend", "frontend", "api", "services", "database", "mobile", "security"];

const states = ["CANCELLED", "CREATED", "FAILED", "KILLED", "KILLING", "PAUSED", "QUEUED", "RESTARTED", "RETRIED", "RETRYING", "RUNNING", "SKIPPED", "SUCCESS", "WARNING"];

/**
 * Returns a random integer between the given minimum and maximum values (inclusive).
 *
 * @param min - The minimum integer value.
 * @param max - The maximum integer value.
 * @returns A random integer between `min` and `max`.
 */
function getRandomNumber(min: number, max: number): number {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

/**
 * Generates a random hierarchical namespace string with a depth of 1 to 4 levels.
 *
 * @returns A dot-separated namespace string, e.g., "company.team.github".
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
 * Generates a synthetic dependency graph as an array of cytoscape compatible elements.
 *
 * The graph starts with `roots` root nodes and grows hierarchically up to the specified
 * `depth`. Each node includes a namespace, revision, and state.
 *
 * @param options - Graph generation options.
 * @param options.roots - Number of root nodes (default 1).
 * @param options.depth - Hierarchy depth levels (default 5).
 * @param options.childrenRange - Min and max children per node (default [2, 20]).
 * @param options.total - Maximum total nodes to generate (default 100).
 * @returns An array of cytoscape compatible elements (nodes and edges).
 *
 * @throws Will throw an error if `total` is less than `roots`.
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
            id: uuid(),
            type: "NODE",
            flow: getRandomFlowID(),
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
                    id: uuid(),
                    type: "NODE",
                    flow: getRandomFlowID(),
                    namespace: getRandomNamespace(),
                    revision: getRandomNumber(1, 100),
                    state: states[getRandomNumber(0, states.length - 1)],
                };

                nodes.push(child);
                edges.push({id: uuid(), type: "EDGE", source: parent.id, target: child.id});

                nextLevelNodes.push(child);
                createdCount++;

                if (createdCount >= total) break;
            }
        }

        currentLevelNodes = nextLevelNodes;
        if (!currentLevelNodes.length || createdCount >= total) break;
    }

    // Convert nodes and edges into cytoscape elements and return combined array
    return [
        ...nodes.map(({id, flow, namespace, revision, state, type}) => ({data: {id, type, flow, namespace, revision, state}})),
        ...edges.map(({id, source, target, type}) => ({data: {id, type, source, target}})),
    ];
}
