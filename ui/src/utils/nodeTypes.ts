// Several megabytes of raw @types/node typings for the Monaco TypeScript
// language service. Only ever import this module dynamically — a static
// import would inline the whole payload into the importer's chunk.
export default import.meta.glob("/node_modules/@types/node/**/*.d.ts", {eager: true, query: "?raw", import: "default"}) as Record<string, string>
