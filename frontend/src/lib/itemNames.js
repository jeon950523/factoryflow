import { get } from './api.js'

/**
 * Resolves only IDs actually visible in the current table/detail.
 * 50 IDs per request avoids one HTTP request per row and long query strings.
 */
export async function lookupItemNames(ids, { signal } = {}) {
  const unique = [...new Set(ids.map(Number).filter(id => Number.isSafeInteger(id) && id > 0))]
  if (!unique.length) return {}

  const groups = []
  for (let i = 0; i < unique.length; i += 50) groups.push(unique.slice(i, i + 50))

  const pages = await Promise.all(groups.map(group =>
    get('/api/v1/items/lookup', { ids: group.join(',') }, { signal }),
  ))
  const resolved = {}
  for (const page of pages) {
    for (const item of page) resolved[item.id] = item
  }
  return resolved
}

export function itemNameOrId(map, id, fallback = '품목') {
  return map?.[id]?.name ?? (id == null ? fallback : fallback + ' #' + id)
}

export function itemSkuOrId(map, id) {
  return map?.[id]?.sku ?? (id == null ? '—' : '#' + id)
}
