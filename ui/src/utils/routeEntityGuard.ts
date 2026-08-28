import type {NavigationGuard, RouteLocationNormalized, RouteRecordNormalized} from "vue-router"
import {useCoreStore} from "../stores/core"

/**
 * The not-found screen is the report, so the global interceptor keeps quiet: a toast on
 * top of that screen would say the same thing twice.
 */
export const PROBE_REQUEST_OPTIONS = {ignoreNotFound: true, showMessageOnError: false}

/** Resolves the one entity a detail page is about. A 404, or a falsy resolution, means it is missing. */
export type EntityResolver = (to: RouteLocationNormalized) => Promise<unknown>

declare module "vue-router" {
    interface RouteMeta {
        /** Declared by a detail page whose whole content is one entity; see {@link entityNotFoundGuard}. */
        entity?: EntityResolver
    }
}

/**
 * Copies the target route's tenant into an entity probe's parameters. Passing
 * `tenant: undefined` instead would blank out the SDK's own default and build a
 * request against `/api/v1/undefined/...`.
 */
export function withTenant<T extends Record<string, unknown>>(to: RouteLocationNormalized, parameters: T): T & {tenant?: string} {
    return to.params.tenant ? {...parameters, tenant: String(to.params.tenant)} : parameters
}

function resolverOf(record: RouteRecordNormalized): EntityResolver | undefined {
    return record.meta.entity
}

function hasSameParams(to: RouteLocationNormalized, from: RouteLocationNormalized): boolean {
    const keys = Object.keys(to.params)
    return keys.length === Object.keys(from.params).length
        && keys.every((key) => String(to.params[key]) === String(from.params[key]))
}

async function probe(resolve: EntityResolver, to: RouteLocationNormalized): Promise<404 | undefined> {
    try {
        return (await resolve(to)) ? undefined : 404
    } catch (error) {
        // Only a missing entity is this guard's business: any other failure is left to the page,
        // which the interceptor has already reported with a toast.
        return (error as {status?: number})?.status === 404 ? 404 : undefined
    }
}

/**
 * Resolves a detail page's entity before the page mounts, so a missing one renders the
 * not-found screen at the requested URL instead of letting the page mount and half-render
 * around data it never got. Declared per page as `meta.entity`.
 *
 * Global rather than a per-record `beforeEnter`, which vue-router skips when only the params
 * change — navigating straight from one flow to another would otherwise go unchecked.
 */
export const entityNotFoundGuard: NavigationGuard = async (to, from) => {
    const record = to.matched.find(resolverOf)

    // A tab or filter change within the same entity has nothing new to resolve.
    if (record && from.matched.includes(record) && hasSameParams(to, from)) return true

    const coreStore = useCoreStore()
    coreStore.error = record ? await probe(resolverOf(record)!, to) : undefined

    return true
}
