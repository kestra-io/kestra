import type {NavigationGuard, RouteLocationNormalized, RouteRecordNormalized} from "vue-router"
import {useCoreStore} from "../stores/core"
import type {KestraRequestOptions} from "./kestraHttp"

/**
 * A 404 is what this guard is looking for and it reports it as the not-found screen, so the
 * interceptor keeps quiet rather than toasting the same thing twice. Every other failure is
 * left to toast as usual.
 */
export const ENTITY_REQUEST_OPTIONS: KestraRequestOptions = {ignoreNotFound: true}

/** Resolves the one entity a detail page is about. A 404, or a falsy resolution, means it is missing. */
export type EntityResolver = (to: RouteLocationNormalized) => Promise<unknown>

declare module "vue-router" {
    interface RouteMeta {
        /** Declared by a detail page whose whole content is one entity; see {@link entityNotFoundGuard}. */
        entity?: EntityResolver
    }
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
    let error: unknown
    try {
        if (await resolve(to)) return undefined
    } catch (thrown) {
        // Only a missing entity is this guard's business: any other failure is left to the page,
        // which the interceptor has already reported with a toast.
        if ((thrown as {status?: number})?.status !== 404) return undefined
        error = thrown
    }

    // The not-found screen is the only report here, and it does not say which entity is missing.
    console.error(`No entity behind ${to.fullPath}, showing the not-found screen instead.`, error)

    return 404
}

let latestNavigation = 0

/**
 * Resolves a detail page's entity before the page mounts, so a missing one renders the
 * not-found screen at the requested URL instead of letting the page mount and half-render
 * around data it never got. Declared per page as `meta.entity`, which resolves through the same
 * store loader the page itself uses, so the entity is fetched once and the page renders from
 * what the guard put in the store.
 *
 * Global rather than a per-record `beforeEnter`, which vue-router skips when only the params
 * change — navigating straight from one flow to another would otherwise go unchecked.
 */
export const entityNotFoundGuard: NavigationGuard = async (to, from) => {
    // Before the early return below: a navigation that resolves nothing still invalidates one that
    // is resolving, or a tab change would let an older flow's verdict land on the current page.
    const navigation = ++latestNavigation
    const record = to.matched.find(resolverOf)

    // A tab or filter change within the same entity has nothing new to resolve.
    if (record && from.matched.includes(record) && hasSameParams(to, from)) return true

    const verdict = record ? await probe(resolverOf(record)!, to) : undefined

    // The user has navigated again while this one was resolving, so its verdict is about a page
    // they are no longer on: applying it would show the not-found screen over the newer one.
    if (navigation !== latestNavigation) return true

    useCoreStore().error = verdict

    return true
}
