import {transformVNodeArgs} from "vue"

/**
 * `mount` from `@vue/test-utils` installs a global, last-call-wins `transformVNodeArgs` (the one that swaps stubbed
 * components for their stubs) and never removes it. Vue is externalized and `isolate: false` keeps one copy per worker,
 * so a file that stubbed or shallow-mounted a component leaves the transformer active for every later file in that worker,
 * including specs that render with a plain `createApp` and expect the real component, which then comes back as
 * `<ks-markdown-stub>`. Calling it with no transformer clears it.
 */
export const resetVNodeTransformer = (): void => {
    transformVNodeArgs()
}
