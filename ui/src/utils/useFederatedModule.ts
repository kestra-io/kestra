import {ref, shallowReactive, markRaw} from "vue";
import {apiUrlWithoutTenants} from "override/utils/route";
import {useClient} from "@kestra-io/kestra-sdk"
import {loadRemote, registerRemotes, registerShared} from "@module-federation/enhanced/runtime";

function addCSSLinkIfNotAlreadyPresent(href: string) {
        if (!document.querySelector(`link[href="${href}"]`)) {
            const link = document.createElement("link");
            link.rel = "stylesheet";
            link.href = href;
            document.head.appendChild(link);
        }
    }

export function useFederatedModule(slotName: string) {

    const RemoteComponents = shallowReactive<Record<string, any>>({});
    const taskAdditionalInfoRemote = ref<Record<string, any>>({});

    const axios = useClient();

    const manifestReady = ref(false);

    
    async function resolveRemoteComponent(taskTypes: {cls: string, version: string | null}[]) {
        // get the manifest of the all the tasks we will 
        // have in the graph
        const pluginTaskManifestsResponse = await axios.post<{
            manifest:Record<string, {
                group: string;
                uiModule: string;
                staticInfo?: Record<string, any>;
                styles?: string[];
            }[]>
        }>(`${apiUrlWithoutTenants()}/plugins/pluginUiManifest`, Array.from(taskTypes));
    
        const pluginTaskManifests = pluginTaskManifestsResponse.data.manifest;

        for(const taskTypeKey in pluginTaskManifests){
            for(const manifest of pluginTaskManifests[taskTypeKey]){
                if(manifest.uiModule === slotName){
                    if(manifest.staticInfo){
                        taskAdditionalInfoRemote.value[taskTypeKey] = manifest.staticInfo
                    }
                }
            }
        }

        manifestReady.value = true;

        for(const taskTypeKey in pluginTaskManifests){
            for(const manifest of pluginTaskManifests[taskTypeKey]){
                if(manifest.uiModule === slotName){
                    const remoteName = `remote--${taskTypeKey}`;
                    const basePath = `${apiUrlWithoutTenants()}/plugins/${manifest.group}/pluginUi/`

                    if(manifest.styles){
                        manifest.styles.forEach((style) => addCSSLinkIfNotAlreadyPresent(`${basePath}${style}`));
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
                    ]);

                    registerShared({
                        vue: {
                            shareConfig: {
                                requiredVersion: "^3",
                                singleton: true,
                            },
                        },
                    });

                    const taskRoot = taskTypeKey.slice(manifest.group.length + 1);
                    const module = await loadRemote<{default: any}>(`${remoteName}/${taskRoot}/${slotName}`)

                    if(!module){
                        console.error(`Remote module ${remoteName} did not load correctly`);
                        return;
                    }
                    
                    RemoteComponents[taskTypeKey] = markRaw(module.default);
                }
            }
        }
    }

    return {
        RemoteComponents,
        taskAdditionalInfoRemote,
        manifestReady,
        resolveRemoteComponent
    }
}