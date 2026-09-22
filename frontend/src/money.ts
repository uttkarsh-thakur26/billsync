/**
 * Money arithmetic in whole paise so the UI never touches a floating-point rupee.
 * The same helpers serve percentages, where 100% is 10000 hundredths.
 */

/** "33.34" -> 3334. null when the text is not a plain non-negative number with at most two decimals. */
export function toHundredths(text: string): number | null {
  const match = /^\s*(\d+)(?:\.(\d{1,2}))?\s*$/.exec(text)
  if (!match) return null
  return Number(match[1]) * 100 + Number((match[2] ?? '').padEnd(2, '0'))
}

/** 3334 -> "33.34", -333 -> "-3.33". The string form the API expects. */
export function fromHundredths(value: number): string {
  const abs = Math.abs(value)
  return `${value < 0 ? '-' : ''}${Math.floor(abs / 100)}.${String(abs % 100).padStart(2, '0')}`
}

/** "33.34" -> "₹33.34", "-3.33" -> "-₹3.33". Takes the API's string form as-is. */
export function rupees(money: string): string {
  return money.startsWith('-') ? `-₹${money.slice(1)}` : `₹${money}`
}

/** -1, 0 or 1 for an API money string. */
export function sign(money: string): -1 | 0 | 1 {
  if (money.startsWith('-')) return -1
  return toHundredths(money) === 0 ? 0 : 1
}
