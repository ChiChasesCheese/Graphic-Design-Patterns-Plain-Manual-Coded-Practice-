# Chapter 17: Bridge

> 类型: 结构 | 难度: ★★★ | 原书: `src/ch09_Bridge/` | 前置: Ch04 (Adapter), Ch02 (Strategy)

---

## 模式速览

**问题**: 如果用继承来同时扩展"功能"和"实现"两个维度，类的数量会呈笛卡尔积式爆炸。假设有 3 种显示方式（Display、CountDisplay、ScrollDisplay）和 4 种渲染后端（终端、HTML、PDF、SVG），纯继承需要 3×4 = 12 个类，再加一个维度就变成 36 个。Bridge 的解法是：把两个维度分成独立的继承体系，用组合（has-a）代替继承（is-a）把它们连接起来。

```
        «Abstraction»           «Implementor»
        ┌───────────┐           ┌───────────────┐
        │  Display  │ ────────▶ │  DisplayImpl  │
        │           │  has-a    │ (抽象实现层)  │
        │ open()    │           │ rawOpen()     │
        │ print()   │           │ rawPrint()    │
        │ close()   │           │ rawClose()    │
        └─────┬─────┘           └──────┬────────┘
              │ extends                │ extends
        ┌─────┴──────┐         ┌──────┴────────────────┐
        │CountDisplay│         │   StringDisplayImpl   │
        │            │         │   HTMLDisplayImpl     │
        │multiDisplay│         │   ...                 │
        └────────────┘         └───────────────────────┘

        功能维度（Abstraction）       实现维度（Implementor）
        各自独立扩展                  各自独立扩展
        通过 has-a 桥接               互不干扰
```

**四个角色**:
- `Abstraction` (`Display`) — 功能层的顶层，持有 Implementor 的引用，调用 Implementor 的接口完成实际工作
- `RefinedAbstraction` (`CountDisplay`) — 功能层的扩展，在 Abstraction 基础上增加新功能，但不关心底层如何渲染
- `Implementor` (`DisplayImpl`) — 实现层的抽象接口，定义底层操作的原语（raw methods）
- `ConcreteImplementor` (`StringDisplayImpl`) — 实现层的具体实现，提供不同的渲染后端

**Bridge vs Adapter 的本质区别**:

Bridge 在**设计阶段**主动分离两个维度，Adapter 在**事后**弥补不兼容接口。判断法则：如果你在设计新系统时有意把功能和实现分开，用 Bridge；如果你在整合已有系统、修补接口不匹配，用 Adapter。Bridge 强调"分而治之"；Adapter 强调"将就一下"。

---

## 本章新语言特性

| 特性 | Java | Python |
|------|------|--------|
| 运行时发现实现 | `ServiceLoader` (SPI 机制) | `importlib` + `entry_points` |
| 声明式插件注册 | `META-INF/services/` 文件 | `pyproject.toml` entry points |
| 接口约束 | 抽象类 / `interface` | `ABC`、`Protocol` |
| 动态加载模块 | `Class.forName()` | `importlib.import_module()` |

### `ServiceLoader` — 运行时发现实现类

Java 的 SPI（Service Provider Interface）机制允许把接口定义和实现完全解耦：调用方只知道接口，具体实现由 classpath 上的 JAR 包提供。这正是 Bridge 思想在工程层面的体现——抽象和实现在编译时互不依赖。

```java
// 1. 定义服务接口（Implementor 层）
public interface DisplayRenderer {
    void open(String content);
    void renderLine(String content);
    void close(String content);
}

// 2. 在 META-INF/services/com.example.DisplayRenderer 文件中声明实现类
// （每行一个全限定类名，打包到 JAR 时自动被 ServiceLoader 发现）

// 3. 运行时发现所有实现
ServiceLoader<DisplayRenderer> loader =
    ServiceLoader.load(DisplayRenderer.class);

for (DisplayRenderer renderer : loader) {
    // 无需知道具体类，只面向接口操作
    System.out.println("发现渲染器: " + renderer.getClass().getSimpleName());
}
```

