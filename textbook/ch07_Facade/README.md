# Chapter 07: Facade

> 类型: 结构 | 难度: ★☆☆ | 原书: `src/ch15_Facade/` | 前置: Ch04 (Adapter)

## 模式速览

Facade（外观）模式为一个复杂的子系统提供一个简化的统一接口，让客户端无需了解子系统内部的细节就能完成高层操作。

```
Client
  │
  ▼
┌─────────────┐
│   Facade    │  ← 唯一对外暴露的入口
└──────┬──────┘
       │ 协调调用
  ┌────┴──────────────────┐
  ▼           ▼           ▼
SubsystemA  SubsystemB  SubsystemC
(DNS)       (Socket)    (HTTP parser)
```

**与 Adapter 的关键区别**:

| 模式 | 包装对象数量 | 核心意图 |
|------|-------------|----------|
| Adapter | 单个对象（1:1） | 接口转换，使不兼容接口可以协作 |
| Facade | 整个子系统（1:N） | 简化接口，隐藏子系统复杂性 |

Adapter 解决的是"接口不匹配"问题，Facade 解决的是"子系统太复杂"问题。两者结构相似，但动机截然不同。

---

## 本章新语言特性

### Java: package-private 访问修饰符 + Java 模块系统

Java 有四种访问级别，其中最容易被忽略的是 **package-private**（无修饰符），这正是实现 Facade 封装的天然工具：

```java
// 子系统内部类：package-private，包外不可见
class DnsResolver {               // 无 public，只有同包类能使用
    String resolve(String host) { // 同样 package-private
        return "93.184.216.34";
    }
}

class SocketManager {             // 子系统实现细节，对外不可见
    Socket connect(String ip, int port) throws IOException {
        return new Socket(ip, port);
    }
}

// Facade：public，对外暴露简化入口
public class HttpClient {         // 唯一公开的类
    private final DnsResolver dns     = new DnsResolver();    // 子系统A
    private final SocketManager socks = new SocketManager();  // 子系统B

    public String get(String url) throws IOException {
        // 将复杂流程封装为一次调用
        String ip = dns.resolve(extractHost(url));
        Socket s  = socks.connect(ip, 80);
        // ... 发送请求、读取响应
        return "response body";
    }
}
```

Java 9 引入的**模块系统**（`module-info.java`）将这种封装从包级别提升到模块级别，是语言层面的 Facade 机制：

```java
// module-info.java：声明模块的公开 API，隐藏所有其他包
module com.example.httpclient {
    // 只暴露 Facade 所在的包
    exports com.example.httpclient.api;

    // 以下子系统包对外完全不可见，模块外代码无法 import
    // com.example.httpclient.internal.dns      ← 隐藏
    // com.example.httpclient.internal.socket   ← 隐藏
    // com.example.httpclient.internal.parser   ← 隐藏
}
```

模块系统相当于在编译器层面强制执行 Facade 模式——子系统实现包被彻底封锁，外部代码即使通过反射也无法随意访问（`--add-opens` 显式授权除外）。

---

### Python: `__all__` + `__init__.py` 作为包级 Facade

Python 没有编译器级别的访问控制，但通过两种约定实现相同的封装效果：

**`_` 前缀约定**：单下划线表示"内部实现，请勿依赖"。这是约定，不是强制。

**`__all__` 列表**：控制 `from package import *` 时导出的名称。也控制代码补全工具和文档生成器所展示的公开 API。

**`__init__.py` 作为 Facade**：将深层实现细节重新导出为简洁的顶层接口。

```python
# 包结构：
# httpclient/
#   __init__.py       ← Facade 层
#   _dns.py           ← 子系统 A（下划线：内部实现）
#   _socket.py        ← 子系统 B（下划线：内部实现）
#   _parser.py        ← 子系统 C（下划线：内部实现）

# __init__.py 内容：
from ._dns    import DnsResolver     # 导入但不重导出（不在 __all__ 中）
from ._socket import SocketManager
from ._parser import HttpParser

__all__ = ["get", "post"]            # 只暴露高层函数，子系统类对外不可见

def get(url: str) -> str:
    """对外唯一接口：一行完成 HTTP GET。"""
    host = _extract_host(url)
    ip   = DnsResolver().resolve(host)    # 使用子系统A
    sock = SocketManager().connect(ip)    # 使用子系统B
    raw  = sock.recv(4096)
    return HttpParser().parse(raw)        # 使用子系统C

def post(url: str, data: dict) -> str:
    ...
```

