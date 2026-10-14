import {h, inject, onMounted, ref, type Component} from "vue"
import {KsSkeleton} from "@kestra-io/design-system"
import {SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../injectionKeys"

type TaskComponents = typeof import("./getTaskComponent")

let loaded: TaskComponents | undefined
let loading: Promise<TaskComponents> | undefined

function loadTaskComponents(): Promise<TaskComponents> {
    loading ??= import("./getTaskComponent").then((module) => {
        loaded = module
        return module
    })
    return loading
}

export type TaskBlockComponent = Component & { ksTaskName: string }

const SkeletonComponent: TaskBlockComponent = Object.assign(
    () => h(KsSkeleton, {rows: 1, animated: true}),
    {ksTaskName: ""},
)

export function useBlockComponent() {
    const definitionsRef = inject(SCHEMA_DEFINITIONS_INJECTION_KEY)
    const definitions = definitionsRef?.value ?? {}

    const resolve = (module: TaskComponents) =>
        (property: unknown, key?: string, siblingKeys?: string[]): TaskBlockComponent =>
            module.getTaskComponent(property, definitions, key, siblingKeys)

    const getBlockComponent = ref<(property: unknown, key?: string, siblingKeys?: string[]) => TaskBlockComponent>(
        loaded ? resolve(loaded) : () => SkeletonComponent,
    )

    onMounted(async () => {
        if (loaded) return
        getBlockComponent.value = resolve(await loadTaskComponents())
    })

    return {
        getBlockComponent,
    }
}