JDBC 的 `DriverManager` 就是这一机制的标准实践：JDK 定义 `java.sql.Driver` 接口，各数据库厂商在自己的 JAR 包里提供实现，用户只需把 JAR 加入 classpath，`DriverManager.getConnection()` 自动找到合适的 Driver。

### Python `importlib` — 动态模块加载

Python 的 `importlib` 提供程序化的模块导入能力，可以在运行时根据字符串名称加载模块和类，实现同样的插件机制：

```python
import importlib

# 根据配置字符串动态加载实现类
def load_renderer(class_path: str):
    """
    class_path 格式: "module.path.ClassName"
    例如: "renderers.html.HtmlRenderer"
    """
    module_path, class_name = class_path.rsplit(".", 1)
    module = importlib.import_module(module_path)
    return getattr(module, class_name)()

# 从配置文件读取实现类名，不硬编码具体类
renderer = load_renderer("renderers.terminal.TerminalRenderer")
```

---

## Java 实战: JDBC + `java.util.logging`

### JDBC: 数据库层的 Bridge

JDBC 是教科书级别的 Bridge 应用。`java.sql` 包定义 Abstraction 层（`Connection`、`Statement`、`ResultSet`），数据库厂商实现 Implementor 层（`OracleDriver`、`MysqlDriver`），应用代码完全面向抽象层编程：

```java
import java.sql.*;

/**
 * JDBC Bridge 结构:
 *   Abstraction  = Connection / Statement / ResultSet（java.sql 标准接口）
 *   Implementor  = Driver（每个数据库厂商实现此接口）
 *
 * 应用代码只依赖 java.sql，切换数据库只需换 JDBC URL 和 JAR，
 * 业务逻辑零修改——这正是 Bridge 的核心价值。
 */
public class JdbcBridgeDemo {

    // 用接口类型声明，与具体数据库实现无关
    private final String jdbcUrl;
    private final String user;
    private final String password;

    public JdbcBridgeDemo(String jdbcUrl, String user, String password) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
    }

    public void queryUsers() throws SQLException {
        // getConnection() 内部用 ServiceLoader 发现 Driver 实现
        // 调用方只看到 Connection 接口，不知道底层是哪个数据库
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, name FROM users LIMIT 10")) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                System.out.printf("用户 [%d]: %s%n", id, name);
            }
        }
        // 所有资源通过 Abstraction 接口操作，切换数据库无需修改此方法
    }

    public static void main(String[] args) throws SQLException {
        // 切换数据库只需改这一行 URL，业务代码不变——Bridge 的价值所在
        var h2Demo = new JdbcBridgeDemo(
            "jdbc:h2:mem:test",   // H2 内存数据库（测试用）
            "sa", ""
        );

        // 若要切换到 MySQL，只需换 URL 和 JAR：
        // "jdbc:mysql://localhost:3306/mydb"
        // 若要切换到 PostgreSQL：
        // "jdbc:postgresql://localhost:5432/mydb"

        h2Demo.queryUsers();
    }
}
```

**ServiceLoader 如何发现 Driver**: 每个 JDBC Driver JAR 包在 `META-INF/services/java.sql.Driver` 文件中声明自己的实现类名。`DriverManager` 在静态初始化时调用 `ServiceLoader.load(Driver.class)` 遍历所有可用的 Driver，并注册它们。这是 Bridge 模式在 Java 生态的标准工程实践。

### `java.util.logging`: Handler × Formatter 的双维 Bridge

JDK 的日志框架展示了 Bridge 的双维组合威力：`Handler` 控制"日志输出到哪里"，`Formatter` 控制"日志如何格式化"，两者正交，可以自由搭配：

