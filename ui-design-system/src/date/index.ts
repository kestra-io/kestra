 
type MomentFn = (date?: any) => any
type DateFormatterFn = (date: string | Date, format?: string) => string

let _momentInstance: MomentFn | null = null
let _dateFormatter: DateFormatterFn | null = null

export function setMomentInstance(momentFn: MomentFn): void {
    _momentInstance = momentFn
}

export function setDateFormatter(fn: DateFormatterFn): void {
    _dateFormatter = fn
}

export function getMomentInstance(): MomentFn | null {
    return _momentInstance
}

export function getDateFormatter(): DateFormatterFn | null {
    return _dateFormatter
}
