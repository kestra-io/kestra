const MINIMUM: number = 1_000_000
const MAXIMUM: number = 9_999_999

const getRandomNumber = (minimum: number = MINIMUM, maximum: number = MAXIMUM): number => Math.floor(Math.random() * (maximum - minimum + 1)) + minimum

export const getRandomID = (): string => `flow_${getRandomNumber()}`