```python
# 用户视角：只需知道顶层接口
import httpclient

response = httpclient.get("http://example.com")  # 无需了解 DNS、Socket、Parser
```

`__all__` 与 `module-info.java exports` 的对比：前者是运行时约定，后者是编译期强制。Python 的哲学是"我们都是有责任感的成年人"——下划线和 `__all__` 传达的是意图，而非硬性封锁。

---

## Java 实战: `URL.openStream()` + `java.util.logging`

### 源码解析

#### 1. `java.net.URL.openStream()` — 网络复杂性的 Facade

`URL.openStream()` 是 Java 标准库中最经典的 Facade 示例之一。一行代码背后隐藏的子系统：

```
URL.openStream()
    │
    ├── DNS 解析（InetAddress.getByName）
    ├── TCP Socket 创建与连接
    ├── HTTP 协议握手（写请求头）
    ├── 重定向处理（301/302 跟踪）
    ├── 响应头解析
    └── 返回响应 body 的 InputStream
```

```java
import java.net.URL;
import java.io.*;

// 用户代码：一行完成所有网络操作
URL url = new URL("https://example.com");
try (InputStream in = url.openStream()) {        // Facade 调用
    String content = new String(in.readAllBytes());
    System.out.println(content);
}

// 等价的"无 Facade"版本：需要手动操作每个子系统
import java.net.*;
import java.io.*;

// 步骤1：DNS 解析
InetAddress address = InetAddress.getByName("example.com");

// 步骤2：建立 TCP 连接
Socket socket = new Socket(address, 80);

// 步骤3：发送 HTTP 请求
PrintWriter writer = new PrintWriter(socket.getOutputStream());
writer.println("GET / HTTP/1.1");
writer.println("Host: example.com");
writer.println("Connection: close");
writer.println();
writer.flush();

// 步骤4：读取并解析响应
BufferedReader reader = new BufferedReader(
    new InputStreamReader(socket.getInputStream()));
String line;
StringBuilder response = new StringBuilder();
while ((line = reader.readLine()) != null) {
    response.append(line).append('\n');
}
// 步骤5：关闭连接
socket.close();
```

对比可见，`URL.openStream()` 将 20+ 行基础设施代码压缩为一行，同时处理了错误恢复、连接复用等生产级细节。

---

#### 2. `java.util.logging.Logger` — 日志子系统的 Facade

`java.util.logging` 的内部子系统相当复杂：`Handler`（输出目标）、`Formatter`（格式化）、`Filter`（过滤规则）、`Level`（级别控制）、`LogRecord`（日志记录对象）。`Logger` 是这个子系统的 Facade：

```java
import java.util.logging.*;

// Facade 使用方式：两行完成日志记录
Logger logger = Logger.getLogger("com.example.MyApp"); // 获取 Facade
logger.info("用户登录成功: userId=42");                 // 简单调用

// -------------------------------------------------------
// 子系统内部实际发生的事（Facade 隐藏的过程）：
// -------------------------------------------------------

// 1. 创建 LogRecord 对象（封装日志元数据）
LogRecord record = new LogRecord(Level.INFO, "用户登录成功: userId=42");
record.setSourceClassName("com.example.MyApp");
record.setSourceMethodName("login");
record.setMillis(System.currentTimeMillis());

// 2. 经过 Filter 链：检查是否需要记录
boolean publish = logger.getFilter() == null
    || logger.getFilter().isLoggable(record);

// 3. 经过 Level 检查
if (publish && Level.INFO.intValue() >= logger.getLevel().intValue()) {

    // 4. 遍历所有 Handler，每个 Handler 独立处理
    for (Handler handler : logger.getHandlers()) {
        // 5. Formatter 将 LogRecord 转为字符串
        String formatted = handler.getFormatter().format(record);
        // 6. Handler 写入目标（文件/控制台/网络）
        handler.publish(record);
    }
}
```

`Logger.info()` 这一行背后是一套完整的责任链——Facade 让调用者完全不必了解这套机制。

---

#### 3. `java.sql.DriverManager` — 数据库连接的 Facade

