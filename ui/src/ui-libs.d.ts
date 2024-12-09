/* eslint-disable no-unused-vars */
declare module "@kestra-io/ui-libs/src/utils/global" {
    // eslint-disable-next-line no-unused-vars
    export function cssVariable(name: string): string;
}

declare module "@kestra-io/ui-libs/src/utils/Utils" {
    export default class Utils {
        static getTheme(): string;
        static humanDuration(duration: number | string): string;
    }
}