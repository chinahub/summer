import { FullscreenExitOutlined, FullscreenOutlined } from '@ant-design/icons';
import { Button, Tooltip } from 'antd';
import { useCallback, useEffect, useState } from 'react';

/** 全屏切换：监听浏览器全屏状态变化保持图标同步 */
export default function Screenfull() {
  const [isFullscreen, setIsFullscreen] = useState(false);

  useEffect(() => {
    const sync = () => setIsFullscreen(!!document.fullscreenElement);
    document.addEventListener('fullscreenchange', sync);
    return () => document.removeEventListener('fullscreenchange', sync);
  }, []);

  /** 进入或退出全屏，失败时静默忽略（浏览器可能拒绝） */
  const toggle = useCallback(() => {
    if (document.fullscreenElement) {
      document.exitFullscreen().catch(() => undefined);
    } else {
      document.documentElement.requestFullscreen().catch(() => undefined);
    }
  }, []);

  return (
    <Tooltip title={isFullscreen ? '退出全屏' : '全屏'}>
      <Button
        type="text"
        icon={isFullscreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />}
        onClick={toggle}
      />
    </Tooltip>
  );
}
