# Spring AI 在项目中的使用

这个项目用 Spring AI 统一调用聊天模型，主要服务于健康档案问答、报告识别和聊天记忆处理。Spring AI 负责构造模型请求并接收文本或流式结果；档案检索、数据存储、模型配置和向前端发送 SSE 由项目代码完成。

可以先记住四个入口：[`HealthModels`](../ruoyi-admin/src/main/java/com/ruoyi/health/HealthModels.java) 封装所有 Spring AI 调用；[`HealthController`](../ruoyi-admin/src/main/java/com/ruoyi/health/HealthController.java) 组织问答和 HTTP 接口；[`HealthFiles`](../ruoyi-admin/src/main/java/com/ruoyi/health/HealthFiles.java) 负责健康档案检索与报告处理；[`HealthMemoryService`](../ruoyi-admin/src/main/java/com/ruoyi/health/HealthMemoryService.java) 负责会话摘要和跨会话记忆。

```text
前端问题 ──> HealthController ──> HealthMemoryService：读取上下文、筛选记忆
                              ├─> HealthFiles：检索 Markdown 档案片段
                              └─> HealthModels：调用 Spring AI 生成回答
                                      └─> 模型服务商
                    HealthController：把回答片段转成 SSE 发给前端
```

这张图中的“检索”与“生成”是两个步骤。当前项目用本地文件和关键词评分选资料，再把选中的文字放进提示词，交给模型生成回答。检索本身没有调用 Spring AI。

## 依赖与模型配置

父工程在 [`pom.xml`](../pom.xml) 中通过 Spring AI BOM 管理版本，当前配置为 `2.0.1`。[`ruoyi-admin/pom.xml`](../ruoyi-admin/pom.xml) 引入 `spring-ai-openai` 和 `spring-ai-client-chat`。

模型配置保存在数据库中，由 [`HealthModels`](../ruoyi-admin/src/main/java/com/ruoyi/health/HealthModels.java) 管理。配置分为 `CHAT`（问答）和 `REPORT`（报告识别）两种用途；调用时可以指定模型编号，也可以选该用途的默认模型。API Key 在入库前加密，向前端列出模型时不返回密钥。

保存配置时，`HealthModels.save` 校验服务商、用途、模型编号和 API Key，生成配置 ID，并将密钥用 AES-GCM 加密后写入数据库。如果设为默认模型，会先取消同一用途下其他配置的默认标记。`select(id, purpose)` 只在对应的用途内查找：传入 ID 时取指定项；未传 ID 时优先取默认项，找不到则抛出“请先配置模型”的错误。`CHAT` 与 `REPORT` 的默认模型因此可以分别设置。

解密使用服务端的 `health.encryption-key`。调用模型时 `select` 将数据库记录转换成内部 `ModelConfig(provider, modelId, apiKey)`；列表接口返回的 `ModelView` 不含 API Key。模型配置的增删、设为默认分别由 [`HealthController` 的模型接口](../ruoyi-admin/src/main/java/com/ruoyi/health/HealthController.java) 提供，修改接口还要求 `system:config:edit` 权限。

`HealthModels.client` 根据服务商选择接口地址，再用 `OpenAiChatOptions` 设置 `baseUrl`、`apiKey` 和 `model`，构造 `OpenAiChatModel` 与 `ChatClient`。代码目前支持 `OPENAI`、`DEEPSEEK`、`QWEN` 三种配置。这里使用同一套 Spring AI 客户端调用对应接口，具体模型能力仍取决于所选服务商和模型。

`client` 每次调用都会根据选中的配置构造客户端。项目没有在这里配置统一的聊天记忆组件或 Advisor；历史消息由业务代码从数据库取出，再显式传给 `ChatClient`。也没有在 `HealthModels` 中设置重试、超时或温度等额外选项，实际调用以当前代码和所选模型服务为准。