```java
import java.sql.*;

// Facade：一行建立数据库连接
Connection conn = DriverManager.getConnection(
    "jdbc:mysql://localhost:3306/mydb", "root", "password");

// DriverManager 内部协调的子系统：
// - 驱动发现（SPI 机制扫描 classpath 中的 Driver 实现）
// - 连接协议协商（TCP 握手 + MySQL 认证协议）
// - 字符集协商
// - 时区配置
// - 连接池分配（若使用连接池数据源）
```

---

### 现代重写：`module-info.java` 作为语言级 Facade

Java 9 模块系统让 Facade 模式从设计层面下沉到语言层面：

```java
// 文件：src/com.example.mail/module-info.java
module com.example.mail {
    // 只导出 Facade API 包
    exports com.example.mail.api;

    // 子系统实现包：不导出 = 对模块外完全不可见
    // com.example.mail.smtp        ← SMTP 协议实现
    // com.example.mail.template    ← 模板引擎
    // com.example.mail.attachment  ← 附件处理
    // com.example.mail.queue       ← 发送队列

    // 声明对外部模块的依赖
    requires java.net;
    requires jakarta.mail;
}
```

```java
// 文件：com/example/mail/api/MailService.java（唯一对外暴露的类）
package com.example.mail.api;

// 导入子系统（同模块内，可以访问）
import com.example.mail.smtp.SmtpSender;
import com.example.mail.template.TemplateEngine;
import com.example.mail.attachment.AttachmentBuilder;
import com.example.mail.queue.SendQueue;

public class MailService {

    private final SmtpSender     sender     = new SmtpSender();
    private final TemplateEngine templates  = new TemplateEngine();
    private final AttachmentBuilder attachments = new AttachmentBuilder();
    private final SendQueue      queue      = new SendQueue();

    // Facade 方法：将复杂的发送流程简化为一次调用
    public void sendWelcomeEmail(String to, String username) {
        var body = templates.render("welcome", Map.of("name", username));
        var msg  = attachments.buildMessage(to, "欢迎加入！", body);
        queue.enqueue(sender.prepare(msg));  // 异步发送
    }

    public void sendInvoice(String to, byte[] pdfBytes) {
        var msg = attachments.withPdf(to, "您的发票", pdfBytes);
        sender.sendNow(msg);  // 同步发送
    }
}
```

模块外的代码无论如何都无法直接 `import com.example.mail.smtp.SmtpSender`——模块边界是编译期强制的，连反射都被限制（除非显式 `opens`）。

---

## Python 实战: `requests` 库 + `subprocess.run()`

### 源码解析

#### 1. `requests.get()` — HTTP 的终极 Facade

`requests` 库的核心价值就是 Facade：将 Python 标准库中繁琐的 HTTP 操作封装为极简接口。

```python
import requests

# Facade：一行完成 HTTP GET
response = requests.get("https://api.github.com/users/python")
data = response.json()

# -------------------------------------------------------
# requests 内部协调的子系统层次（从上到下）：
# requests → urllib3 → http.client → socket → ssl
# -------------------------------------------------------

# 等价的"无 Facade"版本（使用 urllib）：
import urllib.request
import urllib.error
import json
import ssl

context = ssl.create_default_context()          # SSL 子系统
req = urllib.request.Request(
    "https://api.github.com/users/python",
    headers={"User-Agent": "Python/3.x"}        # 需要手动设置
)
try:
    with urllib.request.urlopen(req, context=context) as resp:
        raw = resp.read()                        # 读取字节
        encoding = resp.headers.get_content_charset("utf-8")  # 解析编码
        text = raw.decode(encoding)              # 手动解码
        data = json.loads(text)                  # 手动解析 JSON
except urllib.error.HTTPError as e:
    print(f"HTTP 错误: {e.code}")               # 手动处理 HTTP 错误
except urllib.error.URLError as e:
    print(f"网络错误: {e.reason}")              # 手动处理网络错误
```

`requests` 还隐藏了更多子系统：连接池管理（`urllib3.PoolManager`）、Cookie 持久化（`http.cookiejar`）、重定向跟踪、SSL 证书验证、超时控制、代理配置。这些全部是子系统，`requests` 是统一的 Facade。

---

#### 2. `subprocess.run()` — 进程管理的 Facade

`subprocess.run()` 是 Python 3.5 明确作为 Facade 引入的 API，官方文档原话是："The recommended approach to invoking subprocesses."（推荐方法，因为它封装了底层复杂性。）

