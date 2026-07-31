const assert = require("node:assert/strict");
const model = require("../src/world-state-model.js");
const agent = require("../src/agent-client.js");

async function main() {
  const normalized = agent.normalizeConfig({
    apiBase: "http://127.0.0.1:8000/",
    pollIntervalMs: 1,
    requestTimeoutMs: 999999,
    mapProvider: "amap",
    amapKey: "  web-key  ",
    amapSecurityJsCode: "  security-code  ",
    amapServiceHost: "https://example.com/_AMapService/",
    amapStyle: "amap://styles/whitesmoke",
    amapMonthlyMapLimit: 0,
    amapMonthlyRouteLimit: 99999
  });
  assert.equal(normalized.apiBase, "http://127.0.0.1:8000");
  assert.equal(normalized.streamUrl, "http://127.0.0.1:8000/v1/stream");
  assert.equal(normalized.pollIntervalMs, 2000);
  assert.equal(normalized.requestTimeoutMs, 30000);
  assert.equal(normalized.mapProvider, "amap");
  assert.equal(normalized.amapKey, "web-key");
  assert.equal(normalized.amapSecurityJsCode, "security-code");
  assert.equal(normalized.amapServiceHost, "https://example.com/_AMapService");
  assert.equal(normalized.amapStyle, "amap://styles/whitesmoke");
  assert.equal(normalized.amapMonthlyMapLimit, 1);
  assert.equal(normalized.amapMonthlyRouteLimit, 10000);

  const invalidMapConfig = agent.normalizeConfig({
    mapProvider: "unknown",
    amapServiceHost: "javascript:alert(1)",
    amapMonthlyMapLimit: "invalid",
    amapMonthlyRouteLimit: null
  });
  assert.equal(invalidMapConfig.mapProvider, "auto");
  assert.equal(invalidMapConfig.amapServiceHost, "");
  assert.equal(invalidMapConfig.amapMonthlyMapLimit, 200);
  assert.equal(invalidMapConfig.amapMonthlyRouteLimit, 1);

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
  const config = agent.loadConfig({
    storage,
    search: "",
    globalConfig: {
      apiBase: "https://example.com/api",
      mapProvider: "offline",
      amapKey: "global-key"
    }
  });
  assert.equal(config.apiBase, "https://example.com/api");
  assert.equal(config.mapProvider, "offline");
  assert.equal(config.amapKey, "global-key");
  agent.saveConfig({
    apiBase: "http://localhost:8000",
    token: "secret",
    mapProvider: "amap",
    amapKey: "saved-key"
  }, storage);
  const saved = JSON.parse(storage.value);
  assert.equal(saved.token, "secret");
  assert.equal(saved.mapProvider, "amap");
  assert.equal(saved.amapKey, "saved-key");

  const requests = [];
  const client = agent.createClient({
    config: {
      apiBase: "https://agent.example.test/",
      token: "team-token",
      requestTimeoutMs: 3000
    },
    fetchImpl: async (url, options) => {
      requests.push({ url, options });
      return {
        ok: true,
        status: 200,
        headers: { get: () => "application/json; charset=utf-8" },
        text: async () => JSON.stringify({ enabled: true, provider: "amap" })
      };
    }
  });
  assert.equal(typeof client.requestJson, "function");
  const mapConfig = await client.requestJson("/v1/map-config");
  assert.deepEqual(mapConfig, { enabled: true, provider: "amap" });
  assert.equal(requests.length, 1);
  assert.equal(requests[0].url, "https://agent.example.test/v1/map-config");
  assert.equal(requests[0].options.headers.Accept, "application/json");
  assert.equal(requests[0].options.headers["X-Agent-Token"], "team-token");

  await client.requestJson("/health", { withToken: false });
  assert.equal(requests[1].options.headers["X-Agent-Token"], undefined);

  const invalidJsonClient = agent.createClient({
    config: { apiBase: "https://agent.example.test" },
    fetchImpl: async () => ({
      ok: true,
      status: 200,
      headers: { get: () => "text/html" },
      text: async () => "<html>not json</html>"
    })
  });
  await assert.rejects(
    invalidJsonClient.requestJson("/v1/map-config"),
    (error) => error.code === "INVALID_JSON"
  );

  console.log("vehicle-hmi-next agent-client tests passed");
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
