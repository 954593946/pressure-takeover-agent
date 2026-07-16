import * as hmUI from "@zos/ui";
import { log as Logger } from "@zos/utils";
import { MOCK_STATE_SEQUENCE, MOCK_WATCH_STATE } from "../../../utils/mock-state";
import { normalizeWatchState } from "../../../utils/state-map";
import {
  BACKGROUND_STYLE,
  CORE_STYLE,
  FOOTER_STYLE,
  HALO_STYLE,
  STATUS_DOT_STYLE,
  SUBTITLE_STYLE,
  SWITCH_BUTTON_STYLE,
  TITLE_STYLE
} from "./index.page.r.layout.js";

const logger = Logger.getLogger("pressure-watch-home");

let stateWidgets = null;
let mockStateIndex = 0;

function applyVibration(state) {
  // Future hook: map state.vibration to @zos/sensor Vibrator scenes.
  logger.debug(`vibration reserved: ${state.vibration}`);
}

function renderState(rawState) {
  const nextState = normalizeWatchState(rawState);

  stateWidgets.dot.setProperty(hmUI.prop.MORE, {
    ...STATUS_DOT_STYLE,
    color: nextState.color
  });
  stateWidgets.halo.setProperty(hmUI.prop.MORE, {
    ...HALO_STYLE,
    color: nextState.dimColor
  });
  stateWidgets.core.setProperty(hmUI.prop.MORE, {
    ...CORE_STYLE,
    color: nextState.color
  });
  stateWidgets.title.setProperty(hmUI.prop.MORE, {
    ...TITLE_STYLE,
    text: nextState.title
  });
  stateWidgets.subtitle.setProperty(hmUI.prop.MORE, {
    ...SUBTITLE_STYLE,
    text: nextState.subtitle
  });
  stateWidgets.footer.setProperty(hmUI.prop.MORE, {
    ...FOOTER_STYLE,
    text: nextState.footer
  });

  const switchText = `切换 ${mockStateIndex + 1}/${MOCK_STATE_SEQUENCE.length}`;
  stateWidgets.switchButton.setProperty(hmUI.prop.MORE, {
    ...SWITCH_BUTTON_STYLE,
    text: switchText
  });

  getApp()._options.globalData.currentState = nextState;
  applyVibration(nextState);
}

function showNextMockState() {
  mockStateIndex = (mockStateIndex + 1) % MOCK_STATE_SEQUENCE.length;
  logger.debug(`switch mock state: ${mockStateIndex}`);
  renderState(MOCK_STATE_SEQUENCE[mockStateIndex]);
}

Page({
  onInit() {
    logger.debug("home onInit");
  },

  build() {
    logger.debug("home build");
    this.createStaticLayout();
    this.renderState(MOCK_WATCH_STATE);
  },

  createStaticLayout() {
    hmUI.createWidget(hmUI.widget.FILL_RECT, BACKGROUND_STYLE);

    stateWidgets = {
      dot: hmUI.createWidget(hmUI.widget.CIRCLE, STATUS_DOT_STYLE),
      halo: hmUI.createWidget(hmUI.widget.CIRCLE, HALO_STYLE),
      core: hmUI.createWidget(hmUI.widget.CIRCLE, CORE_STYLE),
      title: hmUI.createWidget(hmUI.widget.TEXT, TITLE_STYLE),
      subtitle: hmUI.createWidget(hmUI.widget.TEXT, SUBTITLE_STYLE),
      footer: hmUI.createWidget(hmUI.widget.TEXT, FOOTER_STYLE),
      switchHotspot: hmUI.createWidget(hmUI.widget.FILL_RECT, {
        x: SWITCH_BUTTON_STYLE.x,
        y: SWITCH_BUTTON_STYLE.y,
        w: SWITCH_BUTTON_STYLE.w,
        h: SWITCH_BUTTON_STYLE.h,
        radius: SWITCH_BUTTON_STYLE.radius,
        color: SWITCH_BUTTON_STYLE.normal_color,
        alpha: 255
      }),
      switchButton: hmUI.createWidget(hmUI.widget.BUTTON, {
        ...SWITCH_BUTTON_STYLE,
        text: `切换 ${mockStateIndex + 1}/${MOCK_STATE_SEQUENCE.length}`,
        click_func: showNextMockState,
        longpress_func: showNextMockState
      }),
      switchTouchLayer: hmUI.createWidget(hmUI.widget.FILL_RECT, {
        x: SWITCH_BUTTON_STYLE.x,
        y: SWITCH_BUTTON_STYLE.y,
        w: SWITCH_BUTTON_STYLE.w,
        h: SWITCH_BUTTON_STYLE.h,
        radius: SWITCH_BUTTON_STYLE.radius,
        color: 0xffffff,
        alpha: 1
      })
    };

    stateWidgets.switchHotspot.addEventListener(hmUI.event.CLICK_DOWN, showNextMockState);
    stateWidgets.switchTouchLayer.addEventListener(hmUI.event.CLICK_DOWN, showNextMockState);
  },

  onDestroy() {
    logger.debug("home onDestroy");
  }
});
