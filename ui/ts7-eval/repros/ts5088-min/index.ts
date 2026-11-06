type Deep<T> = T extends object ? { [K in keyof T]: Deep<T[K]> } : T;
declare function wrap<T>(value: T): { value: Deep<T> };

export type Node = { child?: Node };

export function make() {
    return wrap<Node>({});
}
