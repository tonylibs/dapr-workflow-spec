# Verification Report

> 此檔案由 verify 步驟在 apply 完成後產生，用以確認實作與 specs / design / tasks 的一致性。
> 失敗的檢查須返回對應 artifact 修正後再重跑 verify。

**Change**: `console-api-wiring`
**Verified at**: `2026-08-12 17:35`（第二輪：code review 後重驗）
**Verifier**: Claude Code (apply session, inline executor — worktree/subagent path waived by the user; see Notes)

---

## 1. Structural Validation (`openspec validate --all --json`)

- [x] 全數 items `"valid": true`

**結果**：

```text
total=16 invalid=0
openspec validate console-api-wiring --strict → Change 'console-api-wiring' is valid
```

若有失敗項目，列出 id + issues：

| Item | Type | Issues |
|---|---|---|
| — | — | 無 |

---

## 2. Task Completion (`tasks.md`)

- [x] 所有 `- [ ]` 已變為 `- [x]`（18/18，`grep -c '^- \[ \]'` = 0）

**未完成任務**（若有）：

| Task | 未完成原因 | 是否阻塞 archive |
|---|---|---|
| — | — | 無 |

新增於 apply 期間的兩個 task（皆已完成），來自實跑 dws-admin 時發現的問題：

| Task | 來源 |
|---|---|
| 2.0 加入 `vitest` + test script，adapter 先寫測試 (RED→GREEN) | 使用者選擇 real TDD；`dws-console` 原本沒有 test runner |
| 6.0 dev-server proxy + 4xx 不重試 | 瀏覽器實測發現（見 §4） |

---

## 3. Delta Spec Sync State

| Capability | Sync 狀態 | 備註 |
|---|---|---|
| `console-read-wiring` | ✗ 待 sync | 新 capability，`openspec/specs/` 尚無同名目錄；由 `openspec archive` 建立 |

Requirements 於第二輪新增兩條（`Status vocabulary matches the read model`、
`Complete collections on detail screens`），對應 code review 找出的缺陷。

---

## 4. Design / Specs Coherence Spot Check

| 抽樣項 | design 描述 | specs 對應 | 差距 |
|---|---|---|---|
| Base URL | D4：`VITE_DWS_ADMIN_URL`，集中於 `adminUrl()` | Requirement: Configurable dws-admin base URL | 無 |
| task_events 折疊 | D2：依 `taskName` 分組，rich field 留 `undefined` | Requirement: Instance detail wired to summary and task events | 無 |
| Cursor 分頁 | D5：`useInfiniteQuery` + `nextCursor` | Requirement: Cursor pagination on list endpoints | 無 |
| 狀態驅動 | D6：query status 驅動 loading/empty/error | Requirement: Query-driven loading, empty, and error states | 無 |
| CORS / 4xx retry | D9（apply 期間新增） | 同上 Requirement 的 400/404 scenarios | 無 — D9 是既有 requirement 的實作手段，未新增 requirement |

**漂移警告**（非阻塞）：

- 無。`proposal.md`（依賴：新增 `vitest`）、`design.md`（新增 D8/D9 與對應 Risk）、`plan.md`、
  `tasks.md` 均已在 apply 期間同步更新，未留下與實作不符的敘述。

---

## 5. Implementation Signal

- [x] Worktree 內無未 staged 的檔案（`git status --porcelain` 空）
- [ ] 所有相關 commit 已推送 — 推送在 finishing 步驟執行

**Commit 範圍**：`04b45e7..HEAD`（實作 `497d7c8` + code-review 修正 commit）

---

## 6. Front-Door Routing Leak Detector（warning，非阻塞）

- [x] 無檔案

```bash
ls docs/superpowers/specs/*.md 2>/dev/null   # → 無輸出
```

| 檔案 | 內容是否已 captured 進 change | 建議動作 |
|---|---|---|
| — | — | 無洩漏 |

---

## 7. Deferred Manual Dogfood vs Automated Test Equivalence

`plan.md` 無任何 `[~]` deferred row，本節依判讀規則留空即 PASS。

實際上 plan §6 的 live dogfood **未 defer，已實跑**：本機以
Postgres 16 + drizzle migration 起了一個真實的 `dws-admin`（非 stub；Dapr sidecar
以最小 stub 滿足其開機檢查，該路徑與 read API 無關），seed 讀模型後用 Chromium 走過四條路由。