```java
import java.util.logging.*;

/**
 * logging Bridge 结构:
 *
 *   功能维度（Abstraction）:
 *     Handler ─── 控制日志输出到哪里
 *       ├── ConsoleHandler   → 标准错误流
 *       ├── FileHandler      → 文件（支持滚动）
 *       ├── SocketHandler    → 网络
 *       └── MemoryHandler    → 内存环形缓冲
 *
 *   实现维度（Implementor）:
 *     Formatter ─── 控制日志格式
 *       ├── SimpleFormatter  → 人类可读的单行格式
 *       └── XMLFormatter     → 结构化 XML（机器解析用）
 *
 *   Handler has-a Formatter：handler.setFormatter(formatter)
 *   这正是 Bridge 的桥接点——两个维度通过 has-a 组合，各自独立变化。
 */
public class LoggingBridgeDemo {

    public static void main(String[] args) throws Exception {
        Logger logger = Logger.getLogger("bridge.demo");
        logger.setUseParentHandlers(false);   // 不继承父 logger 的 Handler

        // 组合 1：控制台 × 简单格式——开发阶段常用
        var consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new SimpleFormatter());   // 设置格式（桥接点）
        logger.addHandler(consoleHandler);

        // 组合 2：文件 × XML 格式——生产环境结构化日志
        var fileHandler = new FileHandler("app.%u.log");
        fileHandler.setFormatter(new XMLFormatter());         // 同一 Handler，换格式
        logger.addHandler(fileHandler);

        // 组合 3：自定义 Formatter——只需实现 Formatter 抽象类
        var jsonHandler = new ConsoleHandler();
        jsonHandler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                // 自定义 JSON 格式，无需修改 Handler
                return String.format(
                    "{\"level\":\"%s\",\"msg\":\"%s\",\"ts\":%d}%n",
                    record.getLevel(),
                    record.getMessage(),
                    record.getMillis()
                );
            }
        });
        logger.addHandler(jsonHandler);

        // 所有 Handler 同时接收同一条日志，各自按自己的格式输出
        logger.info("Bridge 模式演示：同一消息，三种输出");
        logger.warning("更换后端不影响业务代码");
    }
}
```

### 用 ServiceLoader 实现自定义渲染插件系统

结合原书的 `Display`/`DisplayImpl` 结构，用 ServiceLoader 实现可扩展的渲染插件系统：

```java
import java.util.ServiceLoader;

// ── Implementor 接口（供第三方实现）──────────────────────────────────────
public interface DisplayImpl {
    /** 打开显示，输出头部 */
    void rawOpen(String content);
    /** 输出内容行 */
    void rawPrint(String content);
    /** 关闭显示，输出尾部 */
    void rawClose(String content);
    /** 实现的名称，用于选择 */
    String name();
}

// ── Abstraction（持有 Implementor 引用）──────────────────────────────────
public class Display {
    // Bridge 的核心：通过接口持有 Implementor，不依赖具体实现
    private final DisplayImpl impl;

    public Display(DisplayImpl impl) {
        this.impl = impl;
    }

    public void open(String content)  { impl.rawOpen(content); }
    public void print(String content) { impl.rawPrint(content); }
    public void close(String content) { impl.rawClose(content); }

    public final void display(String content) {
        open(content);
        print(content);
        close(content);
    }
}

// ── RefinedAbstraction（扩展功能维度，不关心实现维度）──────────────────
public class CountDisplay extends Display {

    public CountDisplay(DisplayImpl impl) {
        super(impl);
    }

    /** 重复显示内容 n 次——新增功能，与实现无关 */
    public void multiDisplay(String content, int times) {
        open(content);
        for (int i = 0; i < times; i++) {
            print(content);
        }
        close(content);
    }
}

// ── 内置 ConcreteImplementor ──────────────────────────────────────────────
public class TerminalDisplayImpl implements DisplayImpl {
    @Override
    public void rawOpen(String content) {
        System.out.println("+" + "-".repeat(content.length()) + "+");
    }

    @Override
    public void rawPrint(String content) {
        System.out.println("|" + content + "|");
    }

    @Override
    public void rawClose(String content) {
        System.out.println("+" + "=".repeat(content.length()) + "+");
    }

    @Override
    public String name() { return "terminal"; }
}

// ── 插件发现：通过 ServiceLoader 在运行时找到所有 DisplayImpl 实现 ────────
public class DisplayFactory {

    /**
     * 根据名称查找 Implementor 实现。
     * ServiceLoader 自动扫描 classpath 上所有声明了此服务的 JAR 包，
     * 无需硬编码具体类名——这是 Bridge 在工程上的完整实现。
     */
    public static DisplayImpl findImpl(String name) {
        return ServiceLoader.load(DisplayImpl.class)
            .stream()
            .map(ServiceLoader.Provider::get)
            .filter(impl -> impl.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "未找到渲染器: " + name
            ));
    }

    public static void main(String[] args) {
        // 从配置读取实现名称（可来自环境变量、配置文件、命令行参数）
        String rendererName = System.getProperty("renderer", "terminal");

        DisplayImpl impl = findImpl(rendererName);

        // Abstraction 层：不关心 impl 具体是哪个类
        var display = new Display(impl);
        display.display("Hello, Bridge!");

        // RefinedAbstraction 层：扩展功能，同样不关心 impl
        var countDisplay = new CountDisplay(impl);
        countDisplay.multiDisplay("Bridge 模式", 3);
    }
}
```