```python
import subprocess

# Facade：一行运行外部命令并获取结果
result = subprocess.run(
    ["git", "log", "--oneline", "-5"],
    capture_output=True,
    text=True,
    check=True              # 非零退出码自动抛出异常
)
print(result.stdout)

# -------------------------------------------------------
# subprocess.run() 内部封装的子系统：
# -------------------------------------------------------

# 等价的底层 Popen 操作：
proc = subprocess.Popen(
    ["git", "log", "--oneline", "-5"],
    stdout=subprocess.PIPE,     # 创建管道
    stderr=subprocess.PIPE,     # 错误管道
    text=True
)
stdout, stderr = proc.communicate()   # 等待进程完成 + 读取所有输出
retcode = proc.wait()                 # 等待进程退出

if retcode != 0:                      # 手动检查退出码
    raise subprocess.CalledProcessError(retcode, proc.args, stdout, stderr)

# subprocess.run() 封装了：
# - Popen 对象生命周期管理
# - stdout/stderr 管道处理（防止死锁的 communicate() 机制）
# - 超时计时与进程终止
# - 退出码检查与异常抛出
# - 输入数据传递
```

---

#### 3. `logging` 模块高层函数 — 与 Java Logger 的对称

```python
import logging

# Facade 使用方式：直接调用模块级函数
logging.basicConfig(level=logging.INFO)
logging.info("用户登录成功: user_id=%d", 42)    # 一行记录日志

# -------------------------------------------------------
# 子系统内部（logging 模块协调的组件）：
# Logger → Filter → Handler → Formatter → 输出目标
# -------------------------------------------------------

# 等价的手动子系统配置：
logger    = logging.getLogger("root")            # 子系统A：Logger 树
handler   = logging.StreamHandler()              # 子系统B：Handler（输出到控制台）
formatter = logging.Formatter(                   # 子系统C：Formatter（格式）
    "%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
handler.setFormatter(formatter)
logger.addHandler(handler)
logger.setLevel(logging.INFO)
logger.info("用户登录成功: user_id=%d", 42)
```

`logging.basicConfig()` + `logging.info()` 是对整个日志子系统的 Facade——与 Java 的 `Logger.getLogger().info()` 在设计哲学上完全对称。

---

### Pythonic 重写：`__init__.py` + `__all__` 定义公开 API

```python
# 包结构：
# mailservice/
#   __init__.py      ← Facade（公开 API）
#   _smtp.py         ← 子系统：SMTP 发送
#   _template.py     ← 子系统：模板渲染
#   _attachment.py   ← 子系统：附件构建
#   _queue.py        ← 子系统：发送队列

# mailservice/_smtp.py
class _SmtpSender:
    """内部子系统：处理 SMTP 协议细节。"""
    def connect(self, host: str, port: int): ...
    def authenticate(self, user: str, pwd: str): ...
    def send(self, msg) -> bool: ...

# mailservice/_template.py
class _TemplateEngine:
    """内部子系统：Jinja2 模板渲染。"""
    def render(self, name: str, ctx: dict) -> str: ...

# mailservice/__init__.py（Facade 层）
from ._smtp       import _SmtpSender
from ._template   import _TemplateEngine
from ._attachment import _AttachmentBuilder
from ._queue      import _SendQueue

# __all__ 明确声明公开 API：只有这两个函数对外可见
__all__ = ["send_welcome", "send_invoice"]

# 子系统实例（私有，包外不应直接使用）
_sender      = _SmtpSender()
_templates   = _TemplateEngine()
_attachments = _AttachmentBuilder()
_queue       = _SendQueue()


def send_welcome(to: str, username: str) -> None:
    """发送欢迎邮件：Facade 方法，协调所有子系统。"""
    body = _templates.render("welcome", {"name": username})
    msg  = _attachments.build(to=to, subject="欢迎加入！", body=body)
    _queue.enqueue(msg)   # 异步发送，由队列协调 SMTP


def send_invoice(to: str, pdf_bytes: bytes) -> None:
    """发送发票邮件：带 PDF 附件的同步发送。"""
    msg = _attachments.with_pdf(to=to, subject="您的发票", pdf=pdf_bytes)
    _sender.connect("smtp.example.com", 587)
    _sender.send(msg)
```

