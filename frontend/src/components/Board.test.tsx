import { describe, expect, it } from 'vitest'
import { task } from '@/test/fixtures'
import { groupByStatus } from './Board'

describe('groupByStatus', () => {
  it('splits tasks into the four columns preserving server order', () => {
    const cols = groupByStatus([
      task({ id: 'a', status: 'TODO' }),
      task({ id: 'b', status: 'DONE' }),
      task({ id: 'c', status: 'TODO' }),
    ])
    expect(cols.TODO.map((t) => t.id)).toEqual(['a', 'c'])
    expect(cols.DONE.map((t) => t.id)).toEqual(['b'])
    expect(cols.BACKLOG).toEqual([])
    expect(cols.IN_PROGRESS).toEqual([])
  })
})