| 驗收項目（plan §6 / brainstorm Acceptance） | 結果 |
|---|---|
| `/workflows` 渲染真實資料 | ✅ 3 個 workflow，狀態與相對時間正確 |
| `/workflows/:name` 版本史 + deployments | ✅ 3 版本；note 推導出 `current` / `drained at 14:48` |
| `/instances` 列表 | ✅ 4 筆，含 `PENDING` 的 null `startedAt` 顯示為 `—` / `in progress` |
| `/instances/:id` header + timeline | ✅ 推導值正確：duration `3m 0s`、tasks `4 · 1 failed`、retries `1`（來自重複的 `started` 事件） |
| Cursor 分頁 | ✅ 首頁 20 筆 → Load more 帶 `cursor=WyIyMDI2...` → 34 筆，無重複 |
| Server-side 篩選 | ✅ `?status=COMPLETED&limit=20`，畫面僅剩 `COMPLETED` |
| Empty state | ✅ `?workflow=drained-flow&status=PENDING` → 「No instances match these filters」 |
| 404 not-found view | ✅ `/workflows/does-not-exist` 1002ms、`/instances/nope-404` 324ms（修正 retry 前需等三次退避重試） |
| dws-admin 不可達 | ✅ 兩種畫面皆顯示 error banner + Retry；恢復連線後重新渲染 |
| 每個 endpoint 一個 query cache entry | ✅ 以網路請求觀察：六個 endpoint 各自獨立的 query key；重訪路由由 cache 供應後在背景 revalidate（`QueryClient` 未設 `staleTime`，屬預設行為） |

### 第二輪：code review 後修正（`/code-review high`）

第一輪 verify 判定 PASS 是**錯的**——review 找出 7 個 runtime/contract 問題，其中最嚴重的一個
連 live dogfood 都沒抓到，因為**我 seed 的資料帶著跟程式碼相同的錯誤假設**（見 retrospective §5）。

| # | 缺陷 | 修正 |
|---|---|---|
| 1 | 🔴 status 詞彙全錯：UI union 用 mockup 的 `DEPLOYED`/`ACTIVE`/`RUNNING`，dws-admin 實際存 `created`/`updated`、`applied`/`failed`/`drained`/`collected`、`started`/`completed`/`failed`。所有 status pill 會是無法辨識的中性色，所有 filter chip 回 0 筆（dws-admin 大小寫敏感比對） | view model union 改用真實詞彙；`statusClass`、`INSTANCE_STATUSES` 同步；新增 2 個 regression test 斷言「每個 stored status 都對到非中性色」「chips 等於 stored instance 詞彙」（design D10 / spec `Status vocabulary matches the read model`） |
| 2 | 🔴 `DEFAULT_BASE_URL = ""` 會打到 console 自己的同名路由，拿到 HTML 後在 `.json()` 炸掉 | 預設改為 `/dws-admin` 前綴 |
| 3 | 🟡 detail 子集合只取第一頁、丟棄 `nextCursor`，timeline 會靜默截斷且 header 計數低估 | 新增 `fetchAllPages()`（上限 20 頁）供 versions / deployments / tasks 使用（spec `Complete collections on detail screens`） |
| 4 | 🟡 workflow filter 只看得到前 20 個 workflow | 新增 `useWorkflowNames()`，drain 所有頁 |
| 5 | 📌 `d.status === "RUNNING"` 與 `dep.status === "DRAINED"` 為 dead comparison | 改用真實詞彙；`tsc` 在修正詞彙後直接把這兩處標成 TS2367 |

修正後以**真實詞彙重新 seed** 並重跑 dogfood：filter `?status=completed` 由「0 筆」變為正確回傳，
status pill 正常上色，其餘項目維持上表結果。測試 28 → 30 全綠。

**apply 期間由實跑發現、已修正的兩個問題**（皆記入 design D9）：

1. **CORS** — `dws-admin` 不送 `Access-Control-Allow-Origin`，瀏覽器完全無法跨來源呼叫它。
   以 `vite.config.ts` 的 dev proxy（`/dws-admin` 前綴）讓請求同源解決，未改動 `dws-admin`。
   部署時需與 `dws-admin` 同一 ingress —— 已記入 design Risks。
2. **4xx 被重試** — TanStack Query 預設重試 3 次，讓 404 的 not-found 畫面躲在退避重試後面，
   對操作者像是卡住。加入 `retryUnlessClientError`：transport / 5xx 才重試。

---

## Overall Decision

- [x] ✅ PASS — 可進入 finishing-a-development-branch 與 archive（第二輪；第一輪 PASS 已被 code review 推翻並修正）

**下一步**：

撰寫 `retrospective.md`，接著 `openspec archive -y`（會把 `console-read-wiring` delta spec
同步進 `openspec/specs/`），最後推送分支。

**Notes（流程偏離，經使用者確認）**：

- `superpowers:requesting-code-review` 原本被連帶跳過（見 retrospective §4）；在寫完 retrospective
  後補跑 `/code-review high`，找出上表 7 項並全數修正。這是本 cycle 最有價值的單一步驟。
- Schema 的 apply instruction 要求 git worktree + subagent-driven-development。使用者選擇
  **inline on current branch、不開 subagent**（容器本身已隔離、分支已指定，且 session 規則限制
  spawning agent）。TDD 則**照做**：adapter 層先寫 28 個測試（RED），再實作至 GREEN。
- `dws-console` 原本沒有 test runner，故本 change 新增 `vitest`（`proposal.md` 已同步更新，
  原本寫「no dependencies added」）。
