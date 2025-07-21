type Node = {
    id: string;
    namespace: string;
    uid: string;
};

type Edge = {
    source: string;
    target: string;
};

const teams = ["platform", "infra", "data", "devops", "frontend", "backend", "mobile", "ml", "security", "qa"]
const companies = ["openai", "google", "microsoft", "netflix", "amazon", "airbnb", "uber", "stripe", "slack", "github"]
const services = ["auth", "billing", "notify", "search", "analytics", "scheduler", "workflow", "events", "images"];

/**
 * Returns a random element from the given array.
 */
function getRandom<T>(arr: T[]): T {
    return arr[Math.floor(Math.random() * arr.length)];
}

/**
 * Generates a synthetic lineage graph of nodes and edges.
 *
 * @param count - Total number of nodes to generate. Must be >= 2.
 * @param singleRoot - If true, all nodes (except the first) are direct children of a single root node.
 *                     If false, multiple roots and a deeper graph structure will be created.
 * @returns An object containing an array of nodes and their connecting edges.
 */
export function getData(count: number, singleRoot: boolean): { nodes: Node[]; edges: Edge[] } {
    if (count < 2) {
        throw new Error("Count must be at least 2.");
    }

    const nodes: Node[] = [];

    for (let i = 0; i < count; i++) {
        const company = getRandom(companies);
        const team = getRandom(teams);
        const id = `${getRandom(services)}_${i}`;
        const namespace = `${company}.${team}`;
        const uid = `main_${namespace}_${id}`;

        nodes.push({uid, namespace, id});
    }

    const edges: Edge[] = [];

    if (singleRoot) {
        const root = nodes[0];

        for (let i = 1; i < nodes.length; i++) {
            edges.push({
                source: root.uid,
                target: nodes[i].uid,
            });
        }
    } else {
        const parentNodes: Node[] = nodes.slice(0, Math.max(1, Math.floor(count / 10)));

        const connected = new Set<string>(parentNodes.map((n) => n.uid));
        const unconnected = nodes.filter((n) => !connected.has(n.uid));

        for (const node of unconnected) {
            const parent = getRandom(
                Array.from(connected).map((uid) => nodes.find((n) => n.uid === uid)!)
            );

            edges.push({
                source: parent.uid,
                target: node.uid,
            });

            connected.add(node.uid);
        }

        // Add additional edges for complexity
        const extraEdgeCount = Math.floor(count * 0.5);

        for (let i = 0; i < extraEdgeCount; i++) {
            const source = getRandom(nodes);
            const target = getRandom(nodes);

            if (source.uid !== target.uid) {
                edges.push({source: source.uid, target: target.uid});
            }
        }
    }

    return {nodes, edges};
}
