# `HealthController.ask` 逐步解读

本文对应当前的 [`HealthController.ask`](../ruoyi-admin/src/main/java/com/ruoyi/health/HealthController.java)。它负责把一次健康资料提问组织成档案检索、模型回答和 SSE 流式响应。相关实现还包括 [`HealthMemoryService`](../ruoyi-admin/src/main/java/com/ruoyi/health/HealthMemoryService.java)、[`HealthFiles`](../ruoyi-admin/src/main/java/com/ruoyi/health/HealthFiles.java)、[`HealthModels`](../ruoyi-admin/src/main/java/com/ruoyi/health/HealthModels.java) 和前端 [`askHealth`](../../ruoyi-fast-vue3/src/api/health.js)。

## 1. 方法声明与请求入口

```java
@PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public ResponseEntity<StreamingResponseBody> ask(@RequestBody Question question) throws IOException
```

- 类上的 `@RestController` 和 `@RequestMapping("/health")` 与方法上的 `@PostMapping` 合起来，形成 `POST /health/ask/stream`。`produces` 声明响应为 `text/event-stream`。
- 类上的 `@SaCheckPermission("health:record:view")` 要求调用者具备查看健康档案的权限。
- `@RequestBody` 让 Spring 把请求体 JSON 反序列化为 `Question(member, text, modelId, conversationId)`。其中 `member` 指档案成员，`text` 是本轮问题；`modelId` 和 `conversationId` 可以不传。
- `ResponseEntity` 用于设置状态码、响应头和响应体；`StreamingResponseBody` 是稍后向 HTTP 输出流写数据的回调。声明的 `throws IOException` 允许准备阶段的文件读取异常向外传播。

下文按代码执行顺序说明。需要分清两个阶段：**返回 `ResponseEntity` 之前**准备检索和提示词；**框架执行 `StreamingResponseBody` 时**生成回答、写出事件并保存问答。

## 2. 校验本轮问题和档案成员

先检查 `question.text()`：不能为 `null`、不能全为空白，且 Java 字符串长度不能超过 2000；不满足时抛出 `IllegalArgumentException`。随后调用 `files.member(question.member())`，确认该成员是健康资料根目录下的有效成员目录。`currentUserId()` 从当前登录态取得账号 ID；取不到时抛出“请先登录”。后续会话与记忆查询都带上账号和成员条件。

## 3. 确定会话编号和问答模型

如果请求没有传 `conversationId`，或传入空白值，就调用 `UUID.randomUUID().toString()` 创建新会话编号；否则沿用传入值。随后用 `[a-f0-9-]{36}` 检查格式。这里是字符格式检查，并未验证字符串一定是规范 UUID，也未检查该会话是否已经存在。

`models.select(question.modelId(), "CHAT")` 选取用途为 `CHAT` 的模型配置。传了模型 ID 就按 ID 查询；未传则按默认标志和创建时间选择。没有可用配置时会在进入流式响应前失败。

## 4. 读取近期对话、摘要与跨会话记忆

`memories.conversation(userId, member, conversation)` 先查该会话的摘要，再查摘要覆盖位置之后最近最多 24 条原始消息，并将消息改为从旧到新排列。摘要和原始消息承担不同作用：摘要概括较早对话，近期消息保留 `user`、`assistant` 的角色与原文。

`memories.relevant(userId, member, question.text(), model)` 从该账号、该成员最近最多 200 条跨会话记忆中，请当前模型挑选最多 5 条与本轮问题有关的记忆。筛选失败时服务返回空列表，问答仍继续。记忆是用户自述或偏好，不能直接当作已核实的健康档案事实。

## 5. 整理用于档案检索的文本

`searchHistoryQuestions(...)` 从 `health_chat_message` 查询**当前账号、成员、会话的全部历史用户问题**，按消息 ID 升序拼接。当前这轮问题还没有写库，所以它不在历史查询结果中，会另外传给检索方法。

当累计历史问题超过 3000 字时，方法调用当前模型，将已有内容分段压缩成检索关键词和主题；提示词要求保留成员、症状、指标、药物、日期和指代关系，不添加事实。模型返回为空时抛出异常；非空结果最多取前 1500 字，再继续拼接后续问题。因此长会话的检索文本是**压缩后的历史信息**，不是逐字保留全部原话。

控制器接着把压缩或原样的历史问题与选中的跨会话记忆拼成 `searchContext`，并调用 `files.search(member, question.text(), searchContext)`。本轮问题单独作为参数传入，便于在打分时获得更高权重。

## 6. 检索 Markdown 档案片段

[`HealthFiles.search`](../ruoyi-admin/src/main/java/com/ruoyi/health/HealthFiles.java) 在成员目录内最多向下遍历两层，读取普通 `.md` 文件，排除“待确认”路径。文件按以 `## ` 开头的二级标题切分；每个片段最多保留前 2200 个字符。来源包含相对路径、从文件名提取的日期（没有则标记“日期见原文”）和片段文本。