---

## Python 实战: DB-API 2.0 + `logging` + `importlib`

### DB-API 2.0 (PEP 249): Python 的 JDBC

PEP 249 定义了 Python 数据库适配器的标准 API，与 JDBC 思路完全一致：应用代码面向统一接口，数据库驱动提供具体实现：

```python
"""
DB-API 2.0 Bridge 结构:

  Abstraction  = PEP 249 规定的 connection/cursor 接口（文档即契约）
  Implementor  = 各驱动模块（sqlite3 / psycopg2 / mysql-connector-python）

  切换数据库只需换 connect() 调用，业务查询代码完全不变。
"""

from typing import Any

def query_users(conn: Any, limit: int = 10) -> list[dict]:
    """
    与具体数据库无关的查询函数。
    conn 可以是 sqlite3.Connection、psycopg2.connection、
    mysql.connector.connection 等任意 DB-API 2.0 兼容连接。
    """
    cursor = conn.cursor()
    cursor.execute("SELECT id, name FROM users LIMIT ?", (limit,))

    # 通用列名提取（cursor.description 是 DB-API 2.0 标准属性）
    columns = [desc[0] for desc in cursor.description]
    rows = cursor.fetchall()
    return [dict(zip(columns, row)) for row in rows]


# ── 使用 sqlite3 驱动（标准库，零依赖）──────────────────────────────────
import sqlite3

with sqlite3.connect(":memory:") as sqlite_conn:
    # 建表并插入测试数据
    sqlite_conn.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT)")
    sqlite_conn.executemany(
        "INSERT INTO users VALUES (?, ?)",
        [(1, "Alice"), (2, "Bob"), (3, "Charlie")]
    )
    users = query_users(sqlite_conn)   # 调用同一个 query_users 函数
    print("sqlite3:", users)


# ── 切换到 PostgreSQL（psycopg2）——query_users 代码完全不变 ──────────────
# import psycopg2
# pg_conn = psycopg2.connect("host=localhost dbname=mydb user=postgres")
# users = query_users(pg_conn)        # 完全相同的调用

# ── 切换到 MySQL（mysql-connector-python）——同样不变 ──────────────────────
# import mysql.connector
# mysql_conn = mysql.connector.connect(host="localhost", database="mydb")
# users = query_users(mysql_conn)
```

### `logging`: Handler × Formatter 的 Python 版

Python 的 `logging` 模块与 JDK 的日志框架结构完全对应，同样是双维 Bridge：

