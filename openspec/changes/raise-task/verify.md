# Verification Report

**Change**: `raise-task`
**Verified at**: `2026-08-06 11:55`
**Verifier**: Claude Code (`/opsx:apply` → verify step, inline executor)

---

## 1. Structural Validation (`openspec validate --all --json`)

- [x] 全數 items `"valid": true`

**結果**：

```text
total items: 14
invalid: 0
```

`openspec validate raise-task --strict` 亦回報 `Change 'raise-task' is valid`。

若有失敗項目，列出 id + issues：

| Item | Type | Issues |
|---|---|---|
| — | — | 無 |

---

## 2. Task Completion (`tasks.md`)

- [x] 所有 `- [ ]` 已變為 `- [x]`（21/21）

**未完成任務**（若有）：

| Task | 未完成原因 | 是否阻塞 archive |
|---|---|---|
| — | — | 無未完成任務 |

---

## 3. Delta Spec Sync State

| Capability | Sync 狀態 | 備註 |
|---|---|---|
| `workflow-error-handling` | ✗ 待 sync | **且前置未滿足 — 見下方阻塞說明** |

**阻塞說明（本次 verify 的唯一實質發現）**：

`openspec/specs/` 目前**沒有** `workflow-error-handling/` 目錄。該 capability 由前一個 slice
`try-catch-retry` 定義，那個 change 的 code 已 merge 到 `main`、tasks 32/32 全數完成，但
**從未跑過 `/opsx:archive`** —它的目錄下只有 `brainstorm/proposal/design/specs/tasks`，
沒有 `verify.md`／`retrospective.md`，delta spec 也從未 sync 進 `openspec/specs/`。

本 change 的 delta 是對同一 capability 的 `## ADDED Requirements`（8 條 raise 需求）。
若在此狀態下直接 `openspec archive raise-task`，產生的
`openspec/specs/workflow-error-handling/spec.md` 只會包含本 change 的 8 條需求，
**靜默遺失 `try-catch-retry` 的 13 條需求**（try body 執行、`catch.errors.with` 靜態過濾、
`catch.when`/`exceptWhen` 動態過濾、error 變數綁定、retry policy 解析、backoff/jitter/limits、
recovery block、scope 語義、controller 巢狀編譯、`catch.then` 缺口）。

這是 **change 間的 archive 順序問題**，不是本 change 的實作缺陷：正確順序是先 archive
`try-catch-retry`（建立完整的 capability spec），再 archive `raise-task`（疊加 ADDED 需求）。
archive 另一個 change 屬於該 change 自己的 lifecycle，超出 `/opsx:apply raise-task` 的範圍，
故在此標記並交由使用者決定，不擅自執行。

---

## 4. Design / Specs Coherence Spot Check

| 抽樣項 | design 描述 | specs 對應 | 差距 |
|---|---|---|---|
| D1 literal vs expression | 依 SDK one-of accessor 分支，不做 `${...}` sniffing | Requirement「Raised error fields resolve literal or expression values」+ 3 scenarios | 無 |
| D2 `status` 無 expression variant | pinned SDK 為 primitive `int`，記為 SDK gap | Requirement「Raised error status is a literal value」 | 無 |
| D3 `instance` 預設/覆寫 | 有宣告用宣告值，無則取 raising task 位置 | Requirement「Raised error `instance` defaults to the raising task's location」+ 2 scenarios | 無 |
| D4 marker short-circuit | 專屬 marker，`of()` 短路不重新分類 | Requirement「Raised error survives error classification unmodified」+ 2 scenarios | 無 |
| D5 `use.errors` 具名解析 | 比照 `use.retries`，未解析大聲失敗 | Requirement「Named error definitions resolve from `use.errors`」+ 3 scenarios | 無 |
| D7 重用既有失敗路徑 | 不新增傳播程式碼 | Requirements「Raised error inside `try`…」/「…outside any `try`…」 | 無 |
| D8 擴充既有 capability | 不新增 capability | delta 置於 `specs/workflow-error-handling/` | 無 |

