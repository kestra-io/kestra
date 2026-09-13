// Eager `?raw` glob inlines every @types/node .d.ts here; reached only via dynamic import()
// from monacoEnvironment, so the whole tree is one lazy chunk, off the boot critical path.
const nodeTypes = import.meta.glob("/node_modules/@types/node/**/*.d.ts", {eager: true, query: "?raw", import: "default"}) as Record<string, string>

export default nodeTypes
