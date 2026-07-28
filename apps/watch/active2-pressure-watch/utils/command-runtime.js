import { ACK_RESULTS, createAck } from "./protocol";
import { isSupportedMode, normalizeWearableCommand } from "./state-map";

const MAX_PROCESSED_COMMANDS = 50;

export function createCommandRuntime() {
  return {
    lastCommandId: "",
    lastRenderedVersion: "",
    lastHapticCommandId: "",
    lastAck: null,
    lastMessageAt: 0,
    processedCommandIds: []
  };
}

function rememberCommand(runtime, commandId) {
  runtime.processedCommandIds.push(commandId);

  if (runtime.processedCommandIds.length > MAX_PROCESSED_COMMANDS) {
    runtime.processedCommandIds.shift();
  }
}

export function processSetState(runtime, rawCommand, execute) {
  const commandId = rawCommand && (rawCommand.command_id || rawCommand.commandId);

  runtime.lastMessageAt = Date.now();

  if (!commandId) {
    runtime.lastAck = createAck("", ACK_RESULTS.ERROR, "missing command_id");
    return { ack: runtime.lastAck, command: null, executed: false };
  }

  if (runtime.processedCommandIds.indexOf(commandId) >= 0) {
    runtime.lastAck = createAck(commandId, ACK_RESULTS.DUPLICATE);
    return { ack: runtime.lastAck, command: null, executed: false };
  }

  if (rawCommand.mode && !isSupportedMode(rawCommand.mode)) {
    runtime.lastAck = createAck(commandId, ACK_RESULTS.UNSUPPORTED, "unsupported mode");
    rememberCommand(runtime, commandId);
    return { ack: runtime.lastAck, command: null, executed: false };
  }

  const command = normalizeWearableCommand(rawCommand);

  try {
    execute(command);
    runtime.lastCommandId = commandId;
    runtime.lastRenderedVersion = `${command.mode}:${command.version}:${command.text}`;
    rememberCommand(runtime, commandId);
    runtime.lastAck = createAck(commandId, ACK_RESULTS.OK);
    return { ack: runtime.lastAck, command, executed: true };
  } catch (error) {
    runtime.lastAck = createAck(commandId, ACK_RESULTS.ERROR, error && error.message ? error.message : "execute failed");
    return { ack: runtime.lastAck, command, executed: false };
  }
}