项目没有实现 `ChatMemoryRepository`：该接口本身只定义聊天消息的存取，单靠它不能完成账号与成员隔离、会话摘要、跨会话记忆筛选，以及回答完成后的引用来源保存等业务流程。当前实现的取舍和可替换范围见[问答记忆机制](问答记忆机制.md#为什么没有实现-chatmemoryrepository)。

## 项目直接使用的 Spring AI 类型

当前后端 Java 代码直接引用了 **6 个 `org.springframework.ai` 包下的类型**，分别出现在 `HealthModels` 和 `HealthController`。它们处在不同层次：`OpenAiChatOptions` 提供模型调用参数，`OpenAiChatModel` 对接模型服务，`ChatClient` 组织提示词和调用；`Message`、`UserMessage`、`AssistantMessage` 描述传给模型的历史消息。

| 类型与包名 | 在本项目中的作用 | 直接使用位置 |
| --- | --- | --- |
| `org.springframework.ai.openai.OpenAiChatOptions` | 保存本次客户端的接口地址、API Key 和模型编号。 | `HealthModels.client` |
| `org.springframework.ai.openai.OpenAiChatModel` | 根据上述选项创建具体聊天模型，供 `ChatClient` 调用。 | `HealthModels.client` |
| `org.springframework.ai.chat.client.ChatClient` | 组合系统规则、历史消息、用户输入，并发起同步或流式模型调用。 | `HealthModels.client`、`ask`、`stream`、`readImage` |
| `org.springframework.ai.chat.messages.Message` | 作为历史消息的共同类型，让用户消息和助手消息能放在同一列表里。 | `HealthController.ask`、`HealthModels.stream` |
| `org.springframework.ai.chat.messages.UserMessage` | 表示历史中由用户发出的消息。 | `HealthController.ask` |
| `org.springframework.ai.chat.messages.AssistantMessage` | 表示历史中由助手生成的消息。 | `HealthController.ask` |

### 1. `OpenAiChatOptions`：确定请求发往哪里、调用哪个模型

[`HealthModels.client`](../ruoyi-admin/src/main/java/com/ruoyi/health/HealthModels.java) 先根据数据库配置中的 `provider` 选出服务商地址，再创建选项：

```java
OpenAiChatOptions options = OpenAiChatOptions.builder()
    .baseUrl(baseUrl)
    .apiKey(config.apiKey())
    .model(config.modelId())
    .build();
```

`baseUrl` 决定接口地址，`apiKey` 用于调用该服务商，`model` 指定服务商提供的模型编号。项目分别给 OpenAI、DeepSeek、通义千问配置地址，但都沿用这一套 `OpenAiChatOptions` 构造过程。`CHAT`、`REPORT` 是项目数据库中的用途标记，**不属于** `OpenAiChatOptions` 的字段；用途只影响调用前选择哪一条模型配置。

### 2. `OpenAiChatModel`：具体模型实现

项目用 `OpenAiChatModel.builder().options(options).build()` 创建模型对象，然后把它传给 `ChatClient.builder(...)`。`OpenAiChatModel` 承接上一步的服务地址、密钥和模型编号，是 `ChatClient` 发起模型请求所依赖的具体实现。

类名中虽然有 `OpenAi`，当前项目也用它配置 DeepSeek 和通义千问的兼容接口。代码没有分别创建 DeepSeek 或通义千问的专用 Spring AI 模型类。能否正常处理某种输入，例如图片，还取决于目标服务和所选模型自身的能力；创建 `OpenAiChatModel` 本身不保证具备视觉能力。

### 3. `ChatClient`：组装一次模型调用

`HealthModels.client` 返回 `ChatClient.builder(OpenAiChatModel.builder().options(options).build()).build()`。它是项目调用模型的统一入口。随后三个业务方法都从 `client(config).prompt()` 开始，按任务添加输入，再选择调用方式：

- `ask`：`.user(prompt).call().content()`，等待完整的文字结果。报告 JSON 提取、记忆筛选、摘要和记忆提取都走这条路径。
- `stream`：`.system(system).messages(history).user(prompt).stream().content()`，把规则、历史和本轮问题一起发给模型，逐段取得文本。
- `readImage`：`.user(user -> user.text(prompt).media(mimeType, new ByteArrayResource(bytes))).call().content()`，在同一条用户输入中放文字提示和图片，用于报告转写。

这些链式方法中，`.system(...)` 加入系统规则，`.user(...)` 加入当前用户输入，`.messages(...)` 加入已有消息；`.call()` 取完整结果，`.stream()` 取流式结果，末尾的 `.content()` 只读取模型回答文本。当前代码没有使用 `ChatClient` 的工具调用、Advisor 或自动记忆功能。

### 4. `Message`：历史消息列表的共同类型

`HealthModels.stream` 的 `history` 参数是 `List<Message>`。`Message` 让不同角色的消息可以一起传给 `.messages(history)`。控制器从数据库读取原始对话，再转换成 Spring AI 消息列表，最后交给 `stream`。

这里的 `Message` 是 Java 侧给模型的消息类型。数据库中的 `HealthChatMessage` 是本项目自己的实体，两者并非同一个类；从数据库实体到 Spring AI `Message` 的转换发生在 `HealthController.ask`。项目没有直接构造 `SystemMessage`：调用 `ChatClient` 的 `.system(system)` 来加入系统规则。

### 5. `UserMessage`：标明历史发言来自用户

数据库消息的 `role` 为 `user` 时，控制器执行 `new UserMessage(bounded)`。`bounded` 是原文截取后的内容，单条最多 2000 字。把它保留为用户角色，可以让模型理解这段话是过去的用户提问或补充，而非助手回答。

当前这一轮问题没有手动创建 `UserMessage` 对象，而是通过 `ChatClient` 的 `.user(prompt)` 加入。这个 `prompt` 除了用户问题，还包含会话摘要、相关记忆和检索到的档案片段，并明确标注这些内容的不同用途。

### 6. `AssistantMessage`：标明历史发言来自助手

数据库消息的 `role` 为 `assistant` 时，控制器执行 `new AssistantMessage(bounded)`。它和 `UserMessage` 一起按历史顺序放进 `List<Message>`，供模型理解此前的对话。

这些消息只提供会话上下文。项目的系统规则明确指出，历史对话和聊天自述不能代替本轮检索到的健康档案证据；助手以前说过的话也不会因此成为已核实的健康事实。

### 容易混淆的非 Spring AI 类型

`HealthModels` 还使用 `reactor.core.publisher.Flux<String>` 表示流式文本片段，使用 Spring Core 的 `ByteArrayResource` 包装图片字节，使用 Spring Framework 的 `MimeType` 表示图片类型。这些类型参与 Spring AI 调用，但**不在** `org.springframework.ai` 包下。`HealthController` 使用的 `StreamingResponseBody` 属于 Spring Web，负责向浏览器写 SSE，也不是 Spring AI 的类。

## 三种模型调用方式

项目将模型调用集中在 `HealthModels` 中：

| 方法 | Spring AI 调用 | 用途 |
| --- | --- | --- |
| `ask` | `prompt().user(prompt).call().content()` | 同步获取一段文本，用于报告信息提取、记忆筛选、会话摘要和记忆提取。 |
| `stream` | `prompt().system(system).messages(history).user(prompt).stream().content()` | 按片段生成健康档案问答，返回 `Flux<String>`。 |
| `readImage` | `prompt().user(user -> user.text(prompt).media(...)).call().content()` | 将报告图片和提示词一起交给具备视觉能力的模型，获取转写文本。 |

这里的 `.call()` 等待一次完整回答；`.stream()` 逐段提供模型输出。Spring AI 返回的 `Flux<String>` 还不是浏览器收到的 SSE：后端会遍历文本片段，将其包装成项目自己的 `chunk` 事件并写入 HTTP 响应。完整过程见[流式对话与 SSE](流式对话与SSE.md)。

三个方法共用 `client(config)`，但输入形式不同。`ask` 只传一段 `user` 文本，适合得到一个完整的短结果。`stream` 同时传 `system` 规则、历史消息和本轮 `user` 提示词；其中 `history` 是 Spring AI 的 `Message` 列表。`readImage` 在一条用户消息中同时放文字和图片媒体：图片字节被包装成 `ByteArrayResource`，并传入对应的 MIME 类型。三种调用最终都只取模型响应的文本 `content`，其他响应信息没有在这些方法中继续处理。

### 模型流与浏览器流的区别

```text
模型服务商 ──流式响应──> Spring AI 的 Flux<String>
                         └─> HealthController 逐块读取并累计完整回答
                               └─> 包装为 SSE 的 chunk 事件
                                     └─> 前端 fetch 读取并追加到消息
```

`Flux<String>` 是 Java 侧的模型输出；SSE 是本项目给浏览器的 HTTP 响应格式。两者通过 `HealthController.ask` 中的循环连接：每收到一个非空片段，就追加到完整回答，并调用 `sendEvent` 写出一个 `chunk` 事件。`sendEvent` 每写完一条事件就刷新输出流。前端使用 `fetch` 的 `response.body.getReader()` 读取这些事件，没有使用 `EventSource`。

## 健康档案问答的调用链

[`HealthController.ask`](../ruoyi-admin/src/main/java/com/ruoyi/health/HealthController.java) 接到问题后，先选出 `CHAT` 模型，读取当前账号、成员和会话的近期消息及摘要，再筛选相关跨会话记忆。随后由 [`HealthFiles.search`](../ruoyi-admin/src/main/java/com/ruoyi/health/HealthFiles.java) 从当前成员的 Markdown 档案中检索片段。

问答接口是 `POST /health/ask/stream`。请求包含成员、问题、可选的模型 ID 和会话 ID；新会话的 UUID 由后端生成。进入模型调用前，控制器会校验问题长度和成员目录，并从当前登录账号获取用户 ID。聊天消息按账号、成员、会话查询；跨会话记忆按账号、成员查询，避免把其他人的内容带进本轮回答。

`HealthFiles.search` 遍历该成员目录内的 Markdown 文件，按二级标题切分片段。检索文本由本轮问题、当前会话的全部历史用户问题（过长时分段压缩）和选中的相关记忆组成。检索文本与档案片段使用 Lucene 中文分词器提取词项，按词项在候选片段中的稀有程度打分，并提高本轮问题词项的权重；最后保留分数大于零的前 8 个片段。每个入选片段连同相对文件路径、日期进入本轮提示词，回答完成后路径也会作为引用来源返回。这里没有向量化、向量数据库或 Spring AI 的 `EmbeddingModel`。完整步骤见 [`HealthController.ask` 逐步解读](HealthController问答方法逐步解读.md)。

控制器把回答规则放在 `system` 消息中：仅依据本轮提供的档案片段回答，标明日期与来源，不能把聊天自述当作已核实的档案事实。近期原始消息转换为 Spring AI 的 `UserMessage` 和 `AssistantMessage`；会话摘要、相关记忆、本轮问题和档案片段放入本轮 `user` 提示词。最后调用 `HealthModels.stream` 生成回答。

历史消息由 `HealthMemoryService.conversation` 读取：先找该会话的摘要，再取摘要之后最近最多 24 条原始消息。控制器过滤空内容和非 `user`、`assistant` 角色，单条消息最多向模型提供前 2000 字，然后转换成对应的 Spring AI 消息类型。摘要与跨会话记忆只帮助理解指代和偏好；提示词明确要求模型不要把它们当作档案证据，也不要执行档案片段中的指令。系统规则还要求区分报告原文、家属补充与整理判断，不把“可能”“待排”写成确诊。

如果检索不到相关档案片段，控制器直接返回“资料不足”的提示，**不会调用回答模型**。有片段时，后端逐块发送回答，完成后保存问题、完整回答和引用来源，再发送包含 `conversationId`、`sources` 的 `done` 事件。这里的档案检索由项目文件逻辑实现，代码没有使用 Spring AI 的向量库或 `EmbeddingModel`。

模型生成过程中发生运行时异常时，控制器发送 `error` 事件。前端收到 `done` 才认为本轮回答完成，随后更新会话编号和引用来源；发生错误时显示提示。问答完成后，后端再尝试异步更新摘要和提取记忆。这个顺序让前端可以先看到回答，记忆维护随后进行。

## 报告识别的调用链

报告上传逻辑位于 [`HealthFiles.upload`](../ruoyi-admin/src/main/java/com/ruoyi/health/HealthFiles.java)。可复制文字的 PDF 先通过 PDFBox 提取文本；图片或扫描 PDF 则调用 `HealthModels.readImage` 转写，扫描 PDF 会先渲染为图片。图片识别需要选用支持视觉输入的 `REPORT` 模型，代码对 `DEEPSEEK` 配置直接拒绝这条路径。

上传文件目前只接受 PDF、JPG、PNG，并按文件头判断实际格式。普通 PDF 最多处理 10 页；如果 PDFBox 提取出的文本少于 40 字，代码将其视为需要视觉识别的扫描 PDF。扫描 PDF 最多处理 3 页，每页先渲染成 PNG，再分别调用 `readImage`。图片转写提示词要求逐字抄录，看不清的地方写 `[不清]`，不补写也不下诊断。

得到报告文字后，项目调用 `HealthModels.ask`，要求模型提取 `summary`、`observations` 和 `todos`，再解析为待确认草稿。模型输出不会直接写成最终健康档案；用户需要核对草稿后再确认。相关细节见 [`HealthFiles.upload`](../ruoyi-admin/src/main/java/com/ruoyi/health/HealthFiles.java) 和 [`HealthFiles.confirm`](../ruoyi-admin/src/main/java/com/ruoyi/health/HealthFiles.java)。

这一阶段的 JSON 是**提示词要求模型生成的文本**，然后由项目的 `parseJson` 用 Jackson 解析，并非 Spring AI 自动把结果映射成 Java 对象。代码从输出的第一个 `{` 到最后一个 `}` 截取 JSON；数值指标只接受符合数字格式的 `value`。草稿同时保存报告原文转写和结构化结果，供用户核对、编辑。确认后，`HealthFiles.confirm` 才把内容写进 Markdown 健康档案，并更新相关趋势数据。

## 聊天记忆中的模型调用

[`HealthMemoryService`](../ruoyi-admin/src/main/java/com/ruoyi/health/HealthMemoryService.java) 复用当前 `CHAT` 模型的同步 `ask` 调用，完成三件事：

1. 回答前，从当前账号和成员最近最多 200 条记忆中，请模型选出最多 5 条与问题相关的记忆；筛选失败时继续普通问答。
2. 回答入库后，将较早的会话消息合并成摘要，让后续对话保留必要上下文。
3. 从本轮用户原话中提取最多 3 条偏好或健康自述，供后续会话参考；健康自述标记为未核实，不作为档案事实。

这些任务使用提示词约束输出，并由项目代码解析和检查模型返回值。会话摘要和记忆提取在回答完成后异步尝试；失败不会改变已经生成的回答。记忆机制的保存范围与边界见[问答记忆机制](问答记忆机制.md)。

### 回答前：筛选相关记忆

`relevant` 从当前账号、成员最近最多 200 条记忆中构造候选列表，每条最多放入前 200 字。它让模型只返回最多 5 个记忆 ID 的 JSON 数组，再按 ID 回查候选列表，过滤无效、重复或超量的结果。无候选记忆时无需调用模型；筛选调用或解析失败时返回空列表，问答继续使用档案检索。这是一次额外的同步模型调用，发生在回答模型开始流式生成之前。

### 回答后：摘要与提取

`afterAnswer` 在完整问答入库后启动后台任务，分别调用 `summarize` 和 `extract`。摘要只处理同一会话中尚未被摘要覆盖的旧消息；当未摘要消息超过 24 条时，把较早的消息与旧摘要一起交给模型，并保留近期原始消息。摘要结果最多保存 3000 字，数据库仍保留原始问答。

`extract` 只把本轮用户原话交给模型分析，让它返回最多 3 条 `PREFERENCE` 或 `SELF_REPORT`。保存前代码检查类型、内容长度和重复项。提示词要求仅在能确认自述对象是所选成员时保存，并把健康自述保持为未核实信息。两个后台步骤分别捕获异常并记日志，不回滚已发送的回答。

这里的“结构化输出”主要依靠提示词要求 JSON，再由 Jackson 解析。`HealthMemoryService.modelArray` 会截取模型文本中的数组部分，兼容模型附带代码围栏的情况；项目没有在这些调用中使用 Spring AI 的结构化输出转换器。记忆机制的完整数据范围与处理边界见[问答记忆机制](问答记忆机制.md)。

## 阅读代码时要分清的边界

Spring AI 在这里提供 `ChatClient`、消息类型、多模态输入以及同步和流式调用接口。项目自己决定检索哪些档案片段、如何组织提示词、如何处理模型输出，以及何时持久化。理解这条边界，再看 `HealthModels`、`HealthController`、`HealthFiles` 和 `HealthMemoryService`，就能串起 Spring AI 在项目中的完整使用路径。

建议按以下顺序读代码：

1. 从 `HealthModels.client` 看服务商配置怎样变成 `ChatClient`，再看 `ask`、`stream`、`readImage` 三种调用。
2. 从 `HealthController.ask` 看检索结果、历史消息和系统规则如何进入 `stream`，以及模型片段如何变成 SSE。
3. 从 `HealthFiles.upload` 看图片输入、文字提取、JSON 草稿和人工确认的先后关系。
4. 从 `HealthMemoryService.relevant`、`summarize`、`extract` 看同一聊天模型如何承担问答以外的辅助任务。
