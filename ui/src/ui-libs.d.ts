declare module "@kestra-io/ui-libs/src/utils/global" {
    export function cssVariable(name: string): string;
}

declare module "@kestra-io/ui-libs/src/utils/Utils" {
    export default class Utils {
        static getTheme(): string;
        static humanDuration(duration: number | string): string;
    }
}