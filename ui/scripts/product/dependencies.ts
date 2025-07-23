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

function getRandom<T>(arr: T[]): T {
    return arr[Math.floor(Math.random() * arr.length)];
}

/**
 * Generates a synthetic lineage graph as Cytoscape-compatible elements.
 *
 * Each node has an ID in the format: `flow-<LETTER>-<NUMBER>`.
 * Nodes are connected either in a single-root structure or as a randomly connected DAG.
 *
 * @param count - Total number of nodes to generate (must be ≥ 2).
 * @param singleRoot - If true, creates a star topology with one root node connected to all others.
 *                     If false, builds a randomly connected acyclic graph with additional cross-links.
 * @returns An array of Cytoscape-compatible elements (nodes and edges) to be used in a graph visualization.
 */
export function getDependencies(count: number, singleRoot: boolean): Element[] {
    if (count < 2) {
        throw new Error("Count must be at least 2.");
    }

    const nodes: Node[] = [];

    for (let i = 0; i < count; i++) {
        const letter = String.fromCharCode(65 + Math.floor(Math.random() * 26)); // A-Z
        const number = Math.floor(Math.random() * 90 + 10); // 10–99
        const id = `flow-${letter}-${number}`;

        nodes.push({id});
    }

    const edges: Edge[] = [];

    if (singleRoot) {
        const root = nodes[0];

        for (let i = 1; i < nodes.length; i++) {
            edges.push({source: root.id, target: nodes[i].id});
        }
    } else {
        const parentNodes: Node[] = nodes.slice(0, Math.max(1, Math.floor(count / 10)));

        const connected = new Set<string>(parentNodes.map((n) => n.id));
        const unconnected = nodes.filter((n) => !connected.has(n.id));

        for (const node of unconnected) {
            const parent = getRandom(Array.from(connected).map((id) => nodes.find((n) => n.id === id)!));

            edges.push({source: parent.id, target: node.id});
            connected.add(node.id);
        }

        const extraEdgeCount = Math.floor(count * 0.5);
        for (let i = 0; i < extraEdgeCount; i++) {
            const source = getRandom(nodes);
            const target = getRandom(nodes);

            if (source.id !== target.id) {
                edges.push({source: source.id, target: target.id});
            }
        }
    }

    const elements: Element[] = [
        ...nodes.map((node) => ({data: {id: node.id}})),
        ...edges.map((edge, i) => ({data: {id: `e${i}`, source: edge.source, target: edge.target}})),
    ];

    return elements;
}
