import {setup} from "@storybook/vue3-vite";
import {withThemeByClassName} from "@storybook/addon-themes";
import initApp from "../src/utils/init";
import {configureAxios} from "@kestra-io/kestra-sdk";
import axios from "axios";
import {vueRouter} from "storybook-vue3-router";

import "../src/styles/vendor.scss";
import "../src/styles/app.scss";
import en from "../src/translations/en.json";

window.KESTRA_BASE_PATH = "/ui";
window.KESTRA_UI_PATH = "./";

// No backend is running during storybook tests, so short-circuit every axios
// request with an empty successful response instead of letting it hit the
// network — this prevents network errors, proxy failures, and the Vue/axios
// error cascade that follows.
// Per axios docs, a custom adapter is just a function assigned to
// `axios.defaults.adapter`: https://axios-http.com/docs/adapters — it must NOT
// be read back out and re-invoked (axios.defaults.adapter is normally an array
// of built-in adapter *names* like ["xhr", "http", "fetch"], not a callable).
// This is intentionally unconditional rather than gated on `config.url`
// containing "/api/": the SDK does not set a fixed `baseURL`, so a request's
// `config.url` is not guaranteed to contain that substring, and there is no
// real backend for ANY request to legitimately reach in this environment.
axios.defaults.adapter = async (config) => ({data: [], status: 200, statusText: "OK", headers: {}, config, request: {}});

// Mirrors the #topnav-*-slot elements rendered by AppTopNavBar.vue, which
// TopNavBar.vue's <Teleport> targets rely on. In the real app AppTopNavBar
// is mounted once at the layout root before any page's TopNavBar teleports
// into it; Storybook has no such layout wrapper, so we render the same
// targets as siblings ahead of <story/> in the decorator tree.
const withTopNavTeleportTargets = () => ({
  template: `
    <div>
      <span id="topnav-title-slot"></span>
      <div id="topnav-description-slot"></div>
      <div id="topnav-actions-slot"></div>
      <story/>
    </div>
  `,
});

/**
 * @type {import('@storybook/vue3-vite').Preview}
 */
const preview = {
  parameters: {
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
  },
  decorators: [
    vueRouter([
      {path: "/", name: "home", component: {template: "<div>home</div>"}},
      {path: "/about", name: "about", component: {template: "<div>about</div>"}},
      {path: "/:pathMatch(.*)*", name: "catchAll", component: {template: "<div/>"}},
    ]),
    withThemeByClassName({
        themes: {
          light: "light",
          dark: "dark",
        },
        defaultTheme: "light",
      }),
    withTopNavTeleportTargets,
  ]
};

setup(async (app) => {
  const {piniaStore} = await initApp(app, [], {}, en);
  configureAxios({},  {oss:true})
  piniaStore.use(({store}) => {
    store.$http = {
        get: () => Promise.resolve({data: []}),
    }
  });
})

window.addEventListener("unhandledrejection", (evt) => {
    if (evt?.reason?.stack?.includes?.("/monaco/esm/vs") || evt?.reason?.stack?.includes?.("/monaco/min/vs")) {
        evt.stopImmediatePropagation()
    }
})

import "../src/utils/monacoEnvironment"

const NodeTypesRaw = import.meta.glob("../node_modules/@types/node/**/*.d.ts", {eager: true, query: "?raw", import: "default"})
function loadNodeTypes(tries = 0) {
    import("monaco-editor/esm/vs/editor/editor.api").then(({languages}) => {
        if (languages.typescript) {
            for (const path in NodeTypesRaw) {
                languages.typescript.typescriptDefaults.addExtraLib(NodeTypesRaw[path], `file://${path}`)
            }
        } else if (tries <= 15) {
            setTimeout(() => loadNodeTypes(tries + 1), (tries + 1) * 100)
        }
    })
}
loadNodeTypes()

export default preview;
