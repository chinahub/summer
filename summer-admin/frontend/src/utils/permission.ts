/** 超级管理员通配权限标识，拥有全部权限 */
export const SUPER_ADMIN_PERMISSION = '*:*:*';

/** 判断当前权限集合是否满足所需权限；need 为空表示无需权限 */
export function hasPermission(need: string | string[] | undefined | null, permissions: string[]): boolean {
  if (!need || (Array.isArray(need) && need.length === 0)) {
    return true;
  }
  if (permissions.includes(SUPER_ADMIN_PERMISSION)) {
    return true;
  }
  const required = Array.isArray(need) ? need : [need];
  return required.every((perm) => permissions.includes(perm));
}