```python
import logging
import json
from datetime import datetime

"""
logging Bridge 结构（与 Java 完全对应）:

  功能维度 Handler（输出到哪里）:
    StreamHandler   → sys.stderr / sys.stdout
    FileHandler     → 文件
    RotatingFileHandler → 滚动文件
    SocketHandler   → 网络
    MemoryHandler   → 内存缓冲

  实现维度 Formatter（如何格式化）:
    logging.Formatter → 自定义 format 字符串
    自定义子类        → 结构化输出（JSON、CEF 等）

  handler.setFormatter(formatter) 是桥接点。
"""

# ── 自定义 JSON Formatter（实现维度的具体类）─────────────────────────────
class JsonFormatter(logging.Formatter):
    """将日志记录格式化为 JSON，适合机器解析（ELK、Splunk 等）"""

    def format(self, record: logging.LogRecord) -> str:
        log_entry = {
            "timestamp": datetime.utcfromtimestamp(record.created).isoformat() + "Z",
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "module": record.module,
            "line": record.lineno,
        }
        # 如果有异常信息，附加到 JSON
        if record.exc_info:
            log_entry["exception"] = self.formatException(record.exc_info)
        return json.dumps(log_entry, ensure_ascii=False)


# ── 组装 Logger：自由组合 Handler 和 Formatter ────────────────────────────
def setup_logger(name: str) -> logging.Logger:
    logger = logging.getLogger(name)
    logger.setLevel(logging.DEBUG)

    # 组合 1：控制台 × 简单格式（开发环境）
    console_handler = logging.StreamHandler()
    console_handler.setLevel(logging.DEBUG)
    console_handler.setFormatter(
        logging.Formatter("%(asctime)s [%(levelname)s] %(message)s")
    )

    # 组合 2：文件 × JSON 格式（生产环境，发送到日志平台）
    file_handler = logging.FileHandler("app.jsonl")
    file_handler.setLevel(logging.WARNING)
    file_handler.setFormatter(JsonFormatter())   # 换格式，Handler 不变

    logger.addHandler(console_handler)
    logger.addHandler(file_handler)
    return logger


logger = setup_logger("bridge.demo")
logger.debug("调试信息，只到控制台")
logger.warning("警告，同时写入 JSON 日志文件")
logger.error("错误，两个 Handler 都接收")
```

### `importlib` 实现插件架构

用 `importlib` 实现与 Java ServiceLoader 等价的运行时插件发现机制：

```python
import importlib
import importlib.metadata
from abc import ABC, abstractmethod

# ── Implementor 抽象基类 ──────────────────────────────────────────────────
class DisplayImpl(ABC):
    """所有渲染后端必须实现此接口"""

    @abstractmethod
    def raw_open(self, content: str) -> None: ...

    @abstractmethod
    def raw_print(self, content: str) -> None: ...

    @abstractmethod
    def raw_close(self, content: str) -> None: ...

    @property
    @abstractmethod
    def name(self) -> str: ...


# ── Abstraction ──────────────────────────────────────────────────────────
class Display:
    """
    功能层顶层，通过 has-a 持有 Implementor。
    所有子类共享同一个桥，只扩展功能，不关心实现细节。
    """

    def __init__(self, impl: DisplayImpl):
        self._impl = impl   # 桥接点：持有 Implementor 引用

    def open(self, content: str) -> None:   self._impl.raw_open(content)
    def print_line(self, content: str) -> None: self._impl.raw_print(content)
    def close(self, content: str) -> None:  self._impl.raw_close(content)

    def display(self, content: str) -> None:
        self.open(content)
        self.print_line(content)
        self.close(content)


# ── RefinedAbstraction ────────────────────────────────────────────────────
class CountDisplay(Display):
    """扩展功能维度：增加重复显示能力。与实现维度完全解耦。"""

    def multi_display(self, content: str, times: int) -> None:
        self.open(content)
        for _ in range(times):
            self.print_line(content)
        self.close(content)


# ── 内置 ConcreteImplementor ──────────────────────────────────────────────
class TerminalImpl(DisplayImpl):
    def raw_open(self, content: str) -> None:
        print("+" + "-" * len(content) + "+")

    def raw_print(self, content: str) -> None:
        print("|" + content + "|")

    def raw_close(self, content: str) -> None:
        print("+" + "=" * len(content) + "+")

    @property
    def name(self) -> str:
        return "terminal"


# ── 插件发现：importlib.metadata.entry_points ─────────────────────────────
def load_impl_by_name(impl_name: str) -> DisplayImpl:
    """
    通过 pyproject.toml 的 entry_points 机制发现插件：

    [project.entry-points."display.impl"]
    terminal = "mypackage.impl:TerminalImpl"
    html     = "mypackage.impl:HtmlImpl"

    安装包后（pip install -e .），entry_points 自动注册，
    无需硬编码类名——与 Java ServiceLoader 等价。
    """
    eps = importlib.metadata.entry_points(group="display.impl")
    for ep in eps:
        if ep.name == impl_name:
            cls = ep.load()   # 动态加载类
            return cls()

    # 回退：直接用 importlib 动态导入（适合开发阶段）
    raise ValueError(f"未找到实现: {impl_name}，可用: {[e.name for e in eps]}")


def load_impl_dynamic(class_path: str) -> DisplayImpl:
    """
    备用方案：直接按类路径动态加载，不依赖 entry_points。
    class_path 格式: "module.ClassName"，例如 "__main__.TerminalImpl"
    """
    module_path, class_name = class_path.rsplit(".", 1)
    module = importlib.import_module(module_path)
    cls = getattr(module, class_name)
    return cls()


# ── 演示 ──────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    impl = TerminalImpl()   # 实际项目中从配置读取并动态加载

    # Abstraction 层
    d = Display(impl)
    d.display("Hello, Bridge!")

    # RefinedAbstraction 层：扩展功能，复用同一实现
    cd = CountDisplay(impl)
    cd.multi_display("Python Bridge", 3)
```

