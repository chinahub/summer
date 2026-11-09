import { useCallback, useEffect, useState } from 'react';
import { listDictDataByTypeApi, type DictDataVo } from '@/api/system/dict';

/** 字典缓存：类型 -> 进行中或已完成的拉取 Promise，避免并发重复请求 */
const dictCache = new Map<string, Promise<DictDataVo[]>>();

/**
 * 按类型拉取启用字典数据：进程内按类型缓存，同一类型仅请求一次；
 * 请求失败时不写入缓存，下一次调用自动重试。
 *
 * @param dictType 字典类型（如 sys_notice_type）
 * @return 该类型启用字典项，顺序与后端返回一致（按 dict_sort 升序）
 */
export function fetchDictData(dictType: string): Promise<DictDataVo[]> {
  const cached = dictCache.get(dictType);
  if (cached) {
    return cached;
  }
  const pending = listDictDataByTypeApi(dictType).catch((error: unknown) => {
    dictCache.delete(dictType);
    throw error;
  });
  dictCache.set(dictType, pending);
  return pending;
}

/** 清空字典缓存：不传类型时全量清空（字典维护变更后调用） */
export function clearDictCache(dictType?: string): void {
  if (dictType === undefined) {
    dictCache.clear();
  } else {
    dictCache.delete(dictType);
  }
}

/** useDict 返回值：各类型字典项、加载/错误态与手动刷新 */
export interface UseDictResult {
  /** 按类型索引的启用字典项 */
  dicts: Record<string, DictDataVo[]>;
  /** 首拉是否进行中 */
  loading: boolean;
  /** 首拉失败的错误（可通过 refresh 重试） */
  error: Error | null;
  /** 失败重试/缓存失效后强制重拉 */
  refresh: () => void;
}

/**
 * 字典 Hook：按类型拉取启用字典项，保持后端返回顺序；
 * 多类型并发共享缓存，失败可经 refresh 重试。
 *
 * @param dictTypes 需要的字典类型列表
 */
export function useDict(...dictTypes: string[]): UseDictResult {
  const typesKey = dictTypes.join(',');
  const [dicts, setDicts] = useState<Record<string, DictDataVo[]>>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    const types = typesKey.split(',').filter(Boolean);
    if (types.length === 0) {
      setDicts({});
      return;
    }
    let cancelled = false;
    setLoading(true);
    Promise.all(
      types.map((type) => fetchDictData(type).then((items) => [type, items] as const)),
    )
      .then((entries) => {
        if (!cancelled) {
          setDicts(Object.fromEntries(entries));
          setError(null);
        }
      })
      .catch((cause: unknown) => {
        if (!cancelled) {
          setError(cause instanceof Error ? cause : new Error('字典加载失败'));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [typesKey, tick]);

  const refresh = useCallback(() => {
    typesKey
      .split(',')
      .filter(Boolean)
      .forEach((type) => dictCache.delete(type));
    setTick((value) => value + 1);
  }, [typesKey]);

  return { dicts, loading, error, refresh };
}
