import * as hmUI from "@zos/ui";
import { getDeviceInfo } from "@zos/device";
import { px } from "@zos/utils";

export const { width: DEVICE_WIDTH, height: DEVICE_HEIGHT } = getDeviceInfo();

const CONTENT_X = px(58);
const CONTENT_W = DEVICE_WIDTH - CONTENT_X * 2;

export const BACKGROUND_STYLE = {
  x: 0,
  y: 0,
  w: DEVICE_WIDTH,
  h: DEVICE_HEIGHT,
  color: 0x05070b
};

export const STATUS_DOT_STYLE = {
  center_x: px(233),
  center_y: px(96),
  radius: px(10),
  color: 0x2f80ff
};

export const HALO_STYLE = {
  center_x: px(233),
  center_y: px(196),
  radius: px(74),
  color: 0x14345f
};

export const CORE_STYLE = {
  center_x: px(233),
  center_y: px(196),
  radius: px(48),
  color: 0x2f80ff
};

export const TITLE_STYLE = {
  x: CONTENT_X,
  y: px(268),
  w: CONTENT_W,
  h: px(54),
  color: 0xffffff,
  text_size: px(34),
  align_h: hmUI.align.CENTER_H,
  align_v: hmUI.align.CENTER_V,
  text_style: hmUI.text_style.ELLIPSIS,
  text: "EMO 已就绪"
};

export const SUBTITLE_STYLE = {
  x: CONTENT_X,
  y: px(324),
  w: CONTENT_W,
  h: px(58),
  color: 0xc9d4e2,
  text_size: px(24),
  align_h: hmUI.align.CENTER_H,
  align_v: hmUI.align.CENTER_V,
  text_style: hmUI.text_style.WRAP,
  text: "等待任务同步"
};

export const FOOTER_STYLE = {
  x: CONTENT_X,
  y: px(416),
  w: CONTENT_W,
  h: px(34),
  color: 0x6f7a88,
  text_size: px(18),
  align_h: hmUI.align.CENTER_H,
  align_v: hmUI.align.CENTER_V,
  text_style: hmUI.text_style.ELLIPSIS,
  text: "Active 2 Demo"
};

export const SWITCH_BUTTON_STYLE = {
  x: px(166),
  y: px(376),
  w: px(134),
  h: px(42),
  radius: px(21),
  normal_color: 0x1d2f48,
  press_color: 0x2f80ff,
  color: 0xffffff,
  text_size: px(20),
  text: "切换"
};
