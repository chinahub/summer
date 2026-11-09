import { request } from '@/api/request';

/** 个人资料视图：与后端 UserVo 对应，密码等敏感字段不出现在该契约中 */
export interface ProfileVo {
  userId: number;
  deptId?: number;
  deptName?: string;
  userName: string;
  nickName?: string;
  email?: string;
  phonenumber?: string;
  sex?: string;
  avatar?: string;
  status?: string;
  loginIp?: string;
  loginDate?: string;
  createTime?: string;
  roleNames?: string[];
  postNames?: string[];
}

/** 个人资料可修改字段：白名单仅限昵称、邮箱、手机号、性别 */
export interface UpdateProfileParams {
  nickName?: string;
  email?: string;
  phonenumber?: string;
  sex?: string;
}

/** 修改密码：必须提供原密码，新密码经 BCrypt 重新编码 */
export interface ChangePasswordParams {
  oldPassword: string;
  newPassword: string;
}

/** 当前登录用户资料 */
export function getProfileApi(): Promise<ProfileVo> {
  return request<ProfileVo>({ url: '/system/profile', method: 'get' });
}

/** 更新个人资料（白名单字段） */
export function updateProfileApi(params: UpdateProfileParams): Promise<void> {
  return request<void>({ url: '/system/profile', method: 'put', data: params });
}

/** 修改密码：验证原密码正确后生效 */
export function changePasswordApi(params: ChangePasswordParams): Promise<void> {
  return request<void>({ url: '/system/profile/password', method: 'put', data: params });
}
