// What the flow editor reads to validate, autocomplete and document a flow: the plugin list, schemas, icons and per-type documentation.
// Install jobs, plugin UI remotes and task endpoints are left out, a stale copy of those would be wrong rather than merely old.
export const EDITOR_REFERENCE_PATTERN = /\/api\/v1\/(?:[^/]+\/)?plugins(?:\/(?:schemas|properties|inputs|icons|groups)(?:\/[^?]*)?|\/(?:[\w$-]+\.)+[\w$-]+(?:\/versions(?:\/[^/?]+)?)?)?(?:\?.*)?$/
