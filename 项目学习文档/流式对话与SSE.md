# 流式对话与 SSE（当前实现）

## 结论

当前问答使用 **SSE（Server-Sent Events）格式**传输流式回答。前端通过 `fetch` 发起 `POST` 请求，再从响应体逐块读取、解析 SSE 事件；**没有使用浏览器的 `EventSource` API**。SSE 指响应的内容类型和事件格式，不要求前端必须用 `EventSource` 接收。

## 请求与响应

前端 [`askHealth`](../../ruoyi-fast-vue3/src/api/health.js) 向 `POST /health/ask/stream` 发送 JSON，包含成员、问题、模型编号和可选的会话编号。请求头设置 `Accept: text/event-stream`，并携带登录令牌。这里使用 `fetch`，可在一次请求中提交 JSON 请求体和认证头。

后端 [`HealthController.ask`](../ruoyi-admin/src/main/java/com/ruoyi/health/HealthController.java) 声明响应类型为 `text/event-stream`，使用 `StreamingResponseBody` 写入事件。每个事件写为 `data:` 加一段 JSON，再加两个换行符；写入后调用 `flush()`，让客户端及时收到内容。响应还设置了 `Cache-Control: no-cache` 和 `X-Accel-Buffering: no`。

事件 JSON 的 `type` 字段决定前端处理方式：

| `type` | 内容 | 前端处理 |
| --- | --- | --- |
| `chunk` | `text`：一段回答文本 | 追加到当前助手消息。 |
| `done` | `conversationId`、`sources` | 结束读取，更新会话编号和引用来源。 |
| `error` | `message`：错误说明 | 抛出错误，由页面显示提示。 |

例如，一段回答的事件格式如下；实际回答会连续返回多个 `chunk`：

```text
data:{"type":"chunk","text":"找到一条相关记录。"}

data:{"type":"done","conversationId":"...","sources":["..."]}

```

## 前端接收与展示

1. `askHealth` 检查 HTTP 状态和响应的 `Content-Type`，然后通过 `response.body.getReader()` 取得读取器。
2. 循环调用 `reader.read()` 接收字节块，用 `TextDecoder` 解码。因为一次读取不保证刚好对应一条事件，代码先把文本放进缓冲区，再按空行分隔完整事件。
3. 对 `data:` 后面的 JSON 按 `type` 处理：收到 `chunk` 时调用页面传入的回调；收到 `done` 时返回完成数据；收到 `error` 时抛出异常。如果连接结束前没有收到 `done`，则提示回答中断。
4. 页面 [`submitQuestion`](../../ruoyi-fast-vue3/src/views/health/index.vue) 先插入一条空的助手消息，再把每个 `chunk` 追加进去，并在用户位于消息底部附近时跟随滚动。完成后保存引用来源；失败时移除本轮临时消息并显示错误提示。

关键代码：[`askHealth`](../../ruoyi-fast-vue3/src/api/health.js)、[`submitQuestion`](../../ruoyi-fast-vue3/src/views/health/index.vue)、[`HealthController.ask`](../ruoyi-admin/src/main/java/com/ruoyi/health/HealthController.java)、[`HealthController.sendEvent`](../ruoyi-admin/src/main/java/com/ruoyi/health/HealthController.java)。
