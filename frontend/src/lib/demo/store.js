import { createSeedState, executeDemo, DemoError } from './engine.js'

const DB_NAME = 'factoryflow-browser-demo'
const STORE_NAME = 'snapshot'
const KEY = 'factory'
let databasePromise

function openDatabase() {
  if (!globalThis.indexedDB) {
    return Promise.reject(new DemoError(503, 'DEMO_STORAGE_UNAVAILABLE',
      '브라우저 저장소를 사용할 수 없습니다. 보안 설정 또는 시크릿 모드를 확인해 주세요.'))
  }
  if (!databasePromise) {
    databasePromise = new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, 1)
      request.onupgradeneeded = () => {
        const store = request.result.createObjectStore(STORE_NAME, { keyPath: 'key' })
        store.put({ key: KEY, value: createSeedState() })
      }
      request.onsuccess = () => {
        const db = request.result
        db.onversionchange = () => { db.close(); databasePromise = null }
        resolve(db)
      }
      request.onerror = () => reject(request.error)
      request.onblocked = () => reject(new DemoError(503, 'DEMO_STORAGE_BLOCKED',
        '다른 브라우저 탭에서 데모 저장소가 사용 중입니다.'))
    }).catch(error => {
      databasePromise = null
      throw error
    })
  }
  return databasePromise
}

const aborted = () => new DOMException('요청이 취소되었습니다.', 'AbortError')

/**
 * One IndexedDB readwrite transaction per command. Other tabs cannot interleave
 * a second write between reading a snapshot and committing the new snapshot.
 */
export async function requestDemo(path, { method = 'GET', body, signal } = {}) {
  if (signal?.aborted) throw aborted()
  const db = await openDatabase()
  if (signal?.aborted) throw aborted()
  const write = method.toUpperCase() !== 'GET'
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, write ? 'readwrite' : 'readonly')
    const store = tx.objectStore(STORE_NAME)
    const reading = store.get(KEY)
    let value
    let thrown
    reading.onsuccess = () => {
      try {
        if (signal?.aborted) { thrown = aborted(); tx.abort(); return }
        const current = reading.result?.value ?? createSeedState()
        const result = executeDemo(current, path, { method, body })
        value = result.data
        if (result.modified) store.put({ key: KEY, value: result.state })
      } catch (error) {
        thrown = error
        tx.abort()
      }
    }
    tx.oncomplete = () => signal?.aborted ? reject(aborted()) : resolve(value)
    tx.onabort = () => reject(thrown ?? tx.error ?? new DemoError(500, 'DEMO_WRITE_ABORTED', '데모 데이터 저장이 취소되었습니다.'))
    tx.onerror = () => { /* onabort supplies the final rejection */ }
  })
}

/** Explicit user action only; never reset data on page reload or login. */
export async function resetDemoData() {
  const db = await openDatabase()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite')
    tx.objectStore(STORE_NAME).put({ key: KEY, value: createSeedState() })
    tx.oncomplete = resolve
    tx.onabort = () => reject(tx.error ?? new Error('브라우저 데모 초기화에 실패했습니다.'))
    tx.onerror = () => { /* onabort supplies the final rejection */ }
  })
}
