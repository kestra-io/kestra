# @kestra-io/topology

The flow graph Kestra draws in its flow editor and on the execution page, as a Vue 3 component built on [Vue Flow](https://vueflow.dev).

Give it a flow's YAML and the graph Kestra's API computes for it. It lays out the tasks, triggers, flowable branches and error handlers with dagre. Pass an execution too and every task shows its state.

## Where Kestra uses it

- The flow editor's topology view and an execution's graph, both rendered by [`LowCodeEditor.vue`](https://github.com/kestra-io/kestra/blob/develop/ui/src/components/inputs/LowCodeEditor.vue)
- The blueprint previews on [kestra.io](https://kestra.io/blueprints)

## Install

```bash
npm install @kestra-io/topology @kestra-io/design-system element-plus @vue-flow/core @vue-flow/controls @vue-flow/background vue-i18n
```

`vue`, `@vue-flow/core`, `@vue-flow/controls`, `@vue-flow/background`, `@kestra-io/design-system`, `element-plus` and `vue-i18n` are peer dependencies, so the app owns their versions. The design system brings peers of its own, listed in [its README](https://github.com/kestra-io/kestra/tree/develop/ui/packages/design-system#readme).

npm lists `vue-i18n` as optional, but the nodes call `useI18n()`, so the app has to install it (`app.use(createI18n({legacy: false, locale: "en"}))` is enough).

## Usage

```vue
<template>
    <div class="graph">
        <Topology v-if="flowGraph" id="hello-world" :source="source" :flowGraph="flowGraph" />
    </div>
</template>

<script setup lang="ts">
    import {onMounted, ref} from "vue"
    import {Topology, type FlowGraph} from "@kestra-io/topology"
    import "@kestra-io/topology/standalone.css"
    import "@kestra-io/topology/style.css"

    const source = `id: hello-world
namespace: company.team
tasks:
  - id: hello
    type: io.kestra.plugin.core.log.Log
    message: Hello World!`

    const flowGraph = ref<FlowGraph>()

    onMounted(async () => {
        const response = await fetch("http://localhost:8080/api/v1/main/flows/graph", {
            method: "POST",
            headers: {"Content-Type": "application/x-yaml"},
            body: source,
        })
        flowGraph.value = await response.json()
    })
</script>

<style scoped>
    .graph {
        height: 600px;
    }
</style>
```

The canvas fills its parent, so give the parent a height.

### Getting the graph

The layout comes from Kestra's server, which knows every plugin's structure. Two endpoints return a `FlowGraph`, both behind your instance's authentication:

- `POST /api/v1/{tenant}/flows/graph` with the flow YAML as the body (`Content-Type: application/x-yaml`), for a flow that is not saved yet
- `GET /api/v1/{tenant}/flows/{namespace}/{id}/graph`, for a saved flow

The tenant is `main` on an open-source instance. Both take a `subflows` query parameter listing the subflow tasks to draw expanded.

### Styles

`@kestra-io/topology/style.css` holds the graph's own styles and the Vue Flow base styles. Its colors, sizes and spacing come from the design system's `--ks-*` tokens.

- In an app that loads `@kestra-io/design-system/styleBase` and installs the design-system plugin, as the Kestra UI does, `style.css` is all it needs.
- Anywhere else, load `@kestra-io/topology/standalone.css` before it. It carries the design system's tokens, light and dark (`html.dark`), declared on the graph and on its tooltips and menus rather than on `:root`, so the graph looks as it does in Kestra whatever `--ks-*` variables the page defines, and the page's own styles are left alone. It also carries the Element Plus base styles its tooltips and menus need, in the `kel` namespace the graph provides to its own components. It holds only what the graph reads: tokens nothing in the graph or its design-system components refers to, and Element Plus rules for components it never renders, are left out. It does not set a font: the graph uses the page's.

`standalone.css` is generated from the design system by `npm run generate:standalone`; a unit test fails when it falls out of date.

### Task icons

The nodes render each task's icon through the component the app provides under `TASK_ICON_INJECTION_KEY`. Without one they show a generic placeholder:

```ts
import {TASK_ICON_INJECTION_KEY} from "@kestra-io/design-system"

app.provide(TASK_ICON_INJECTION_KEY, TaskIcon)
```

The component receives the task class as `cls`, plus the `icons` map and `loadIcon` function passed to `<Topology>`.

### Translations

The labels are keys of the Kestra UI's messages (`topology-graph.*`, `state_history.*`, `edit`, `delete` and a few more), read through the app's `vue-i18n` instance. The package ships their English values and adds the ones the app does not define to its `en` messages, so an app without Kestra's translations shows English labels. The English values are kept equal to [`ui/src/translations/en.json`](https://github.com/kestra-io/kestra/blob/develop/ui/src/translations/en.json) by a unit test.

### Controlling the canvas

`id` names the Vue Flow store of the graph. Call `useVueFlow` with the same id to drive it from outside:

```ts
import {useVueFlow} from "@vue-flow/core"

const {fitView, zoomIn, zoomOut} = useVueFlow("hello-world")
```

## Props

| Prop | Type | Default | |
|---|---|---|---|
| `id` | `string` | required | Vue Flow store id, unique per graph on the page |
| `source` | `string` | required | The flow YAML |
| `flowGraph` | `FlowGraph` | required | The graph returned by the API |
| `isHorizontal` | `boolean` | `false` | Lay the flow out left to right instead of top to bottom |
| `isReadOnly` | `boolean` | `true` | Turn off every way of changing the flow from the graph |
| `isAllowedEdit` | `boolean` | `false` | With `isReadOnly` off, let the user add tasks and triggers and drag tasks to move them |
| `toggleOrientationButton` | `boolean` | `false` | Add a control that emits `toggle-orientation` |
| `execution` | `GraphExecution` | | An execution of the flow, to show each task's state |
| `subflowsExecutions` | `Record<string, GraphExecution>` | `{}` | Executions of the expanded subflows |
| `expandedSubflows` | `string[]` | `[]` | The subflow tasks currently drawn expanded |
| `enableSubflowInteraction` | `boolean` | `true` | Let subflow tasks expand and link to their subflow |
| `icons` | `PluginIconMap` | `{}` | Plugin icons, passed to the task icon component |
| `loadIcon` | `(cls: string) => Promise<PluginIconData \| undefined>` | | Loads an icon missing from `icons` |

The component accepts a few more props for the Kestra UI's own features (playground, validation, loop lanes, artifacts). [`Topology.vue`](https://github.com/kestra-io/kestra/blob/develop/ui/packages/topology/src/Topology.vue) lists them all.

## Events

The node menus emit the names in the exported `EVENTS` constant: `edit`, `delete`, `duplicate`, `runTask`, `openLink`, `showLogs`, `showOutputs`, `showArtifacts`, `replayTask`, `showDescription`, `showCondition`, `showDetails`, `showCustomAction`, `addTask`, `addTrigger`, `editFlow`, `moveTask`, `loopStep` and `loopScopeFailed`.

The canvas also emits:

- `loading`, `true` while the layout runs and `false` once it is drawn
- `expand-subflow`, with the new list of subflow tasks to expand: fetch the graph again with them in `subflows` and pass the list back as `expandedSubflows`
- `toggle-orientation`, from the control added by `toggleOrientationButton`

## Slots

`taskDetails`, `taskActions` and `loopScope` add the app's own content to a node, and receive that node's data as slot props. `scopeBar` sits above the canvas and receives `collapsedLanes` and `setLanesCollapsed`.

## Entry points

| Import | Contents |
|---|---|
| `@kestra-io/topology` | `Topology`, `NodeMenu`, `NodeMenuItem`, `Duration`, `LoopStateBar`, `useScreenshot`, `EVENTS`, the injection keys and the graph types |
| `@kestra-io/topology/vue-flow-utils` | The layout and graph helpers alone, without the components |
| `@kestra-io/topology/style.css` | The stylesheet |

## In the Kestra repositories

The package is an npm workspace, so `ui/` and `kestra-ee/ui-ee/` depend on it with `"@kestra-io/topology": "*"` and compile its TypeScript sources directly, with no build step.

## Development

```bash
npm run build          # tsdown: JS, types and style.css in dist/
npm run dev            # the same build, in watch mode
npm run check:types    # vue-tsc
npm test               # unit tests
npm run lint           # eslint, with fixes
```

## Publishing

The package is published to npm by the **Publish Design System** workflow ([`.github/workflows/publish-design-system.yml`](https://github.com/kestra-io/kestra/blob/develop/.github/workflows/publish-design-system.yml)). Run it with `topology` as the package and a patch, minor or major increment.

## License

[Apache 2.0](https://github.com/kestra-io/kestra/blob/develop/LICENSE)
