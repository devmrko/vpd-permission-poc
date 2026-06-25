# 설계서: MCP Reasoning/ORDS 검증 탭 사용 흐름 정리 (#463)

> **상태**: Approved
> **작성**: [AI] Architect · **최종수정**: 2026-06-25
> **추적성** — Redmine: #463 · 관련 ADR: 없음
> · 구현 파일: `mcp-reasoning.html`, `mcp-reasoning-result.html`, `probe.html`, `McpReasoningService` · 테스트: `mvn test`, Spring Boot 기동

## 1. 목적 (Why)

운영자가 보호 객체와 Bearer Token을 선택한 뒤 ORDS 검증과 MCP Reasoning을 자연스럽게 이어서 실행하고, 모델 답변을 표/요약 중심으로 읽을 수 있게 한다.

## 2. 범위 (Scope)

- **포함**: MCP Reasoning 기본 질문/원클릭 질문 개선, 결과 Markdown 렌더링, 실행 증거 요약, ORDS 검증에서 Reasoning 이동 안내.
- **제외**: MCP protocol 자체 변경, LLM 모델 변경, token 자동 공유.

## 3. 인수조건 (Acceptance Criteria)

- [ ] Reasoning 답변이 Markdown view로 렌더링되어 표가 표시된다.
- [ ] Prompt가 요약 먼저, 상세 나중 구조를 요구한다.
- [ ] 질문 없이 실행해도 권한 검증에 맞는 기본 질문이 사용된다.
- [ ] ORDS 검증 화면에서 같은 흐름의 다음 단계로 Reasoning 탭을 인지할 수 있다.
- [ ] `mvn test`와 Spring Boot 기동이 통과한다.

## 4. 컨텍스트 & 제약

- token 원문은 화면 이동으로 자동 전달하지 않는다.
- Reasoning은 실제 ORDS 호출 결과를 evidence JSON으로 모델에 전달한다.
- Markdown 렌더러는 기존 `data-markdown-view`를 사용한다.

## 5. 아키텍처 개요

```
ORDS 검증
  -> 결과 확인
  -> MCP Reasoning 이동
      -> 보호 객체 선택
      -> token 입력
      -> 기본 질문 또는 원클릭 질문
      -> ORDS tool 실행
      -> Markdown answer + evidence 표시
```

## 6. 데이터 모델

- `McpReasoningResult.answer`: Markdown text.
- `ProbeResult`: request/response/evidence table의 근거.

## 7. 함수 명세 (Function Specs)

| 함수 | 책임(1줄) | 시그니처(잠정) | 입력 | 출력 | 에러/실패 | 복잡? |
|------|-----------|----------------|------|------|-----------|-------|
| `buildPrompt` | 모델에 전달할 업무형 권한 분석 prompt 생성 | Java method | question, tool, evidence | prompt | 없음 | 단순 |
| `fallbackAnswer` | AI 미설정 시 evidence 기반 기본 설명 생성 | Java method | question, probeResult | Markdown text | 없음 | 단순 |

## 8. 흐름 / 알고리즘

1. 질문이 비어 있으면 기본 권한 검증 질문을 사용한다.
2. ORDS probe를 실행한다.
3. evidence JSON을 구성한다.
4. 모델 답변은 요약, 판단 근거 표, 상세, 다음 조치 순서로 요청한다.
5. 화면은 answer를 Markdown으로 렌더링한다.

## 9. 엣지케이스 & 에러 처리

- AI 미설정: Markdown fallback으로 ORDS 상태와 row count를 보여준다.
- ORDS 실패: 모델 호출 여부와 무관하게 request/response evidence를 표시한다.
- token 원문은 prompt/evidence에 포함하지 않는다.

## 10. 테스트 계획

- `mvn test`
- Spring Boot 재기동
- `/mcp-reasoning` 인증 요청 HTTP 200 확인

## 11. 리스크 & 대안 검토

- 선택: 화면/프롬프트 개선. 데이터 모델 변경 없이 사용 흐름을 개선한다.
- 대안: wizard UI. 구현 범위가 커지고 기존 탭 구조를 크게 바꿔야 한다.

## 12. 미해결 질문 (Open Questions)

- token 선택 드롭다운과 token 원문 자동 복호화는 보안 정책상 별도 검토가 필요하다.
