import {ref, shallowReactive, markRaw, defineComponent, h, onErrorCaptured} from "vue"
import {apiUrlWithoutTenants} from "override/utils/route"
import {loadRemote, registerRemotes, registerShared} from "@module-federation/enhanced/runtime"
import * as PluginsAPI from "@kestra-io/kestra-sdk/plugins"

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

export function useFederatedModule(slotName: string) {

    const RemoteComponents = shallowReactive<Record<string, any>>({})
    const taskAdditionalInfoRemote = ref<Record<string, any>>({})

    const manifestReady = ref(false)

    
    async function resolveRemoteComponent(taskTypes: {cls: string, version: string | undefined}[]) {
        let pluginTaskManifests: Record<string, any[]> = {}
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

                    registerRemotes([
                        {
                            type: "module",
                            name: remoteName,
                            // FIXME: avoid caching by always providing a new url, 
                            // we need to store the hash of the dist folder in the manifest 
                            // and use it here instead of a random string
                            entry: `${basePath}plugin-ui.js?${Math.random().toString(36).substring(7)}`,
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
                    const module = await loadRemote<{default: any}>(`${remoteName}/${taskRoot}/${slotName}`)

                    if(!module){
                        console.error(`Remote module ${remoteName} did not load correctly`)
                        continue
                    }
                    
                    RemoteComponents[taskTypeKey] = markRaw(wrapWithErrorBoundary(module.default))
                }
            }
        }
    }

    return {
        RemoteComponents,
        taskAdditionalInfoRemote,
        manifestReady,
        resolveRemoteComponent,
    }
}
