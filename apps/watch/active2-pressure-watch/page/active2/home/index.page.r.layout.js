import * as hmUI from "@zos/ui";
import { getDeviceInfo } from "@zos/device";
import { px } from "@zos/utils";

export const { width: DEVICE_WIDTH, height: DEVICE_HEIGHT } = getDeviceInfo();

const CENTER_X = px(233);
const CONTENT_X = px(52);
const CONTENT_W = DEVICE_WIDTH - CONTENT_X * 2;

export const BACKGROUND_STYLE = {
  x: 0,
  y: 0,
  w: DEVICE_WIDTH,
  h: DEVICE_HEIGHT,
  color: 0x0b1b33
};

export const STATUS_DOT_STYLE = {
  center_x: CENTER_X,
  center_y: px(58),
  radius: px(6),
  color: 0x2f6bff
};

export const HALO_STYLE = {
  center_x: CENTER_X,
  center_y: px(156),
  radius: px(76),
  color: 0x132c66
};

export const CORE_STYLE = {
  center_x: CENTER_X,
  center_y: px(156),
  radius: px(50),
  color: 0x2f6bff
};

export const ICON_STYLE = {
  x: px(153),
  y: px(123),
  w: px(160),
  h: px(66),
  color: 0xf5f2ec,
  text_size: px(30),
  align_h: hmUI.align.CENTER_H,
  align_v: hmUI.align.CENTER_V,
  text_style: hmUI.text_style.ELLIPSIS,
  text: "A"
};

export const TITLE_STYLE = {
  x: CONTENT_X,
  y: px(246),
  w: CONTENT_W,
  h: px(54),
  color: 0xf5f2ec,
  text_size: px(34),
  align_h: hmUI.align.CENTER_H,
  align_v: hmUI.align.CENTER_V,
  text_style: hmUI.text_style.ELLIPSIS,
  text: "AURI 已就绪"
};

export const SUBTITLE_STYLE = {
  x: CONTENT_X,
  y: px(300),
  w: CONTENT_W,
  h: px(52),
  color: 0xc9d4e2,
  text_size: px(23),
  align_h: hmUI.align.CENTER_H,
  align_v: hmUI.align.CENTER_V,
  text_style: hmUI.text_style.ELLIPSIS,
  text: "等待手机同步"
};

export const FOOTER_STYLE = {
  x: CONTENT_X,
  y: px(386),
  w: CONTENT_W,
  h: px(32),
  color: 0x8fa0b5,
  text_size: px(18),
  align_h: hmUI.align.CENTER_H,
  align_v: hmUI.align.CENTER_V,
  text_style: hmUI.text_style.ELLIPSIS,
  text: "AURI Active 2"
};
