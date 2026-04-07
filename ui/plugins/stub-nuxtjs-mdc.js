/**
 * Stub for @nuxtjs/mdc, which is a peer dependency of @kestra-io/ui-libs that is
 * not installed in this project. All imports from @nuxtjs/mdc/* are aliased to this
 * file in vite.config.js to prevent Rolldown from erroring on unresolved imports.
 */
import {computed as vueComputed} from "vue";

// Re-export stubs from @kestra-io/ui-libs where applicable
export const useRuntimeConfig = () => ({
    public: {
        mdc: {
            headings: {
                anchorLinks: true
            }
        }
    }
});
export const computed = vueComputed;
export const resolveComponent = () => undefined;

// Stubs for @nuxtjs/mdc/runtime named exports
export const createMarkdownParser = async () => () => null;
export const createShikiHighlighter = async () => ({});
export const rehypeHighlight = () => {};

export default {};
