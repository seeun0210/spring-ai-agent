# Round 1 Curl Experiment Results

- executedAt: 2026-05-17 14:36:50 +0900
- model: qwen3:4b
- appBaseUrl: http://localhost:18080
- advisorLog: docs/experiment-logs/llm-performance.log

## 1단계 시나리오 1 - 배달 위치 확인

```bash
curl -sS -X POST http://localhost:18080/api/v1/support -H 'Content-Type: application/json' -d '{"message":"주문번호 2024-1234 배달 어디쯤에 있어요?"}'
```

```json
{
  "summary": "주문번호 2024-1234의 배달 상태를 확인 중입니다. 현재 배달 위치를 정확히 알 수 없습니다. 시스템에서 즉시 확인해 드리겠습니다.",
  "category": "DELIVERY",
  "urgency": "NORMAL",
  "nextAction": "배달 상태를 기다리세요.",
  "neededInfo": [],
  "handoffRequired": true,
  "handoffReason": "배달 상태 확인이 필요합니다."
}
```

```text
time_total=38.854686s
```

## 1단계 시나리오 2 - 취소와 환불 문의

```bash
curl -sS -X POST http://localhost:18080/api/v1/support -H 'Content-Type: application/json' -d '{"message":"방금 시킨 주문 취소하고 싶어요. 환불은 얼마나 걸려요?"}'
```

```json
{
  "summary": "주문 취소를 요청하셨습니다. 환불 처리 시간은 주문 취소 후 시스템 확인 후 1~3일 내에 진행됩니다. 주문번호를 알려주시면 즉시 처리해 드리겠습니다.",
  "category": "CANCEL",
  "urgency": "NORMAL",
  "nextAction": "주문번호를 알려주시면 즉시 취소 및 환불 절차를 진행해 드리겠습니다.",
  "neededInfo": [
    "주문번호"
  ],
  "handoffRequired": true,
  "handoffReason": "주문번호가 필요합니다."
}
```

```text
time_total=22.814464s
```

## 1단계 시나리오 3 - 배달 중 음식 파손

```bash
curl -sS -X POST http://localhost:18080/api/v1/support -H 'Content-Type: application/json' -d '{"message":"라이더가 음식을 엎었다는데 보상 받을 수 있나요?"}'
```

```json
{
  "summary": "라이더가 음식을 엎은 사항은 배달 상태 확인 후 보상 처리 가능합니다. 현재 주문 번호, 사고 발생 시간, 라이더 정보를 알려주시면 보상 절차를 진행할 수 있습니다. 고객센터로 연락해 주세요.",
  "category": "DELIVERY",
  "urgency": "NORMAL",
  "nextAction": "주문 번호와 사고 발생 시간을 알려주시면 보상 절차를 안내해 드리겠습니다.",
  "neededInfo": [
    "주문 번호",
    "사고 발생 시간",
    "라이더 정보"
  ],
  "handoffRequired": true,
  "handoffReason": "배달 상태 확인 및 보상 처리 절차 필요"
}
```

```text
time_total=16.663923s
```

## 2단계 Prompt Lab - 단순 프롬프트 curl 5회

```bash
for i in {1..5}; do curl -sS -X POST http://localhost:18080/api/v1/prompt-lab -H 'Content-Type: application/json' -d '{"systemPrompt":"당신은 배달 고객 상담 AI입니다. 반드시 한국어로만 응답하세요. 고객 문의를 ORDER, DELIVERY, CANCEL, REFUND, PAYMENT, ETC 중 하나로 분류하고 JSON으로 답하세요.","message":"주문번호 2024-1234 배달 어디쯤에 있어요?"}'; done
```

```json
{
  "totalRuns": 5,
  "categoryCounts": {
    "DELIVERY": 5
  },
  "urgencyCounts": {
    "HIGH": 1,
    "NORMAL": 4
  },
  "categoryConsistency": 1,
  "runs": [
    {
      "run": 1,
      "time_total": 19.686746,
      "response": {
        "summary": "주문번호 2024-1234의 배달 위치를 알려드리겠습니다.",
        "category": "DELIVERY",
        "urgency": "NORMAL",
        "nextAction": "배달 현황 확인",
        "neededInfo": [],
        "handoffRequired": false,
        "handoffReason": "배달 위치 확인 요청"
      }
    },
    {
      "run": 2,
      "time_total": 38.876492,
      "response": {
        "summary": "주문번호 2024-1234 배달 현황 확인 요청",
        "category": "DELIVERY",
        "urgency": "NORMAL",
        "nextAction": "배달 위치 확인 중",
        "neededInfo": [],
        "handoffRequired": false,
        "handoffReason": "배달 위치 확인 요청"
      }
    },
    {
      "run": 3,
      "time_total": 26.730179,
      "response": {
        "summary": "주문번호 2024-1234 배달 위치 확인 요청",
        "category": "DELIVERY",
        "urgency": "NORMAL",
        "nextAction": "Check current delivery location for order 2024-1234",
        "neededInfo": [],
        "handoffRequired": true,
        "handoffReason": "Delivery location inquiry"
      }
    },
    {
      "run": 4,
      "time_total": 15.123188,
      "response": {
        "summary": "주문번호 2024-1234의 배달 위치를 알려드릴게요.",
        "category": "DELIVERY",
        "urgency": "NORMAL",
        "nextAction": "배달 현황 확인",
        "neededInfo": [],
        "handoffRequired": false,
        "handoffReason": "배달 위치 확인 요청"
      }
    },
    {
      "run": 5,
      "time_total": 20.510076,
      "response": {
        "summary": "주문번호 2024-1234의 배달 현황을 알려드립니다.",
        "category": "DELIVERY",
        "urgency": "HIGH",
        "nextAction": "배달 현황을 확인합니다",
        "neededInfo": [
          "주문번호 2024-1234"
        ],
        "handoffRequired": true,
        "handoffReason": "배달 위치 확인 요청"
      }
    }
  ]
}
```

## 2단계 Prompt Lab - 구조화 프롬프트 curl 5회

```bash
for i in {1..5}; do curl -sS -X POST http://localhost:18080/api/v1/prompt-lab -H 'Content-Type: application/json' -d '{"systemPrompt":"","message":"주문번호 2024-1234 배달 어디쯤에 있어요?"}'; done
```
