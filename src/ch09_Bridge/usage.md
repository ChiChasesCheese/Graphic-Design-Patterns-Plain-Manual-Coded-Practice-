# Bridge 模式 — 真实应用

核心：**把抽象和实现分离成两个独立的继承体系，通过组合连接，两侧可以独立扩展。**

---

## 1. Java — JDBC

JDBC 是 Bridge 模式最经典的教科书案例。
抽象层（`Connection`、`Statement`、`ResultSet`）和实现层（MySQL Driver、PG Driver）完全分离。

```java
// 抽象层（你写的代码）
Connection conn = DriverManager.getConnection(url, user, pass);
PreparedStatement ps = conn.prepareStatement("SELECT * FROM users");
ResultSet rs = ps.executeQuery();

// 实现层（Driver 厂商提供）
// MySQL:    com.mysql.cj.jdbc.ConnectionImpl
// Postgres: org.postgresql.jdbc.PgConnection
// Oracle:   oracle.jdbc.OracleConnectionWrapper

// 抽象和实现独立扩展：
// 新增 SQL 操作 → 扩展抽象层，Driver 不需要动
// 新增数据库支持 → 实现新 Driver，抽象层不需要动
```

---

## 2. Spring Data — Repository 抽象

Spring Data 把"数据访问操作"（抽象）和"存储实现"（实现）分离，
同一套 Repository 接口可以对接 JPA、MongoDB、Redis、Elasticsearch。

```java
// 抽象层：你定义的 Repository 接口
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByEmail(String email);
    // Spring Data 自动生成实现
}

// 切换实现：只改依赖，接口代码不动
// spring-data-jpa      → SQL 数据库（MySQL, Postgres）
// spring-data-mongodb  → MongoDB
// spring-data-redis    → Redis
// spring-data-elasticsearch → ES

// 抽象和实现各自独立扩展：
// 新增查询方法 → 只改 Repository 接口
// 换存储引擎   → 只换 spring-data-xxx 依赖
```

---

## 3. JavaScript — DOM 与渲染引擎

浏览器 DOM API 是 Bridge：
`document.createElement`、`element.appendChild` 是抽象层，
Chrome（Blink）、Firefox（Gecko）是实现层。
你的 JS 代码不关心底层渲染引擎。

React 把这个思想推进了一步：

```typescript
// React 抽象层（与平台无关）
const element = <div className="container"><Button>Click</Button></div>;

// 实现层（Bridge 的另一侧）：
// react-dom          → 浏览器 DOM
// react-native       → iOS/Android 原生组件
// react-three-fiber  → Three.js 3D 场景
// @react-pdf/renderer → PDF 文件

// 同一套组件代码，切换 renderer 就能渲染到不同目标
```

---

## 4. Python — `logging.Handler`

Python 日志系统：Logger（抽象）和 Handler（实现）是两个独立的继承体系，
通过 `addHandler()` 桥接。

```python
import logging

logger = logging.getLogger("myapp")  # 抽象层：Logger 体系

# 实现层：Handler 体系（独立扩展）
logger.addHandler(logging.StreamHandler())          # 输出到控制台
logger.addHandler(logging.FileHandler("app.log"))   # 输出到文件
logger.addHandler(logging.handlers.SysLogHandler()) # 输出到 syslog

# 可以同时桥接多个实现
# 新增 Handler 类型（如 SlackHandler）不影响 Logger 体系
# 新增 Logger 功能不影响 Handler 体系
logger.info("User logged in")
# → 同时写控制台、文件、syslog
```

---

## 5. Go — `io.Writer` / `io.Reader`

Go 标准库用接口 + 组合实现 Bridge，是最 Golang 风格的表达。

```go
// io.Writer 是抽象层（写入操作）
type Writer interface {
    Write(p []byte) (n int, err error)
}

// 实现层各自独立：
// os.File           → 写文件
// bytes.Buffer      → 写内存
// net.Conn          → 写网络
// gzip.Writer       → 压缩后写（还能套娃）

// 桥接：任何接受 io.Writer 的函数都能用任意实现
func writeJSON(w io.Writer, data any) error {
    return json.NewEncoder(w).Encode(data)
}

// 测试时用 bytes.Buffer，生产时用 os.File 或 http.ResponseWriter
writeJSON(os.Stdout, user)
writeJSON(responseWriter, user)
writeJSON(&buf, user)  // 单元测试
```

