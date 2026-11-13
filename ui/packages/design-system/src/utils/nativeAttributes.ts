/** HTML attributes a wrapper forwards through `$attrs`, minus the `on*` listeners, which belong to the wrapper's own events. */
export type NativeAttributes<T> = {[K in keyof T as K extends `on${string}` ? never : K]: T[K]}
