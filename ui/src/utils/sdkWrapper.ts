/**
 * SDK Wrapper - Makes `tenant` parameter optional in hey-api generated SDK methods
 * 
 * This wrapper creates proxy functions that automatically inject a default tenant value
 * while preserving all TypeScript types from the generated SDK.
 */

import * as sdk from "../generated/kestra-api/sdk.gen";
import type {Options} from "../generated/kestra-api/sdk.gen";

// Type utility: Check if an object type has only the 'tenant' key
type HasOnlyTenant<T> = keyof Omit<T, "tenant"> extends never ? true : false;

// Type utility: Make the `tenant` property optional if it exists in the parameters
type MakeTenantOptional<T> = T extends {tenant: string}
    ? Omit<T, "tenant"> & {tenant?: string}
    : T;

// Type for the tenant provider function
export type TenantProvider = () => string;

// Default tenant provider - can be overridden
let tenantProvider: TenantProvider = () => "main";

/**
 * Set the function that provides the default tenant value.
 * Call this once during app initialization.
 * 
 * @example
 * // In your app setup:
 * import { setTenantProvider } from './utils/sdkWrapper';
 * import { useRoute } from 'vue-router';
 * 
 * setTenantProvider(() => {
 *   const route = useRoute();
 *   return (route.params.tenant as string) || 'main';
 * });
 */
export function setTenantProvider(provider: TenantProvider): void {
    tenantProvider = provider;
}

/**
 * Get the current tenant value from the provider
 */
export function getCurrentTenant(): string {
    return tenantProvider();
}

/**
 * Creates a wrapped version of an SDK class where all methods have tenant made optional.
 * Methods that already don't require tenant are passed through unchanged.
 * If tenant is the only required param, the entire params object becomes optional.
 */
function createWrappedClass<T>(sdkClass: T): T {
    const wrapped = {} as T;
    
    for (const key of Object.getOwnPropertyNames(sdkClass)) {
        const method = (sdkClass as Record<string, unknown>)[key];
        if (typeof method === "function") {
            // We wrap all methods that take a parameters object as first arg
            (wrapped as Record<string, unknown>)[key] = function(...args: unknown[]) {
                // If no arguments provided or first arg is undefined, create params with just tenant
                if (args.length === 0 || args[0] === undefined) {
                    args[0] = {tenant: tenantProvider()};
                } else if (typeof args[0] === "object" && args[0] !== null) {
                    // If first arg is an object, inject tenant if not provided
                    const params = args[0] as Record<string, unknown>;
                    if (!("tenant" in params) || params.tenant === undefined) {
                        args[0] = {...params, tenant: tenantProvider()};
                    }
                }
                return (method as (...args: unknown[]) => unknown).apply(sdkClass, args);
            };
        } else {
            (wrapped as Record<string, unknown>)[key] = method;
        }
    }
    
    return wrapped;
}

// ============================================================================
// Type definitions for wrapped SDK classes
// ============================================================================

import type {AxiosError, AxiosResponse} from "axios";

// Helper type to extract data type from a Promise<AxiosResponse<T>> or similar
type ExtractDataType<R> = R extends Promise<infer U>
    ? U extends AxiosResponse<infer D>
        ? D
        : U extends {data: infer D}
            ? D
            : unknown
    : unknown;

// Helper type to extract error type from a Promise<AxiosError<E>> or similar  
type ExtractErrorType<R> = R extends Promise<infer U>
    ? U extends AxiosError<infer E>
        ? E
        : U extends {error: infer E}
            ? E
            : unknown
    : unknown;

// Create a properly discriminated union result type for SDK responses
// This ensures error property is always present for type narrowing
type SdkResult<TData, TError = unknown> = Promise<
    | (AxiosResponse<TData> & {error: undefined})
    | (AxiosError<TError> & {data: undefined; error: TError})
>;

// Helper type to fix the return type to always have proper error discrimination
type FixReturnType<R> = SdkResult<ExtractDataType<R>, ExtractErrorType<R>>;

// Helper type to transform a method signature, making tenant optional in parameters
// If tenant is the only param, make the entire params object optional
// Fixes the return type to always have proper error discrimination
type WrapMethodSignature<T> = T extends (params: infer P, options?: infer O) => infer R
    ? P extends {tenant: string}
        ? HasOnlyTenant<P> extends true
            // tenant is the only param - make params optional
            ? (params?: MakeTenantOptional<P>, options?: O) => FixReturnType<R>
            // tenant + other params - keep params required but tenant optional
            : (params: MakeTenantOptional<P>, options?: O) => FixReturnType<R>
        // no tenant in params - pass through unchanged
        : (params: P, options?: O) => FixReturnType<R>
    : T extends (options?: infer O) => infer R
        ? (options?: O) => FixReturnType<R>
        : T;

// Transform all static methods of a class to have optional tenant
type WrapClass<T> = {
    [K in keyof T]: WrapMethodSignature<T[K]>;
};

// ============================================================================
// Wrapped SDK exports with proper types
// ============================================================================

// We use type assertions here because at runtime we inject the tenant,
// but TypeScript can't verify that the wrapper adds the missing property.
// The runtime behavior is correct - tenant will always be present when the SDK method is called.

export const Misc = createWrappedClass(sdk.Misc) as unknown as WrapClass<typeof sdk.Misc>;
export const Ai = createWrappedClass(sdk.Ai) as unknown as WrapClass<typeof sdk.Ai>;
export const Plugins = createWrappedClass(sdk.Plugins) as unknown as WrapClass<typeof sdk.Plugins>;
export const Blueprints = createWrappedClass(sdk.Blueprints) as unknown as WrapClass<typeof sdk.Blueprints>;
export const BlueprintTags = createWrappedClass(sdk.BlueprintTags) as unknown as WrapClass<typeof sdk.BlueprintTags>;
export const Dashboards = createWrappedClass(sdk.Dashboards) as unknown as WrapClass<typeof sdk.Dashboards>;
export const Executions = createWrappedClass(sdk.Executions) as unknown as WrapClass<typeof sdk.Executions>;
export const Files = createWrappedClass(sdk.Files) as unknown as WrapClass<typeof sdk.Files>;
export const Flows = createWrappedClass(sdk.Flows) as unknown as WrapClass<typeof sdk.Flows>;
export const Kv = createWrappedClass(sdk.Kv) as unknown as WrapClass<typeof sdk.Kv>;
export const Logs = createWrappedClass(sdk.Logs) as unknown as WrapClass<typeof sdk.Logs>;
export const Metrics = createWrappedClass(sdk.Metrics) as unknown as WrapClass<typeof sdk.Metrics>;
export const Namespaces = createWrappedClass(sdk.Namespaces) as unknown as WrapClass<typeof sdk.Namespaces>;
export const Secrets = createWrappedClass(sdk.Secrets) as unknown as WrapClass<typeof sdk.Secrets>;
export const Services = createWrappedClass(sdk.Services) as unknown as WrapClass<typeof sdk.Services>;
export const Triggers = createWrappedClass(sdk.Triggers) as unknown as WrapClass<typeof sdk.Triggers>;

// Re-export the Options type for convenience
export type {Options};

// Create a single wrapped SDK object for convenience
export const wrappedSdk = {
    Misc,
    Ai,
    Plugins,
    Blueprints,
    BlueprintTags,
    Dashboards,
    Executions,
    Files,
    Flows,
    Kv,
    Logs,
    Metrics,
    Namespaces,
    Secrets,
    Services,
    Triggers,
} as const;

export default wrappedSdk;

