# mcp 调试器
```shell
# run the MCP inspector
npx @modelcontextprotocol/inspector
```

# SSE 交互过程记录
GET /sse

event: endpoint
data: /message?sessionId=4111c49e-48ad-4bb4-ba5c-cdeb5b5dc3b3

---
POST /message?sessionId=4111c49e-48ad-4bb4-ba5c-cdeb5b5dc3b3
``json
{"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{"sampling":{},"roots":{"listChanged":true}},"clientInfo":{"name":"mcp-inspector","version":"0.14.3"}},"jsonrpc":"2.0","id":0}
``

event: message
data: {"jsonrpc":"2.0","id":0,"result":{"protocolVersion":"2024-11-05","capabilities":{"completions":{},"logging":{},"prompts":{"listChanged":true},"resources":{"subscribe":false,"listChanged":true},"tools":{"listChanged":true}},"serverInfo":{"name":"mcp-server","version":"1.0.0"}}}

---
POST /message?sessionId=4111c49e-48ad-4bb4-ba5c-cdeb5b5dc3b3
```json
{"method":"notifications/initialized","jsonrpc":"2.0"}
```

---
POST /message?sessionId=4111c49e-48ad-4bb4-ba5c-cdeb5b5dc3b3
```json
{"method":"tools/list","params":{"_meta":{"progressToken":1}},"jsonrpc":"2.0","id":1}
```
event: message
data: {"jsonrpc":"2.0","id":1,"result":{"tools":[{"name":"calculateAdd","description":"计算2个数的和","inputSchema":{"type":"object","properties":{"num1":{"type":"integer","format":"int32","description":"数据1"},"num2":{"type":"integer","format":"int32","description":"数据2"}},"required":["num1","num2"],"additionalProperties":false}}]}}

---
POST /message?sessionId=4111c49e-48ad-4bb4-ba5c-cdeb5b5dc3b3
```json
{"method":"tools/call","params":{"name":"calculateAdd","arguments":{"num1":1,"num2":2},"_meta":{"progressToken":2}},"jsonrpc":"2.0","id":2}
```
event: message
data: {"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"3"}],"isError":false}}
