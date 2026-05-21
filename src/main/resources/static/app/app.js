const STORAGE_KEY = "life-service-console";

const state = {
  baseUrl: "",
  email: "demo2001@life.local",
  token: "",
  userId: "",
  nickname: "",
  voucherId: "1001",
  merchantId: "1",
  orderNo: ""
};

const $ = (selector) => document.querySelector(selector);

const fields = {
  baseUrl: $("#baseUrlInput"),
  email: $("#emailInput"),
  token: $("#tokenInput"),
  userId: $("#userIdInput"),
  nickname: $("#nicknameInput"),
  voucherId: $("#voucherIdInput"),
  merchantId: $("#merchantIdInput"),
  orderNo: $("#orderNoInput"),
  categoryId: $("#categoryIdInput"),
  keyword: $("#keywordInput")
};

function loadState() {
  const fallbackBaseUrl = location.protocol.startsWith("http") ? location.origin : "http://localhost:8081";
  try {
    Object.assign(state, JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}"));
  } catch (error) {
    appendLog("读取本地配置", {
      level: "error",
      httpStatus: "-",
      code: "LOCAL_STORAGE_ERROR",
      message: error.message,
      elapsed: 0,
      traceId: "-"
    });
  }
  state.baseUrl = state.baseUrl || fallbackBaseUrl;
  fields.baseUrl.value = state.baseUrl;
  fields.email.value = state.email;
  fields.token.value = state.token;
  fields.userId.value = state.userId;
  fields.nickname.value = state.nickname;
  fields.voucherId.value = state.voucherId;
  fields.merchantId.value = state.merchantId;
  fields.orderNo.value = state.orderNo;
}

function saveState() {
  state.baseUrl = normalizeBaseUrl(fields.baseUrl.value);
  state.email = fields.email.value.trim();
  state.token = fields.token.value.trim();
  state.userId = fields.userId.value.trim();
  state.nickname = fields.nickname.value.trim();
  state.voucherId = fields.voucherId.value.trim();
  state.merchantId = fields.merchantId.value.trim();
  state.orderNo = fields.orderNo.value.trim();
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

function normalizeBaseUrl(value) {
  const fallback = location.protocol.startsWith("http") ? location.origin : "http://localhost:8081";
  const trimmed = (value || fallback).trim();
  return trimmed.endsWith("/") ? trimmed.slice(0, -1) : trimmed;
}

function buildUrl(path, params = {}) {
  const url = new URL(`${normalizeBaseUrl(fields.baseUrl.value)}${path}`);
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== "") {
      url.searchParams.set(key, value);
    }
  });
  return url.toString();
}

