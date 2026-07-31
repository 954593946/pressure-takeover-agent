const assert = require("node:assert/strict");
const model = require("../src/world-state-model.js");
const agent = require("../src/agent-client.js");

const normalized = agent.normalizeConfig({
  apiBase: "http://127.0.0.1:8000/",
  pollIntervalMs: 1,
  requestTimeoutMs: 999999
});
assert.equal(normalized.apiBase, "http://127.0.0.1:8000");
assert.equal(normalized.streamUrl, "http://127.0.0.1:8000/v1/stream");
assert.equal(normalized.pollIntervalMs, 2000);
assert.equal(normalized.requestTimeoutMs, 30000);

const parsed = agent.parseSseFrames(
  "event: state.updated\r\ndata: {\"revision\":1,\r\ndata: \"ok\":true}\r\n\r\nevent: ping\ndata: keep"
);
assert.equal(parsed.events.length, 1);
assert.equal(parsed.events[0].event, "state.updated");
assert.equal(parsed.events[0].data, '{"revision":1,\n"ok":true}');
assert.equal(parsed.remainder, "event: ping\ndata: keep");

const baseState = {
  schema_version: "0.2.0",
  session_id: "session-a",
  revision: 1
};
const store = agent.createWorldStateStore(model);
const changes = [];
store.subscribe((state, metadata) => changes.push({ state, metadata }));
assert.equal(store.consume(baseState, { source: "initial" }).accepted, true);
assert.equal(store.consume({ ...baseState, revision: 1 }, { source: "duplicate" }).accepted, false);
assert.equal(store.consume({ ...baseState, revision: 2 }, { source: "stream" }).accepted, true);
assert.equal(store.consume({ ...baseState, session_id: "session-b", revision: 1 }, { source: "new_session" }).resetRequired, true);
assert.equal(store.consume({ ...baseState, revision: 99 }, { source: "late_old_request" }).reason, "retired_session");
assert.equal(changes.length, 3);
assert.deepEqual(store.getMeta().retiredSessionIds, ["session-a"]);

const storage = {
  value: "{not json",
  getItem() { return this.value; },
  setItem(_key, value) { this.value = value; }
};
const config = agent.loadConfig({ storage, search: "", globalConfig: { apiBase: "https://example.com/api" } });
assert.equal(config.apiBase, "https://example.com/api");
agent.saveConfig({ apiBase: "http://localhost:8000", token: "secret" }, storage);
assert.equal(JSON.parse(storage.value).token, "secret");

console.log("vehicle-hmi-next agent-client tests passed");
