import {ref, shallowReactive, markRaw, defineComponent, h, onErrorCaptured, type Component} from "vue"
import {useI18n} from "vue-i18n"
import {apiUrlWithoutTenants} from "override/utils/route"
import {loadRemote, registerRemotes, registerShared} from "@module-federation/enhanced/runtime"
import * as PluginsAPI from "@kestra-io/kestra-sdk/plugins"
import {KnownSlotsPropNames, ManifestsRegistry, type KnownSlotProps} from "@kestra-io/slot-contracts"
import {PluginUiModuleWithGroup} from "@kestra-io/kestra-sdk"


function wrapWithErrorBoundary(inner: Component) {
    return defineComponent({
        name: "FederatedModuleBoundary",
        inheritAttrs: false,
        setup(_, {attrs, slots}) {
            const {t} = useI18n()
            const error = ref<Error | null>(null)

            onErrorCaptured((err: Error) => {
                error.value = err
                console.error("[FederatedModule] Error caught by boundary:", err)
                return false
            })

            return () => {
                if (error.value) {
                    return h("div", {class: "federated-module-error"}, t("plugins.ui_load_error"))
                }
                return h(inner, attrs, slots)
            }
        },
    })
}

function addCSSLinkIfNotAlreadyPresent(href: string) {
    if (!document.querySelector(`link[href="${href}"]`)) {
        const link = document.createElement("link")
        link.rel = "stylesheet"
        link.href = href
        document.head.appendChild(link)
    }
}

// A slot that hosts several modules per task type ("execution-artifact") registers each one as
// `<slot>/<id>` in the manifest; the module is then keyed `<taskType>/<id>` here.
function moduleKey(slotName: string, uiModule: string | undefined, taskTypeKey: string): string | undefined {
    if (uiModule === slotName) return taskTypeKey
    return uiModule?.startsWith(`${slotName}/`) ? `${taskTypeKey}/${uiModule.slice(slotName.length + 1)}` : undefined
}

export function useFederatedModule<T extends keyof typeof KnownSlotsPropNames>(slotName: T) {

    const RemoteComponents = shallowReactive<Record<string, Component>>({})
    const subtitleResolvers = shallowReactive<Record<string, (ctx: never) => unknown>>({})
    const taskAdditionalInfoRemote = ref<Record<string, ManifestsRegistry[T]>>({})

    const manifestReady = ref(false)
    const failedModules = shallowReactive<Record<string, true>>({})

    async function resolveRemoteComponent(taskTypes: {cls: string, version: string | undefined}[]) {
        
        // return early if no task is passed
        if(taskTypes.length === 0){
            manifestReady.value = true
            return
        }

        let pluginTaskManifests: Record<string, PluginUiModuleWithGroup[]> = {}

        try {
            // get the manifest of the all the tasks we will
            // have in the graph
            const pluginTaskManifestsResponse = await PluginsAPI.pluginUiManifest({body: Array.from(taskTypes)})
            pluginTaskManifests = pluginTaskManifestsResponse?.manifest ?? {}
        } catch (error) {
            console.error("[FederatedModule] Failed to load plugin UI manifest:", error)
        } finally {
            manifestReady.value = true
        }

        for(const taskTypeKey in pluginTaskManifests){
            for(const manifest of pluginTaskManifests[taskTypeKey]){
                const key = moduleKey(slotName, manifest.uiModule, taskTypeKey)
                if(key){
                    if(manifest.staticInfo){
                        taskAdditionalInfoRemote.value[key] = manifest.staticInfo as ManifestsRegistry[T]
                    }
                }
            }
        }

        for(const taskTypeKey in pluginTaskManifests){
            for(const manifest of pluginTaskManifests[taskTypeKey]){
                const key = moduleKey(slotName, manifest.uiModule, taskTypeKey)
                if(key){
                    const remoteName = `remote--${taskTypeKey}`
                    const basePath = `${apiUrlWithoutTenants()}/plugins/${manifest.group}/pluginUi/`

                    if(manifest.styles){
                        manifest.styles.forEach((style: string) => addCSSLinkIfNotAlreadyPresent(`${basePath}${style}`))
                    }

                    const sourceHash = manifest.sourceHash ?? Math.random().toString(36).substring(7)

                    registerRemotes([
                        {
                            type: "module",
                            name: remoteName,
                            entry: `${basePath}plugin-ui.js?${sourceHash}`,
                        },
                    ])

                    registerShared({
                        vue: {
                            shareConfig: {
                                requiredVersion: "^3",
                                singleton: true,
                            },
                        },
                        "vue-i18n": {
                            shareConfig: {
                                requiredVersion: "^11",
                                singleton: true,
                            },
                        },
                    })

                    const taskRoot = manifest.group ? taskTypeKey.slice(manifest.group.length + 1) : []
                    const remoteId = `${remoteName}/${taskRoot}/${manifest.uiModule}`
                    
                    let module: {default: Component, getSubtitle?: (ctx: never) => unknown} | null = null
                    try {
                        module = await loadRemote<{default: Component, getSubtitle?: (ctx: never) => unknown}>(remoteId)
                    } catch(err) {
                        console.error(`[FederatedModule] loadRemote FAILED for "${remoteId}":`, err)
                        failedModules[key] = true
                        continue
                    }

                    if(!module){
                        console.error(`[FederatedModule] loadRemote returned null for "${remoteId}"`)
                        failedModules[key] = true
                        continue
                    }

                    RemoteComponents[key] = markRaw(wrapWithErrorBoundary(module.default))
                    if(typeof module.getSubtitle === "function"){
                        subtitleResolvers[key] = module.getSubtitle
                    }
                }
            }
        }
    }

    const RemoteComponent = defineComponent({
        props: ["taskType", ...KnownSlotsPropNames[slotName]],
        inheritAttrs: false,
        setup(rawProps, {attrs}) {
            // The declared props are the slot's own propNames, so the generic slot type can't be inferred from them.
            const {taskType, ...restProps} = rawProps as unknown as {taskType: string} & KnownSlotProps[T]
            // ponytail: read inside render so shallowReactive tracks it — loadRemote completes after manifestReady
            return () => {
                const Comp = RemoteComponents[taskType]
                return Comp ? h(Comp, {...restProps, ...attrs}) : null
            }
        },
    })

    function hasResolvedComponent(taskType: string): boolean {
        return !!RemoteComponents[taskType]
    }

    function hasFailedComponent(taskType: string): boolean {
        return !!failedModules[taskType]
    }

    // A task runner's module wins only when the runner ships one, otherwise the task's own module is used.
    function componentTypeFor(task?: {type?: string; taskRunner?: {type?: string}}): string {
        const runnerType = task?.taskRunner?.type
        return runnerType && hasResolvedComponent(runnerType) ? runnerType : task?.type ?? ""
    }

    return {
        RemoteComponent,
        taskAdditionalInfoRemote,
        manifestReady,
        resolveRemoteComponent,
        hasResolvedComponent,
        hasFailedComponent,
        componentTypeFor,
        subtitleResolvers,
    }
}


