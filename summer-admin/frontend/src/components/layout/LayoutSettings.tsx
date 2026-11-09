import { BgColorsOutlined } from '@ant-design/icons';
import { Button, ColorPicker, Divider, Drawer, Radio, Switch, Tooltip } from 'antd';
import { useState } from 'react';
import { useAppStore, type LayoutMode } from '@/stores/appStore';

/** 预设主题色（同 antd 常用品牌色板） */
const PRESET_THEME_COLORS = [
  '#1677ff',
  '#722ed1',
  '#13c2c2',
  '#52c41a',
  '#fa8c16',
  '#eb2f96',
  '#f5222d',
];

const LAYOUT_OPTIONS: Array<{ value: LayoutMode; label: string }> = [
  { value: 'side', label: '侧边布局' },
  { value: 'top', label: '顶部布局' },
  { value: 'mix', label: '混合布局' },
];

/** 布局设置抽屉：主题色、明暗模式与 side/top/mix 布局切换 */
export default function LayoutSettings() {
  const [open, setOpen] = useState(false);
  const themeColor = useAppStore((state) => state.themeColor);
  const dark = useAppStore((state) => state.dark);
  const layout = useAppStore((state) => state.layout);
  const setThemeColor = useAppStore((state) => state.setThemeColor);
  const setDark = useAppStore((state) => state.setDark);
  const setLayout = useAppStore((state) => state.setLayout);

  return (
    <>
      <Tooltip title="布局设置">
        <Button type="text" icon={<BgColorsOutlined />} onClick={() => setOpen(true)} />
      </Tooltip>
      <Drawer title="布局设置" open={open} onClose={() => setOpen(false)} width={320}>
        <Divider titlePlacement="left" plain>
          主题色
        </Divider>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {PRESET_THEME_COLORS.map((color) => (
            <Tooltip key={color} title={color}>
              <button
                type="button"
                aria-label={`主题色 ${color}`}
                onClick={() => setThemeColor(color)}
                style={{
                  width: 24,
                  height: 24,
                  borderRadius: 4,
                  border: themeColor === color ? '2px solid #000' : 'none',
                  background: color,
                  cursor: 'pointer',
                }}
              />
            </Tooltip>
          ))}
          <ColorPicker value={themeColor} onChange={(value) => setThemeColor(value.toHexString())} />
        </div>
        <Divider titlePlacement="left" plain>
          明暗模式
        </Divider>
        <Switch checked={dark} checkedChildren="暗色" unCheckedChildren="亮色" onChange={setDark} />
        <Divider titlePlacement="left" plain>
          布局模式
        </Divider>
        <Radio.Group value={layout} onChange={(e) => setLayout(e.target.value as LayoutMode)}>
          {LAYOUT_OPTIONS.map((option) => (
            <Radio.Button key={option.value} value={option.value}>
              {option.label}
            </Radio.Button>
          ))}
        </Radio.Group>
      </Drawer>
    </>
  );
}