function traceId() {
  return `ui-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
}

async function requestApi(label, path, options = {}) {
  saveState();
  const started = performance.now();
  const requestTraceId = traceId();
  const headers = new Headers(options.headers || {});
  headers.set("X-Trace-Id", requestTraceId);
  const token = fields.token.value.trim();
  if (options.auth !== false && token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  try {
    const response = await fetch(buildUrl(path, options.params), {
      method: options.method || "GET",
      headers,
      body: buildRequestBody(options, headers)
    });
    const elapsed = Math.round(performance.now() - started);
    const responseTraceId = response.headers.get("X-Trace-Id") || requestTraceId;
    const raw = await response.text();
    let body = raw;
    try {
      body = raw ? JSON.parse(raw) : null;
    } catch (error) {
      body = raw;
    }

    const result = {
      level: response.ok && body?.success !== false ? "ok" : "fail",
      label,
      httpStatus: response.status,
      code: body?.code || (response.ok ? "OK" : "HTTP_ERROR"),
      message: body?.message || response.statusText || "",
      data: body?.data ?? body,
      elapsed,
      traceId: responseTraceId,
      raw: body
    };
    updateMetrics(result);
    appendLog(label, result);
    return result;
  } catch (error) {
    const result = {
      level: "error",
      label,
      httpStatus: "NETWORK",
      code: "REQUEST_FAILED",
      message: error.message,
      data: null,
      elapsed: Math.round(performance.now() - started),
      traceId: requestTraceId,
      raw: null
    };
    updateMetrics(result);
    appendLog(label, result);
    return result;
  }
}

function buildRequestBody(options, headers) {
  if (options.body === undefined) {
    return undefined;
  }
  if (options.body instanceof FormData || typeof options.body === "string") {
    return options.body;
  }
  if (!headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  return JSON.stringify(options.body);
}

function updateMetrics(result) {
  $("#lastHttpStatus").textContent = result.httpStatus;
  $("#lastCode").textContent = result.code || "-";
  $("#lastElapsed").textContent = `${result.elapsed} ms`;
  $("#lastTraceId").textContent = result.traceId || "-";
}

function appendLog(label, result) {
  const log = $("#responseLog");
  const entry = document.createElement("article");
  entry.className = `log-entry ${result.level || "fail"}`;
  entry.innerHTML = `
    <div class="log-title">
      <span>${escapeHtml(label)}</span>
      <span class="log-meta">${escapeHtml(String(result.httpStatus))} · ${escapeHtml(String(result.elapsed))} ms</span>
    </div>
    <div class="log-meta">${escapeHtml(result.code || "-")} · traceId=${escapeHtml(result.traceId || "-")}</div>
    <pre class="json-block">${escapeHtml(formatJson({
      success: result.level === "ok",
      code: result.code,
      message: result.message,
      data: result.data
    }))}</pre>
  `;
  log.prepend(entry);
}

function renderResult(selector, result) {
  const target = $(selector);
  target.innerHTML = `<pre class="json-block">${escapeHtml(formatJson({
    httpStatus: result.httpStatus,
    code: result.code,
    message: result.message,
    traceId: result.traceId,
    data: result.data
  }))}</pre>`;
}

function formatJson(value) {
  return JSON.stringify(value, null, 2);
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function yuan(cents) {
  if (cents === null || cents === undefined || Number.isNaN(Number(cents))) {
    return "-";
  }
  return `¥${(Number(cents) / 100).toFixed(2)}`;
}

function statusText(status) {
  const map = {
    0: "禁用",
    1: "启用",
    2: "售罄",
    3: "暂停"
  };
  return map[status] || String(status ?? "-");
}

async function withLoading(button, action) {
  button.disabled = true;
  const original = button.textContent;
  button.textContent = "处理中";
  try {
    await action();
  } finally {
    button.disabled = false;
    button.textContent = original;
  }
}

function renderCategories(categories = []) {
  const list = $("#categoryList");
  if (!categories.length) {
    list.innerHTML = `<span class="empty">没有分类数据。</span>`;
    return;
  }
  list.innerHTML = categories.map((item) => `
    <button class="pill" type="button" data-category-id="${escapeHtml(item.id)}">
      ${escapeHtml(item.name)} · ${escapeHtml(item.id)}
    </button>
  `).join("");
  list.querySelectorAll("[data-category-id]").forEach((button) => {
    button.addEventListener("click", () => {
      fields.categoryId.value = button.dataset.categoryId;
    });
  });
}

function renderMerchantTable(records = []) {
  const target = $("#merchantResult");
  if (!records.length) {
    target.innerHTML = `<span class="empty">没有商户数据。</span>`;
    return;
  }
  target.innerHTML = `
    <table class="result-table">
      <thead>
      <tr>
        <th>ID</th>
        <th>名称</th>
        <th>区域</th>
        <th>人均</th>
        <th>评分</th>
        <th>操作</th>
      </tr>
      </thead>
      <tbody>
      ${records.map((item) => `
        <tr>
          <td>${escapeHtml(item.id)}</td>
          <td>${escapeHtml(item.name)}</td>
          <td>${escapeHtml(item.area || "-")}</td>
          <td>${escapeHtml(yuan(item.avgPriceCent))}</td>
          <td>${escapeHtml(item.score ?? "-")}</td>
          <td>
            <button class="table-action" type="button" data-merchant-id="${escapeHtml(item.id)}">选中</button>
          </td>
        </tr>
      `).join("")}
      </tbody>
    </table>
  `;
  target.querySelectorAll("[data-merchant-id]").forEach((button) => {
    button.addEventListener("click", () => {
      fields.merchantId.value = button.dataset.merchantId;
      saveState();
    });
  });
}

function renderVoucherTable(records = []) {
  const target = $("#merchantResult");
  if (!records.length) {
    target.innerHTML = `<span class="empty">该商户没有优惠券数据。</span>`;
    return;
  }
  target.innerHTML = `
    <table class="result-table">
      <thead>
      <tr>
        <th>ID</th>
        <th>标题</th>
        <th>支付</th>
        <th>抵扣</th>
        <th>状态</th>
        <th>操作</th>
      </tr>
      </thead>
      <tbody>
      ${records.map((item) => `
        <tr>
          <td>${escapeHtml(item.id)}</td>
          <td>${escapeHtml(item.title)}</td>
          <td>${escapeHtml(yuan(item.payAmountCent))}</td>
          <td>${escapeHtml(yuan(item.discountAmountCent))}</td>
          <td>${escapeHtml(statusText(item.status))}</td>
          <td>
            <button class="table-action" type="button" data-voucher-id="${escapeHtml(item.id)}">选中</button>
          </td>
        </tr>
      `).join("")}
      </tbody>
    </table>
  `;
  target.querySelectorAll("[data-voucher-id]").forEach((button) => {
    button.addEventListener("click", () => {
      fields.voucherId.value = button.dataset.voucherId;
      saveState();
    });
  });
}

function updateAuthFields(data = {}) {
  if (data.token) {
    fields.token.value = data.token;
  }
  if (data.userId !== undefined && data.userId !== null) {
    fields.userId.value = data.userId;
  }
  if (data.email) {
    fields.email.value = data.email;
  }
  if (data.nickname) {
    fields.nickname.value = data.nickname;
  }
  saveState();
}

async function login() {
  const email = fields.email.value.trim();
  if (!email) {
    $("#authResult").textContent = "请先填写邮箱。";
    return;
  }
  const result = await requestApi("邮箱登录", "/api/v1/auth/login", {
    method: "POST",
    auth: false,
    body: { email }
  });
  if (result.level === "ok") {
    updateAuthFields(result.data || {});
  }
  renderResult("#authResult", result);
}

async function loadCurrentUser() {
  const result = await requestApi("查询当前用户", "/api/v1/auth/me");
  if (result.level === "ok") {
    updateAuthFields(result.data || {});
  }
  renderResult("#authResult", result);
}

async function logout() {
  const result = await requestApi("退出登录", "/api/v1/auth/logout", {
    method: "POST"
  });
  if (result.level === "ok") {
    fields.token.value = "";
    fields.userId.value = "";
    fields.nickname.value = "";
    saveState();
  }
  renderResult("#authResult", result);
}

function hasToken(targetSelector) {
  if (fields.token.value.trim()) {
    return true;
  }
  $(targetSelector).textContent = "请先使用邮箱登录，获取 Authorization Bearer token。";
  return false;
}

async function loadCategories() {
  const result = await requestApi("加载商户分类", "/api/v1/merchant-categories", { auth: false });
  if (result.level === "ok") {
    renderCategories(result.data || []);
  }
}

async function searchMerchants() {
  const result = await requestApi("查询商户列表", "/api/v1/merchants", {
    auth: false,
    params: {
      categoryId: fields.categoryId.value.trim(),
      keyword: fields.keyword.value.trim(),
      pageNo: 1,
      pageSize: 10
    }
  });
  if (result.level === "ok") {
    renderMerchantTable(result.data?.records || []);
  } else {
    renderResult("#merchantResult", result);
  }
}

async function loadMerchantDetail() {
  const merchantId = fields.merchantId.value.trim();
  if (!merchantId) {
    $("#merchantResult").textContent = "请先填写或选中商户 ID。";
    return;
  }
  const result = await requestApi("查询商户详情", `/api/v1/merchants/${encodeURIComponent(merchantId)}`, {
    auth: false
  });
  renderResult("#merchantResult", result);
}

async function loadVouchers() {
  const merchantId = fields.merchantId.value.trim();
  if (!merchantId) {
    $("#merchantResult").textContent = "请先填写或选中商户 ID。";
    return;
  }
  const result = await requestApi("查询商户优惠券", `/api/v1/merchants/${encodeURIComponent(merchantId)}/vouchers`, {
    auth: false
  });
  if (result.level === "ok") {
    renderVoucherTable(result.data || []);
  } else {
    renderResult("#merchantResult", result);
  }
}

async function warmupVoucher() {
  const voucherId = fields.voucherId.value.trim();
  const result = await requestApi("预热秒杀券", `/api/v1/flash-sale-vouchers/${encodeURIComponent(voucherId)}/warmup`, {
    method: "POST",
    auth: false
  });
  renderResult("#flashSaleResult", result);
}

async function seckillVoucher() {
  if (!hasToken("#flashSaleResult")) {
    return;
  }
  const voucherId = fields.voucherId.value.trim();
  const result = await requestApi("秒杀下单", `/api/v1/flash-sale-vouchers/${encodeURIComponent(voucherId)}/orders`, {
    method: "POST"
  });
  if (result.level === "ok" && typeof result.data === "string") {
    fields.orderNo.value = result.data;
    saveState();
  }
  renderResult("#flashSaleResult", result);
}

async function repeatSeckill(targetSelector) {
  if (!hasToken(targetSelector)) {
    return;
  }
  const voucherId = fields.voucherId.value.trim();
  const results = [];
  for (let i = 0; i < 4; i += 1) {
    results.push(await requestApi(`秒杀同用户第 ${i + 1} 次`, `/api/v1/flash-sale-vouchers/${encodeURIComponent(voucherId)}/orders`, {
      method: "POST"
    }));
  }
  $(targetSelector).innerHTML = `<pre class="json-block">${escapeHtml(formatJson(results.map((item) => ({
    httpStatus: item.httpStatus,
    code: item.code,
    message: item.message,
    data: item.data,
    traceId: item.traceId,
    elapsed: `${item.elapsed} ms`
  }))))}</pre>`;
}

async function payOrder() {
  if (!hasToken("#paymentResult")) {
    return;
  }
  const orderNo = fields.orderNo.value.trim();
  if (!orderNo) {
    $("#paymentResult").textContent = "请先填写订单号，或先完成一次成功秒杀。";
    return;
  }
  const result = await requestApi("模拟支付回调", `/api/v1/voucher-orders/${encodeURIComponent(orderNo)}/payment`, {
    method: "POST"
  });
  renderResult("#paymentResult", result);
}

async function hitMerchantLimit() {
  const merchantId = fields.merchantId.value.trim();
  if (!merchantId) {
    $("#rateLimitResult").textContent = "请先填写或选中商户 ID。";
    return;
  }
  const results = await Promise.all(Array.from({ length: 12 }, (_, index) =>
    requestApi(`商户详情限流第 ${index + 1} 次`, `/api/v1/merchants/${encodeURIComponent(merchantId)}`, {
      auth: false
    })
  ));
  $("#rateLimitResult").innerHTML = `<pre class="json-block">${escapeHtml(formatJson(results.map((item) => ({
    httpStatus: item.httpStatus,
    code: item.code,
    message: item.message,
    traceId: item.traceId,
    elapsed: `${item.elapsed} ms`
  }))))}</pre>`;
}

function bindEvents() {
  $("#saveBaseUrlButton").addEventListener("click", () => {
    saveState();
    appendLog("保存 API 地址", {
      level: "ok",
      httpStatus: "-",
      code: "SAVED",
      message: "API 地址已保存到浏览器本地存储。",
      data: { baseUrl: state.baseUrl },
      elapsed: 0,
      traceId: "-"
    });
  });

  $("#loginButton").addEventListener("click", (event) => withLoading(event.currentTarget, login));
  $("#meButton").addEventListener("click", (event) => withLoading(event.currentTarget, loadCurrentUser));
  $("#logoutButton").addEventListener("click", (event) => withLoading(event.currentTarget, logout));
  $("#loadCategoriesButton").addEventListener("click", (event) => withLoading(event.currentTarget, loadCategories));
  $("#searchMerchantsButton").addEventListener("click", (event) => withLoading(event.currentTarget, searchMerchants));
  $("#loadMerchantDetailButton").addEventListener("click", (event) => withLoading(event.currentTarget, loadMerchantDetail));
  $("#loadVouchersButton").addEventListener("click", (event) => withLoading(event.currentTarget, loadVouchers));
  $("#warmupButton").addEventListener("click", (event) => withLoading(event.currentTarget, warmupVoucher));
  $("#seckillButton").addEventListener("click", (event) => withLoading(event.currentTarget, seckillVoucher));
  $("#repeatSeckillButton").addEventListener("click", (event) => withLoading(event.currentTarget, () => repeatSeckill("#flashSaleResult")));
  $("#payButton").addEventListener("click", (event) => withLoading(event.currentTarget, payOrder));
  $("#merchantLimitButton").addEventListener("click", (event) => withLoading(event.currentTarget, hitMerchantLimit));
  $("#flashSaleLimitButton").addEventListener("click", (event) => withLoading(event.currentTarget, () => repeatSeckill("#rateLimitResult")));
  $("#clearLogButton").addEventListener("click", () => {
    $("#responseLog").innerHTML = "";
  });
}

loadState();
bindEvents();
