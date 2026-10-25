import { describe, expect, it } from 'vitest'
import { addDays } from 'date-fns'
import { formatBytes, formatDue, greeting, initials, toISODate } from './utils'

describe('utils', () => {
  it('builds initials', () => {
    expect(initials('Ava Chen')).toBe('AC')
    expect(initials('Cher')).toBe('C')
    expect(initials('  mary jane watson ')).toBe('MW')
  })
  it('formats relative due dates', () => {
    expect(formatDue(null)).toBe('')
    expect(formatDue(toISODate(new Date()))).toBe('Today')
    expect(formatDue(toISODate(addDays(new Date(), 1)))).toBe('Tomorrow')
  })
  it('formats bytes and greetings', () => {
    expect(formatBytes(512)).toBe('512 B')
    expect(formatBytes(2048)).toBe('2.0 KB')
    expect(greeting(new Date('2026-01-01T09:00:00'))).toBe('Good morning')
    expect(greeting(new Date('2026-01-01T20:00:00'))).toBe('Good evening')
  })
})
