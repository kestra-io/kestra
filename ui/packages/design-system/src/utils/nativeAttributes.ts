export type NativeAttributes<T> = {[K in keyof T as K extends `on${string}` ? never : K]: T[K]}
