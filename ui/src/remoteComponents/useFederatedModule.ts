import {ref, shallowReactive, markRaw, defineComponent, h, onErrorCaptured} from "vue"
import {apiUrlWithoutTenants} from "override/utils/route"
import {loadRemote, registerRemotes, registerShared} from "@module-federation/enhanced/runtime"
import * as PluginsAPI from "@kestra-io/kestra-sdk/plugins"
import {KnownSlotsPropNames, ManifestsRegistry, type KnownSlotProps} from "@kestra-io/slot-contracts"
import {PluginUiModuleWithGroup} from "@kestra-io/kestra-sdk"


function wrapWithErrorBoundary(inner: any) {
    return defineComponent({
        name: "FederatedModuleBoundary",
        inheritAttrs: false,
        setup(_, {attrs, slots}) {
            const error = ref<Error | null>(null)

            onErrorCaptured((err: Error) => {
                error.value = err
                console.error("[FederatedModule] Error caught by boundary:", err)
                return false
            })

            return () => {
                if (error.value) {
                    return h("div", {class: "federated-module-error"}, "A plugin component failed to load.")
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

export function useFederatedModule<T extends keyof typeof KnownSlotsPropNames>(slotName: T) {

    const RemoteComponents = shallowReactive<Record<string, any>>({})
    const taskAdditionalInfoRemote = ref<Record<string, ManifestsRegistry[T]>>({})

    const manifestReady = ref(false)

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
            const pluginTaskManifestsResponse = await PluginsAPI.pluginUiManifest({
                body: Array.from(taskTypes),
            })
            pluginTaskManifests = pluginTaskManifestsResponse?.manifest ?? {}
        } catch (error) {
            console.error("[FederatedModule] Failed to load plugin UI manifest:", error)
        } finally {
            manifestReady.value = true
        }

        for(const taskTypeKey in pluginTaskManifests){
            for(const manifest of pluginTaskManifests[taskTypeKey]){
                if(manifest.uiModule === slotName){
                    if(manifest.staticInfo){
                        taskAdditionalInfoRemote.value[taskTypeKey] = manifest.staticInfo
                    }
                }
            }
        }

        for(const taskTypeKey in pluginTaskManifests){
            for(const manifest of pluginTaskManifests[taskTypeKey]){
                if(manifest.uiModule === slotName){
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
                    const remoteId = `${remoteName}/${taskRoot}/${slotName}`
                    console.warn(`[FederatedModule] loadRemote start: "${remoteId}"`)
                    let module: {default: any} | null = null
                    try {
                        module = await loadRemote<{default: any}>(remoteId)
                    } catch(err) {
                        console.error(`[FederatedModule] loadRemote FAILED for "${remoteId}":`, err)
                        continue
                    }

                    if(!module){
                        console.error(`[FederatedModule] loadRemote returned null for "${remoteId}"`)
                        continue
                    }

                    console.warn(`[FederatedModule] loadRemote OK: "${remoteId}", default=`, module.default)
                    RemoteComponents[taskTypeKey] = markRaw(wrapWithErrorBoundary(module.default))
                }
            }
        }
    }

    const RemoteComponent = defineComponent({
        props: ["taskType", ...KnownSlotsPropNames[slotName]],
        inheritAttrs: false,
        setup(props: { taskType: string } & KnownSlotProps[T], {attrs}) {
            const {taskType, ...restProps} = props
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

    return {
        RemoteComponent,
        taskAdditionalInfoRemote,
        manifestReady,
        resolveRemoteComponent,
        hasResolvedComponent,
    }
}