```python
# 用户视角：导入即用，无需了解任何子系统
import mailservice

mailservice.send_welcome("user@example.com", "Alice")
mailservice.send_invoice("client@example.com", pdf_data)

# 即使尝试访问子系统，也只是违反约定，不会报错
# mailservice._sender.connect(...)  ← 可以访问，但 _ 前缀明确表示"别用"
# from mailservice import *         ← 只导出 send_welcome 和 send_invoice
```

---

## 两种哲学

| 维度 | Java | Python |
|------|------|--------|
| 封装机制 | `module-info.java` exports | `__all__` + `__init__.py` |
| 子系统隐藏 | package-private（编译期强制） | `_` 前缀（约定，不强制） |
| 封装强度 | 强制，模块外无法访问 | 君子协定，"我们都是成年人" |
| 违反成本 | 编译错误 | 无报错，但违反社区约定 |
| 文档化工具 | `javadoc` 不生成非 public 文档 | `__all__` 控制 `help()` 和补全 |
| 典型 Facade | `URL.openStream()`、`DriverManager` | `requests.get()`、`subprocess.run()` |

Java 的强制封装来自语言设计的严格性——"如果你想让某个类对外可见，必须显式声明"。Python 的封装来自社区文化——"下划线和 `__all__` 是给懂行的人看的信号，不需要编译器来当保姆"。

两种方式各有代价：Java 的模块系统能有效防止内部 API 被外部代码意外依赖（这在大规模重构时非常宝贵）；Python 的约定方式让代码更灵活，在调试和实验时可以直接访问"私有"子系统，不会被语言本身阻拦。

---

## 动手练习

**07.1 Java** — 为一个邮件发送子系统创建 Facade。子系统包含三个 package-private 类：`SmtpConnection`（负责 TCP 连接和 SMTP 握手）、`MailTemplate`（负责 HTML 模板渲染，支持变量替换）、`AttachmentEncoder`（负责将文件 Base64 编码为 MIME 附件）。Facade 类 `MailClient` 只暴露两个 public 方法：`sendText(String to, String subject, String body)` 和 `sendWithAttachment(String to, String subject, String templateName, File attachment)`。思考：哪些异常应该在 Facade 内部处理并转换为业务异常，哪些应该直接抛给调用者？

**07.2 Python** — 为一个文件处理包创建 `__init__.py` Facade。包内部有 `_csv_reader.py`、`_excel_reader.py`、`_json_reader.py` 三个模块，各自有不同的读取接口。在 `__init__.py` 中实现一个统一的 `read_file(path: str) -> list[dict]` 函数，根据文件扩展名自动分发到对应子系统，并用 `__all__` 确保只有 `read_file` 对外可见。

**07.3 跨语言思考** — Facade 什么时候会退化为 God Object 反模式？

> **参考思路**：当 Facade 开始承担**业务逻辑**而不仅仅是**协调调用**时，问题就出现了。合法的 Facade 只做三件事：调用子系统、组合结果、处理错误转换。一旦 Facade 开始包含 if/else 业务判断、持有跨请求的状态、或者子系统数量超过 5-7 个，应该考虑将其拆分为多个更聚焦的 Facade，或者引入领域层来承担业务逻辑。

---

## 回顾与连接

- **Adapter (Ch04) vs Facade (Ch07)**：Adapter 是 1:1 的接口转换——让一个对象的接口变得符合预期；Facade 是 1:N 的接口简化——让整个子系统对外呈现为一个统一入口。两者结构相似（都持有被包装对象），但意图不同：Adapter 的动机是"兼容"，Facade 的动机是"简化"。

- **Mediator (Ch21) vs Facade (Ch07)**：两者都协调多个对象，但方向相反。Facade 是单向的——客户端通过 Facade 访问子系统，子系统之间彼此不知道 Facade 的存在；Mediator 是双向的——各组件都知道 Mediator，并通过它相互通信。Facade 降低客户端与子系统的耦合，Mediator 降低组件之间的耦合。

- **Observer (Ch08)**：许多事件系统（EventBus、消息队列客户端）在对外暴露时都采用 Facade 模式，将订阅管理、线程调度、序列化等子系统隐藏在简洁的 `subscribe(event, handler)` / `publish(event, data)` 接口之后。下一章的 Observer 模式正是这类事件系统的核心机制。
