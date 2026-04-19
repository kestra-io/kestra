import {client} from "kestra-api/client.gen"
import {configureAxios as _configureAxios} from "@kestra-io/api-client"

export {useAxios} from "@kestra-io/api-client"

type ConfigureAxiosArgs = Parameters<typeof _configureAxios>

/**
 * Wraps configureAxios from @kestra-io/api-client to also wire the generated
 * kestra-api OpenAPI client to the same axios instance.
 */
export function configureAxios(
    callback: ConfigureAxiosArgs[0],
    ...args: ConfigureAxiosArgs extends [any, ...infer R] ? R : never
): void {
    _configureAxios((instance) => {
        client.setConfig({axios: instance})
        callback(instance)
    }, ...args)
}