本轮问题、历史检索文本、档案片段及路径都使用 Lucene `SmartChineseAnalyzer` 分词，并在各自文本内对词项去重。对每个词，先统计它出现在多少个候选片段中，再给包含该词的片段加分：

```text
稀有度分 = 1 + 8 × (候选片段数 - 包含该词的片段数) / 候选片段数
该词得分 = 稀有度分 × 权重（本轮问题中的词为 3，其他词为 1）
片段总分 = 所有命中词的得分之和
```

这里的除法是整数除法。常见词的区分能力较低；同时出现在本轮问题和历史中的词按本轮问题的权重计算一次。最后只保留分数大于 0 的前 8 个片段。这个流程是基于分词的词项匹配，不会自动理解同义词或医学语义。

## 7. 构造给回答模型的上下文

控制器把选中片段依次拼进 `context`，格式包含 `【相对路径；日期】` 和片段正文。`prompts.get()` 读取可配置的系统提示词，放在独立的 `system` 消息位置。

本轮 `prompt` 按顺序放入旧会话摘要、相关跨会话记忆、本轮问题和档案片段。每条记忆都加上“聊天自述或偏好，未核实”标识。检索时用的 `searchContext` 与这里传给回答模型的 `prompt` 不是同一个字符串：前者用于找档案，后者用于生成回答。

`recent` 中只保留内容非空且角色为 `user` 或 `assistant` 的消息；单条内容超过 2000 字时截取前 2000 字，再分别转换成 Spring AI 的 `UserMessage` 或 `AssistantMessage`。另从入选片段提取路径并去重，形成稍后保存、返回给前端的 `paths`。

## 8. 创建流式响应体并返回 HTTP 响应

`StreamingResponseBody body = output -> { ... }` 定义了拿到 HTTP 输出流以后要做的事。此时只是创建回调，尚未在这行代码里生成模型回答。方法最后返回 HTTP 200，内容类型为 `text/event-stream`，并设置 `Cache-Control: no-cache`、`X-Accel-Buffering: no`，减少缓存和代理缓冲。

准备阶段发生的异常（例如成员无效、模型未配置、读取档案失败、历史压缩失败）发生在返回 `ResponseEntity` 之前；它们不会由回调内的 `catch` 转成 SSE `error` 事件。

## 9. 在流式回调中发送回答

框架执行 `body` 时，代码先创建 `StringBuilder answer` 累计完整回答，然后分两种情况：

1. **没有命中档案片段**：不调用回答模型，直接发送“当前档案中没有找到足够的相关记录。没有记录不代表没有发生。”，并把这段文字放入 `answer`。
2. **命中片段**：`models.stream(model, system, history, prompt)` 调用 Spring AI 的流式模型接口，返回文字片段流。`toIterable()` 让当前回调逐个读取片段；跳过空片段，把每个有效片段追加到 `answer`，同时发出一个 `chunk` 事件。

`sendEvent` 使用 Jackson 将事件对象转成 JSON，按 SSE 格式写成 `data:` 加 JSON、再加两个换行符，编码为 UTF-8，并调用 `output.flush()`。例如：

```text
data:{"type":"chunk","text":"第一段回答"}

data:{"type":"chunk","text":"第二段回答"}

```

模型的 `Flux<String>` 是后端内部的片段流；SSE 是发给浏览器的 HTTP 格式。上述循环负责将两者连接起来。

## 10. 保存完整问答、发送完成事件

片段发送完后，`chatMapper.insertExchange(...)` 一次插入两条数据库消息：一条 `user` 问题、一条 `assistant` 完整回答；助手消息还保存去重后的来源路径 JSON。即使没有命中档案，固定的“资料不足”回答也会按这个流程保存。

数据库写入之后发送 `done` 事件，内容包含 `conversationId` 和 `sources`。**前端收到 `done` 才把这一轮视为正常完成**。随后调用 `memories.afterAnswer(...)` 提交异步任务，尝试更新较早消息的会话摘要，并从本轮用户原话提取跨会话记忆。`done` 已经发送，不代表后台记忆处理也已完成。

## 11. 异常与前端接收

流式回调中的 `RuntimeException` 会尝试发送 `{"type":"error","message":"回答生成失败，请重试"}`。写输出流时的 `IOException` 会直接向外抛出，客户端可能只看到连接中断。如果错误发生在若干 `chunk` 之后、保存之前，前端可能已临时显示部分文字，但这轮问答未必已入库。

前端 [`askHealth`](../../ruoyi-fast-vue3/src/api/health.js) 用 `fetch` 发送 JSON 和认证头，再用 `response.body.getReader()` 逐块读取字节。它用 `TextDecoder` 解码、按空行拆分 SSE 帧：`chunk` 交给页面逐段展示，`done` 返回会话编号和来源，`error` 抛出异常；如果连接结束仍未收到 `done`，则提示回答中断。关于前端展示的完整过程，另见[流式对话与 SSE](流式对话与SSE.md)。
