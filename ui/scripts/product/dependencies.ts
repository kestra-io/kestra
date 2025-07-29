type Node = {
    id: string;
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
    };
};

/**
 * Returns a random integer between the given minimum and maximum values, inclusive.
 *
 * @param min - The minimum value that can be returned.
 * @param max - The maximum value that can be returned.
 * @returns A random integer between `min` and `max`.
 */
function getRandomNumber(min: number, max: number): number {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

/**
 * Generates a unique node label in the format `<prefix>-<LETTER>-<NUMBER>`.
 *
 * The prefix is customizable, while the letter is a random uppercase
 * character (A–Z) and the number is a two-digit integer (10–99).
 *
 * @param prefix - The prefix to use for the node label. Defaults to `"flow"`.
 * @returns A string representing a unique node label.
 */
function getNodeLabel(prefix: string = "flow"): string {
    const letter = String.fromCharCode(65 + Math.floor(Math.random() * 26));
    const number = Math.floor(Math.random() * 90 + 10); // 10–99
    return `${prefix}-${letter}-${number}`;
}

/**
 * Configuration options for generating a synthetic dependency graph.
 *
 * @property roots - The number of root nodes at the top level of the graph. Defaults to 1.
 * @property depth - The number of hierarchy levels to generate. Defaults to 2.
 * @property childrenRange - A tuple specifying the minimum and maximum number of children per parent. Defaults to [2, 4].
 * @property total - The maximum number of nodes in the graph. Defaults to 20.
 */
export interface DependencyOptions {
    roots?: number;
    depth?: number;
    childrenRange?: [number, number];
    total?: number;
}

/**
 * Generates a synthetic dependency graph as an array of cytoscape-compatible elements.
 *
 * The graph is structured as a tree-like hierarchy, beginning with the specified
 * number of root nodes and expanding according to the depth and children range.
 *
 * @param options - The configuration options for graph generation.
 * @returns An array of cytoscape-compatible elements representing the nodes and edges.
 * @throws Will throw an error if the total number of nodes is less than the number of roots.
 */
export function getDependencies(options: DependencyOptions): Element[] {
    const {roots = 1, depth = 5, childrenRange = [2, 20], total = 100} = options;

    if (total < roots) {
        throw new Error("Total must be greater than or equal to the number of roots.");
    }

    const nodes: Node[] = [];
    const edges: Edge[] = [];

    // Create the initial root nodes
    const rootNodes: Node[] = Array.from({length: roots}, () => {
        const node = {id: getNodeLabel()};
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
                const child = {id: getNodeLabel()};

                nodes.push(child);
                edges.push({source: parent.id, target: child.id});

                nextLevelNodes.push(child);
                createdCount++;

                if (createdCount >= total) break;
            }
        }

        // Proceed to the next level if there are new children
        currentLevelNodes = nextLevelNodes;
        if (!currentLevelNodes.length || createdCount >= total) break;
    }

    // Return cytoscape-compatible elements
    return [
        ...nodes.map((node) => ({data: {id: node.id}})),
        ...edges.map((edge, i) => ({data: {id: `e${i}`, source: edge.source, target: edge.target}})),
    ];
}
