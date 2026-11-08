/** 统一响应体：{code,msg,data}，成功 code 固定 200 */
export interface R<T> {
  code: number;
  msg: string;
  data: T;
}

/** 分页表格响应体：{code,msg,rows,total} */
export interface TableData<T> {
  code: number;
  msg: string;
  rows: T[];
  total: number;
}

/** 前端路由元数据；perms 为前端约定的菜单/按钮权限标识 */
export interface RouterMeta {
  title?: string;
  icon?: string;
  noCache?: boolean;
  perms?: string;
}

/** 动态路由视图对象，与后端 RouterVo 对应 */
export interface RouterVo {
  name?: string;
  path: string;
  hidden?: boolean;
  redirect?: string;
  component?: string;
  query?: string;
  meta?: RouterMeta;
  children?: RouterVo[];
}

/** 登录成功响应 data，snake_case 与后端一致 */
export interface LoginResult {
  access_token: string;
  token_type: string;
  expires_in: number;
}