---

## 两种哲学

| 维度 | Java | Python |
|------|------|--------|
| 接口定义 | `abstract class` / `interface` 强制约束 | `ABC` + `@abstractmethod` 或 `Protocol` |
| 插件注册 | `META-INF/services/` 文件（编译期打包） | `pyproject.toml` entry_points（安装期注册） |
| 插件发现 | `ServiceLoader.load(Interface.class)` | `importlib.metadata.entry_points(group=...)` |
| 动态加载 | `Class.forName("com.example.Impl")` | `importlib.import_module("module")` |
| 类型安全 | 编译期检查接口实现的完整性 | 运行时检查（`isinstance`、`ABC` 报错） |
| 典型案例 | JDBC、logging、JAXP、JAX-RS | DB-API 2.0、logging、SQLAlchemy 方言 |

**核心共识**: 两种语言达到同一个目标——让 Abstraction 层在编译时不依赖任何 ConcreteImplementor，实现按需替换。Java 通过类型系统和 SPI 文件在编译/打包阶段建立契约；Python 通过 Duck Typing 和 entry_points 在安装/运行阶段建立契约。前者更严格（编译期报错），后者更灵活（运行时适配）。

**与 Strategy 的差异**: Strategy（Ch02）用组合替换"算法"，通常在同一维度内切换不同策略，Abstraction 本身没有独立的继承体系。Bridge 有两个独立扩展的维度，两者的继承体系都可以独立生长。如果只有一个 Abstraction 类但有多种算法，用 Strategy；如果 Abstraction 和 Implementor 都需要扩展，用 Bridge。

---

## 动手练习

**17.1 Java — 消息推送系统**

设计一个消息推送系统，有两个独立变化的维度：
- 消息类型（Abstraction）：`TextMessage`、`RichMessage`（包含标题+正文）、`AlertMessage`（紧急提醒）
- 推送渠道（Implementor）：`EmailSender`、`SmsSender`、`PushNotificationSender`

```java
// 起始骨架
interface MessageSender {
    void sendRaw(String recipient, String content);
    String channelName();
}

abstract class Message {
    protected final MessageSender sender;
    public Message(MessageSender sender) { this.sender = sender; }
    public abstract void send(String recipient);
}

// 实现 TextMessage、RichMessage、AlertMessage 和三种 Sender
// 验证：3×3 = 9 种组合，类只有 3+3+1 = 7 个（不用 Bridge 需要 9 个）
```

**17.2 Java — ServiceLoader 插件**