---

## Python 生态

Python 的 Bridge 模式常通过**组合 + Protocol**实现，把实现层抽象成可替换的 Protocol。

```python
from abc import ABC, abstractmethod
from typing import Protocol

# 1. 经典 Bridge：消息发送器
# 实现层（Implementor）
class MessageSender(Protocol):
    def send(self, to: str, content: str) -> bool: ...

class EmailSender:
    def send(self, to: str, content: str) -> bool:
        print(f"Email → {to}: {content}")
        return True

class SlackSender:
    def send(self, to: str, content: str) -> bool:
        print(f"Slack → #{to}: {content}")
        return True

class SMSSender:
    def send(self, to: str, content: str) -> bool:
        print(f"SMS → {to}: {content[:160]}")   # SMS 有长度限制
        return True

# 抽象层（Abstraction）
class Notification(ABC):
    def __init__(self, sender: MessageSender):   # Bridge：持有实现层引用
        self._sender = sender

    @abstractmethod
    def notify(self, recipient: str, event: str) -> None: ...

class AlertNotification(Notification):
    def notify(self, recipient: str, event: str) -> None:
        content = f"[ALERT] {event}"
        self._sender.send(recipient, content)

class ReportNotification(Notification):
    def __init__(self, sender: MessageSender, report_name: str):
        super().__init__(sender)
        self._report_name = report_name

    def notify(self, recipient: str, event: str) -> None:
        content = f"Report '{self._report_name}' ready: {event}"
        self._sender.send(recipient, content)

# 抽象层和实现层独立扩展
alert_via_email = AlertNotification(EmailSender())
alert_via_slack = AlertNotification(SlackSender())
report_via_sms  = ReportNotification(SMSSender(), "Monthly Sales")

alert_via_email.notify("ops@company.com", "CPU > 90%")
alert_via_slack.notify("ops-channel", "CPU > 90%")
report_via_sms.notify("+1234567890", "Generated at 2026-04-11")

# 2. 依赖注入 + Protocol — Python 中更常见的 Bridge 形式
class Repository(Protocol):
    def find_by_id(self, id: int) -> dict | None: ...
    def save(self, entity: dict) -> None: ...

class SQLRepository:
    def find_by_id(self, id: int) -> dict | None:
        # 实际：执行 SELECT 查询
        return {"id": id, "name": "Alice"}

    def save(self, entity: dict) -> None:
        # 实际：执行 INSERT/UPDATE
        print(f"SQL: saving {entity}")

class RedisRepository:
    def find_by_id(self, id: int) -> dict | None:
        # 实际：GET key
        return None

    def save(self, entity: dict) -> None:
        # 实际：SET key value EX ttl
        print(f"Redis: caching {entity}")

class UserService:
    def __init__(self, repo: Repository):   # Bridge：注入实现
        self._repo = repo

    def get_user(self, id: int) -> dict | None:
        return self._repo.find_by_id(id)

    def create_user(self, data: dict) -> None:
        self._repo.save(data)

# 运行时切换实现，不修改业务逻辑
svc = UserService(SQLRepository())     # 生产
svc = UserService(RedisRepository())   # 缓存层
```

> **Python 洞察**：Python 的依赖注入天然就是 Bridge——
> 构造函数接受一个 Protocol 参数，实现层可以在运行时替换。
> FastAPI、Django 等框架大量使用这种模式（数据库 session、存储后端、邮件发送器等）。

---

## 关键洞察

> Bridge 模式在实际工程里经常以"接口 + 依赖注入"的形式出现，
> 不一定有明显的"两个继承体系"。
> 识别 Bridge 的关键：**抽象和实现可以独立变化，互不影响**。
> 当你发现"如果没有这一层，新增功能需要改两边"，就该引入 Bridge。
