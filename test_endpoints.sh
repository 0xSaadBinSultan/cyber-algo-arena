#!/bin/bash
curl -X POST http://localhost:3000/api/admin/challenges \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{"type": "CP", "id": "TEST-1", "title": "Test Chal", "basePoints": 100, "difficulty": "EASY", "timeLimitMs": 1000, "memoryLimitMb": 256}'
echo ""
curl -X DELETE http://localhost:3000/api/admin/challenges/TEST-1 \
  -H "Authorization: Bearer ADMIN_TOKEN"
echo ""