**漂移警告**（非阻塞）：

- 無。

---

## 5. Implementation Signal

- [x] Worktree 內無未 staged 的檔案（`git status --porcelain` 回傳 0 行）
- [x] 所有相關 commit 已推送（PR #33，branch `claude/openworkflow-raise-task-5u9sd5`）

**Commit 範圍**：`ea02abb..3794cd2`（實作 4 個 commit；`ea02abb` 為先前已推送的 opsx artifacts）

| Commit | 內容 |
|---|---|
| `fadfbb8` | `RaisedErrorException` + `WorkflowErrors` short-circuit + 3 個 unit test |
| `d21189d` | `RaiseErrorActivity` + `RaiseErrorRequest` + bootstrap 註冊 + 9 個 unit test |
| `db8e338` | `InterpreterWorkflow` dispatch wiring + 5 個 interpreter test |
| `3794cd2` | `taskTypeOf` lifecycle test + roadmap 更新 |

**測試門檻結果（實跑，非推測）**：

```text
dws-orchestrator  ./mvnw verify → Tests run: 102, Failures: 0, Errors: 0 — BUILD SUCCESS
dws-controller    ./mvnw test   → Tests run:  49, Failures: 0, Errors: 0 — BUILD SUCCESS
```

`dws-controller` 綠燈確認 `raise` 確實不需要任何 controller 變更（本 change 未動該元件一行）。

**環境備註**：容器預設 JDK 為 21，而兩個元件的 `maven.compiler.release=25`（CI 用 Temurin 25）。
本次為跑真實門檻，於 scratchpad 安裝 Temurin 25 並以 `JAVA_HOME` 指向它執行；未修改任何
`pom.xml`。此為既有的環境落差（已記載於 `dws-controller/CLAUDE.md`「Known issues」），非本
change 造成。

---

## 6. Front-Door Routing Leak Detector（warning，非阻塞）

- [x] 無檔案

**洩漏清單**：

| 檔案 | 內容是否已 captured 進 change | 建議動作 |
|---|---|---|
| — | — | 無洩漏（`docs/superpowers/specs/` 不存在） |

---

## 7. Deferred Manual Dogfood vs Automated Test Equivalence

plan.md 無任何 `[~]` deferred row（`grep -c '\[~\]'` = 0），本節依判讀規則留白即 PASS。

| Deferred dogfood (plan §) | Equivalent automated test | Coverage assessment | 真正 gap? |
|---|---|---|---|
| — | — | — | — |

---

## Overall Decision

- [ ] ✅ PASS — 可進入 finishing-a-development-branch 與 archive
- [x] ⚠️ PASS WITH WARNINGS — 實作本身通過，但 **archive 有前置順序需求**
- [ ] ❌ FAIL

**實作面**：全數通過。21/21 tasks 完成、structural validation 全綠、design 與 specs 無漂移、
兩個元件的 CI 門檻皆實跑綠燈、無未提交檔案。

**警告（僅影響 archive，不影響 merge）**：如 §3 所述，`try-catch-retry` 尚未 archive，
`openspec/specs/workflow-error-handling/` 因此不存在。

**下一步**：

1. 產出 `retrospective.md`（本 cycle 的最後一個 artifact）。
2. **archive 順序需使用者決定**：
   - 建議：先 `openspec archive try-catch-retry`（它已 32/32 完成且 code 已在 `main`），
     再 `openspec archive raise-task`，讓 `workflow-error-handling` 的 spec 依序完整組成。
   - 若僅 archive `raise-task`，該 capability spec 會只含本 change 的 8 條需求，
     遺失 slice 2.1 的 13 條 —— 不建議。
   - 亦可兩者都暫不 archive，先讓 PR #33 merge，archive 另案處理。
3. PR #33 已存在並已推送本次實作，不另開 PR。
