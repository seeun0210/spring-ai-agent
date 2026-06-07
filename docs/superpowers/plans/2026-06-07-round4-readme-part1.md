# Round 4 README 신규 작성 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 Round 3 README를 백업하고, `README.md`를 Round 4 RAG 학습 문서로 새로 작성한다.

**Architecture:** Round 3 결과물은 `docs/round3-backup.md`로 보존한다. 새 README는 Round 4의 개요, 학습 목표, 1부 "왜 RAG가 필요한가"를 담는다. 이후 구현이 진행되면 같은 README에 Spring AI 구성요소, PgVector 실행, 인덱싱/검색 검증, 실험 결과를 이어서 추가한다.

**Tech Stack:** Markdown, Spring AI RAG 개념, PgVector/RAG 학습 문서.

---

## File Structure

- Create: `docs/round3-backup.md`
  - 기존 `README.md` 내용을 그대로 보존한다.
- Modify: `README.md`
  - Round 4 RAG 문서로 새로 작성한다.
- Modify: `docs/superpowers/plans/2026-06-07-round4-readme-part1.md`
  - 실제 작업 방향과 맞게 계획을 보존한다.

---

### Task 1: Round 3 README 백업

**Files:**
- Create: `docs/round3-backup.md`

- [x] **Step 1: 기존 README를 백업**

Run:

```bash
cp README.md docs/round3-backup.md
```

Expected:

```text
docs/round3-backup.md 파일이 생성된다.
```

- [ ] **Step 2: 백업 파일이 원본과 같은지 확인**

Run:

```bash
cmp -s README.md docs/round3-backup.md
```

Expected:

```text
Exit code 0.
```

---

### Task 2: Round 4 README 신규 작성

**Files:**
- Modify: `README.md`

- [ ] **Step 1: README를 Round 4 문서로 교체**

Replace `README.md` with a Round 4-focused document containing:

```markdown
# loop-play-spring-ai-agent

## Round 4 - RAG로 배달 정책/FAQ 지식 연동

...
```

Required sections:

- 실행 환경
- 이번 라운드에 배우는 것
- 학습 목표
- 1부. 왜 RAG가 필요한가 - LLM이 모르는 것을 답하게 하는 법
- 1.1 Round 3까지의 한계
- 1.2 RAG가 해결하는 세 가지 문제
- 1.3 그냥 Tool로 검색 API를 만들면 안 되는가?
- 1.4 RAG의 두 파이프라인
- 1.5 임베딩의 직관
- 1.6 왜 청킹이 필요한가
- Round 3 백업 위치

- [ ] **Step 2: 미완성 문구 scan**

Run:

```bash
rg -n "미작성|보류|나중에 작성" README.md
```

Expected:

```text
No output.
```

- [ ] **Step 3: Markdown whitespace check**

Run:

```bash
git diff --check README.md docs/round3-backup.md docs/superpowers/plans/2026-06-07-round4-readme-part1.md
```

Expected:

```text
No output.
```

- [ ] **Step 4: Commit**

Run:

```bash
git add README.md docs/round3-backup.md docs/superpowers/plans/2026-06-07-round4-readme-part1.md
git commit -m "docs: start round four rag readme"
```

Expected:

```text
Commit succeeds with README, round3 backup, and plan changes.
```