在 17.1 基础上，用 ServiceLoader 实现渠道插件化：
- 在 `META-INF/services/MessageSender` 中声明 `EmailSender`
- 实现 `SenderRegistry`，通过 `ServiceLoader` 动态发现所有可用渠道
- 支持通过渠道名称（如 `"email"`）查找对应实现
- 新增 `WechatSender` 时，只需添加实现类和注册文件，无需修改 `SenderRegistry`

**17.3 Python — 图表渲染器**

实现一个图表渲染系统：
- Abstraction 维度：`BarChart`、`LineChart`、`PieChart`（各自有不同的数据处理逻辑）
- Implementor 维度：`SvgRenderer`（输出 SVG 字符串）、`AsciiRenderer`（终端输出）、`JsonRenderer`（输出数据 JSON，供前端渲染）

要求：用 `ABC` 定义 `ChartRenderer` 抽象基类，用 `importlib` 实现按配置字符串动态加载渲染器。

**17.4 思考题 — 识别真正的 Bridge**

分析以下场景，判断是否是 Bridge 模式，说明理由：

1. Java 中 `BufferedReader` 包装 `FileReader`——它们是 Bridge 还是 Decorator？（提示：思考两个继承体系是否各自独立变化）
2. `logging.Handler.setFormatter()` 确实是 Bridge——如果把 Formatter 直接内嵌到 Handler 类里，会发生什么？需要多少个类？
3. 你的项目中有哪些地方存在"功能扩展"和"实现替换"两个独立变化的维度？它们目前用的是继承还是 Bridge？

---

## 回顾与连接

**三种"组合型"模式的本质区分**:

- **Bridge vs Adapter (Ch04)**: Adapter 事后弥补接口不匹配，修复已有系统的对接问题，不改变两侧的设计；Bridge 在设计阶段主动引入两个独立维度，让两侧各自演化。如果你在设计新功能，用 Bridge；如果你在整合第三方库，用 Adapter。

- **Bridge vs Strategy (Ch02)**: Strategy 用组合替换单个算法，Abstraction 层通常只有一种（Context 不需要继承层次）；Bridge 有两个独立的继承体系，两个维度都可以扩展。实践中区分两者的方法：Abstraction 层是否也需要多种变体？如果是，考虑 Bridge。

- **Bridge vs Decorator (Ch09)**: Decorator 和 Bridge 都用组合，但意图不同。Decorator 是"叠加"——多个 Decorator 可以链式包裹同一个对象，每层添加功能；Bridge 是"替换"——一个 Abstraction 只对应一个 Implementor，目的是选择不同实现，而非叠加。

**设计要点**:

1. **组合优于继承**: Bridge 最直接的好处是把 N×M 个子类压缩为 N+M 个类。每新增一种功能变体，只加 1 个 Abstraction 子类；每新增一种实现后端，只加 1 个 Implementor 子类。继承则需要 N×M 的子类矩阵。

2. **桥接点是 has-a**: Abstraction 构造函数接收 Implementor 引用，这是 Bridge 的标志性代码结构。这个引用既可以在构造时注入（DI），也可以在运行时切换（如果 `setImpl()` 是公开的）。

3. **接口稳定性**: Implementor 接口一旦定稳就不应轻易改变，否则所有 ConcreteImplementor 都要修改。设计 Implementor 接口时要尽量定义"原语"操作（细粒度），让 Abstraction 用这些原语组合出丰富的功能。

4. **Bridge 的代价**: 引入了额外的间接层，代码可读性稍有下降（功能和实现分散在两个类层次中）。当只有一种实现、且未来不打算扩展时，用 Bridge 是过度设计。只有当两个维度都需要独立变化时，Bridge 的收益才超过成本。

5. **SPI 是工程级 Bridge**: Java 的 `ServiceLoader` 和 Python 的 `entry_points` 把 Bridge 的"运行时选择实现"推进到"部署时选择实现"——不同的 JAR/wheel 包提供不同的实现，应用代码永远面向接口，真正做到开闭原则。
