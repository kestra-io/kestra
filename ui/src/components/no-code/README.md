### Guide on How to Handle the `NoCode` Editor

> Code within `Vue` components must be written using the `<script setup>` block, as enforced by the `ESLint` rule.

This is a brief guide on working with the `NoCode` editor component.

#### Flow schema loading

The flow schema is megabytes of JSON, so it is only fetched by what needs it: the no-code tab when it mounts (`BlockEditor.vue`, or `useFlowFields` for the legacy engine), and topology once a form is opened, through the `load()` of `SCHEMA_LOADER_INJECTION_KEY` that `TaskEditor.vue` calls. The YAML editor does not load it; its worker fetches its own copy.

Until the response lands, `pluginsStore.flowSchema` is the placeholder bundled in `src/stores/flow-schema.json`: the real `Flow` root and every core model it references (inputs, retry, SLA, ...), with each list of plugins cut down to a single sample task. Flow-level fields therefore render at once and do not change when the full schema arrives. A block the placeholder cannot resolve, such as a task of any other type, shows a skeleton while the loader's `isLoading` is true.

Regenerate the placeholder whenever the `Flow` model or one of the models it references changes:

```bash
npm run generate:flow-schema-placeholder -- http://localhost:8080/api/v1/plugins/schemas/flow
```
