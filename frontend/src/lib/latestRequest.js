/**
 * Single-owner GET requests: a newer user action invalidates the previous response.
 * Abort saves bandwidth; isCurrent also guards results already resolved when abort happened.
 */
export function createLatestRequest() {
  let revision = 0
  let controller = null

  return {
    begin() {
      revision += 1
      controller?.abort()
      controller = new AbortController()
      const expected = revision
      return { signal: controller.signal, isCurrent: () => expected === revision }
    },
    cancel() {
      revision += 1
      controller?.abort()
      controller = null
    },
  }
}

export const isAbortError = error => error?.name === 'AbortError'
