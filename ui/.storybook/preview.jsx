// Must stay the FIRST import: it patches window.fetch before any src/ or SDK module is evaluated.
import {apiFetch, beginStoryScope} from "./apiMock";
import {setup} from "@storybook/vue3-vite";
import {withThemeByClassName} from "@storybook/addon-themes";
import initApp from "../src/utils/init";
import {globalI18n} from "../src/translations/i18n";
import {configureClient, useClient} from "@kestra-io/kestra-sdk";
import axios from "axios";
import {createMemoryHistory} from "vue-router";
import {vueRouter} from "storybook-vue3-router";

import "../src/styles/vendor.scss";
import "../src/styles/app.scss";
import en from "../src/translations/en.json";

window.KESTRA_BASE_PATH = "/ui/";
window.KESTRA_UI_PATH = "./";

// No backend is running during storybook tests, so short-circuit every axios
// request with an empty successful response instead of letting it hit the
// network — this prevents network errors, proxy failures, and the Vue/axios
// error cascade that follows. The fetch-based SDK — which is everything except
// the 4 remaining axios importers in src/ — is handled by ./apiMock instead.
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
// into it; Storybook has no such layout wrapper.
//
// These are appended directly to document.body — as plain DOM nodes, outside
// Vue's own render tree — rather than rendered as template siblings in a
// decorator. Teleport resolves its target via a synchronous
// document.querySelector() call the instant it mounts; a decorator-rendered
// sibling only reliably exists in time for components whose own mount is a
// shallow, single synchronous pass. Components/Admin/Triggers renders
// <TopNavBar> as a root element behind an extra layer of story/decorator
// nesting, which raced the sibling into existing too late and printed
// "Failed to locate Teleport target". Real, static body-level nodes created
// once before any story ever mounts have no such race.
for (const id of ["topnav-title-slot", "topnav-description-slot", "topnav-actions-slot"]) {
  if (!document.getElementById(id)) {
    const el = document.createElement(id === "topnav-title-slot" ? "span" : "div");
    el.id = id;
    document.body.appendChild(el);
  }
}

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
    // createMemoryHistory keeps route state in JS memory instead of the real
    // window.location/history APIs, so router.push() and friends can't trigger
    // a native browser navigation during a story or interaction test.
    vueRouter(
      [
        {path: "/", name: "home", component: {template: "<div>home</div>"}},
        {path: "/about", name: "about", component: {template: "<div>about</div>"}},
        {path: "/:pathMatch(.*)*", name: "catchAll", component: {template: "<div/>"}},
      ],
      {vueRouterOptions: {history: createMemoryHistory()}},
    ),
    withThemeByClassName({
        themes: {
          light: "light",
          dark: "dark",
        },
        defaultTheme: "light",
      }),
  ],
  // Name the running story in "unmocked API request" warnings, and clear their de-duplication
  // record, so an unmocked route is reported for every story it affects and points at the story to
  // fix rather than at nothing.
  beforeEach({title, name}) {
    beginStoryScope(`${title} > ${name}`);
  },
};

setup(async (app) => {
  const {piniaStore} = await initApp(app, [], {}, en);
  // Isolated stories lack many namespaced i18n keys, so silence vue-i18n's
  // noisy "Not found" warnings in Storybook (it already falls back to the key).
  globalI18n.value.missingWarn = false;
  globalI18n.value.fallbackWarn = false;
  // Pin the SDK's fetch to the mock: the generated client resolves
  // `options.fetch ?? _config.fetch ?? globalThis.fetch`, so this makes the generated-SDK path
  // explicit rather than depending on the global patch alone.
  configureClient({fetch: apiFetch})
  // The real app gives stores the full client (src/main.ts), so binding only `get` here left
  // `$http.post/put/delete` as TypeErrors. useClient() goes through the mocked fetch like
  // everything else.
  piniaStore.use(({store}) => {
    store.$http = useClient();
  });
})

// The unhandledrejection listener that used to live here now lives in ./apiMock, installed before
// anything else: it drops the same monaco teardown rejections (matching the node_modules path the
// dev server actually serves, which this one missed) and prints the reason of every other rejection,
// which vitest otherwise reports as a bare `PromiseRejectionEvent {isTrusted: true}`.

import "../src/utils/monacoEnvironment"

export default preview;
