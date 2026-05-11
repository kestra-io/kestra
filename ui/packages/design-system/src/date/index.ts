 
type MomentFn = (date?: any) => any
type DateFormatterFn = (date: string | Date, format?: string) => string

let momentInstance: MomentFn | null = null
let dateFormatter: DateFormatterFn | null = null

export function setMomentInstance(momentFn: MomentFn): void {
    momentInstance = momentFn
}

export function setDateFormatter(fn: DateFormatterFn): void {
    dateFormatter = fn
}

export function getMomentInstance(): MomentFn | null {
    return momentInstance
}

export function getDateFormatter(): DateFormatterFn | null {
    return dateFormatter
}
