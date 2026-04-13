# Chapter 10: Builder

> 类型: 创建 | 难度: ★★☆ | 原书: `src/ch07_Builder/` | 前置: Ch02 (Strategy)

---

## 模式速览

**问题**: 你需要构造一个复杂对象，它有很多字段，部分可选、部分有默认值、部分之间有依赖关系。直接用构造方法，七八个参数排成一排，调用方根本看不懂每个位置代表什么含义；用多个重载构造方法，组合爆炸，维护噩梦。Builder 模式的解决方案是：把"怎么构造"这个过程单独抽象出来，让调用方通过一系列具名方法逐步设置字段，最后调用 `build()` 一次性生成目标对象。

```
                ┌─────────────┐
                │  Director   │  ← 知道"构造顺序"，不知道具体实现
                │ construct() │
                └──────┬──────┘
                       │ 依赖
                       ▼
              «interface»
              ┌──────────────────┐
              │    Builder       │
              │ makeTitle()      │
              │ makeString()     │
              │ makeItems()      │
              │ close()          │
              └────────┬─────────┘
                       │ 实现
          ┌────────────┴─────────────┐
          ▼                          ▼
  ┌───────────────┐        ┌──────────────────┐
  │  TextBuilder  │        │   HTMLBuilder    │
  │ (纯文本输出)  │        │  (HTML 文件输出) │
  └───────┬───────┘        └────────┬─────────┘
          │ 生成                    │ 生成
          ▼                         ▼
     String 文本               HTML 文件
```

**四个角色**:
- `Builder` — 定义构造步骤的抽象接口（或抽象类）
- `Director` — 持有 Builder，按固定顺序调用构造步骤，封装"构造逻辑"
- `TextBuilder` / `HTMLBuilder` (ConcreteBuilder) — 实现具体的构造动作，生成不同表现形式的产品
- 产品 (Product) — 最终被构造出来的复杂对象（本例中分别是 String 和 HTML 文件）

**GoF Builder 的核心价值**: Director 把"构造顺序"和"构造细节"彻底分离。同一份 Director 可以驱动完全不同的 Builder，生成格式迥异的产品，而 Director 内部的逻辑代码一行不变。

---

## 两种 Builder 形态

GoF 原书的 Builder 关注**步骤分离**（Director + Builder + ConcreteBuilder）；  
现代 Java 生态中更常见的是 **Fluent Builder**（链式调用），它解决的是另一个问题：**参数爆炸**。

```
GoF Builder:         Director → Builder ← ConcreteBuilder → Product
                     （强调步骤顺序，适合表示格式多样的场景）

Fluent Builder:      Product.builder().field1(v1).field2(v2).build()
                     （强调参数具名化，适合复杂构造方法的替代）
```

理解这两种形态的差异，是读懂本章的关键。

---

## 本章新语言特性

| 特性 | Java | Python |
|------|------|--------|
| 多行字符串 | text block `"""..."""`（Java 13+） | `"""..."""` 一直都有 |
| 不可变数据类 | `record` + 内嵌 Builder | `@dataclass(frozen=True)` |
| 字段默认值 | Builder 方法设初值 | `field(default=...)` |
| 构造后校验 | Compact Constructor | `__post_init__` |
| 关键字参数 | ❌ 无，Builder 来模拟 | ✅ 内置，`kw_only=True` 强制 |
| 内存紧凑布局 | 无直接对应 | `@dataclass(slots=True)`（3.10+） |

### Java text block — 多行字符串

```java
// 旧写法：字符串拼接，可读性差
String json = "{\n" +
              "  \"name\": \"Alice\",\n" +
              "  \"age\": 30\n" +
              "}";

// text block（Java 13+）：所见即所得，缩进自动对齐
String json = """
        {
          "name": "Alice",
          "age": 30
        }
        """;
// 规则：开头 """ 后必须换行；结尾 """ 的缩进决定公共前缀的删除量
```

### Python `@dataclass(kw_only=True, slots=True)`

```python
from dataclasses import dataclass, field

@dataclass(kw_only=True, slots=True)   # kw_only: 3.10+，slots: 3.10+
class HttpRequest:
    url: str                           # 必填，无默认值
    method: str = "GET"               # 可选，有默认值
    headers: dict = field(default_factory=dict)  # 可变默认值必须用 field()
    body: str | None = None
    timeout_seconds: int = 30

# kw_only=True 意味着只能用关键字参数构造，不能靠位置
req = HttpRequest(url="https://api.example.com", method="POST")
# slots=True 用 __slots__ 替代 __dict__，节省内存约 40%，属性访问更快
```

---

## Java 实战: StringBuilder · HttpRequest · ProcessBuilder

### `StringBuilder` — 顺序累积型 Builder

`StringBuilder` 是 GoF Builder 思想的最简实践：多次调用 `append()` 逐步构造，最后 `toString()` 产出结果。

```java
// 链式调用：每个 append() 返回 this，允许方法链
String result = new StringBuilder()
    .append("Hello")
    .append(", ")
    .append("World")
    .append("!")
    .toString();

// 实际上等价于 TextBuilder 里的逻辑——用 StringBuilder 充当"产品容器"
```

**为什么不用 `+` 拼接？** `String` 是不可变的，每次 `+` 都创建新对象。循环里拼接 1000 次就是 1000 个中间 `String` 对象。`StringBuilder` 内部维护可变 `char[]`，`append()` 只是追加，`toString()` 时才创建一个 `String`。编译器对简单 `+` 会自动优化成 `StringBuilder`，但循环体内的拼接需要手动使用。

### `java.net.http.HttpRequest.newBuilder()` — 参数具名型 Fluent Builder

Java 11 标准库中的 `HttpRequest` 是 Fluent Builder 的教科书示例。它用一系列具名方法模拟了 Python 的关键字参数：

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.time.Duration;

String body = """
        {"name": "Alice", "role": "admin"}
        """;

HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/users"))  // ≈ uri="..."
    .header("Content-Type", "application/json")         // ≈ header=...
    .header("Authorization", "Bearer token123")         // header 可以多次调用！
    .timeout(Duration.ofSeconds(30))                    // ≈ timeout=30
    .POST(BodyPublishers.ofString(body))                // ≈ method="POST"
    .build();                                           // 校验 + 构造不可变对象

// 对比 Python：无需 Builder，关键字参数天然解决
// import httpx
// response = httpx.post(
//     "https://api.example.com/users",
//     headers={"Content-Type": "application/json", "Authorization": "Bearer token123"},
//     json={"name": "Alice", "role": "admin"},
//     timeout=30
// )
```

`header()` 可以多次调用是关键——这正是 Builder 相比构造方法的独特能力：**同一步骤可以重复执行，产生累积效果**。普通构造方法无论如何不可能做到这一点。

### `ProcessBuilder` — 可变配置型 Builder

`ProcessBuilder` 是可变 Builder：构造后还能继续修改，不要求不可变。

```java
// 配置阶段：逐步设置进程参数
ProcessBuilder pb = new ProcessBuilder("python3", "script.py");
pb.directory(new java.io.File("/tmp"));          // 工作目录
pb.environment().put("DEBUG", "true");            // 环境变量
pb.redirectErrorStream(true);                     // 合并 stderr 到 stdout

// 产出阶段：start() 才真正启动进程
Process process = pb.start();
String output = new String(process.getInputStream().readAllBytes());
```

---

## Java 现代重写: `record` + 内嵌 Builder

`record` 是不可变的——所有字段必须在构造时一次性传入。字段一多，构造方法就成了参数爆炸的重灾区。内嵌 Builder 是标准解法：

```java
// record 声明产品结构，内嵌 Builder 类负责逐步收集参数
public record HttpRequest(
    String url,
    String method,
    Map<String, String> headers,
    String body,
    int timeoutSeconds,
    int retries
) {
    // Compact Constructor：record 独有，做校验和规范化，字段赋值由编译器自动完成
    public HttpRequest {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url 不能为空");
        }
        method = method.toUpperCase();  // 规范化：统一大写
    }

    // 静态工厂方法，入口更简洁
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String url;
        private String method = "GET";                              // 默认值在此定义
        private final Map<String, String> headers = new HashMap<>();
        private String body;
        private int timeoutSeconds = 30;
        private int retries = 3;

        // 每个 setter 返回 this，支持链式调用
        public Builder url(String url)           { this.url = url; return this; }
        public Builder method(String method)     { this.method = method; return this; }
        public Builder header(String k, String v) {
            this.headers.put(k, v);
            return this;                                            // 可多次调用，累积 header
        }
        public Builder body(String body)         { this.body = body; return this; }
        public Builder timeoutSeconds(int t)     { this.timeoutSeconds = t; return this; }
        public Builder retries(int r)            { this.retries = r; return this; }

        public HttpRequest build() {
            // Map.copyOf() 转不可变 Map，防止外部修改 Builder 后影响已构造的 record
            return new HttpRequest(url, method, Map.copyOf(headers), body, timeoutSeconds, retries);
        }
    }
}

// 使用：具名、可选、可累积——Builder 模拟了 Python 的关键字参数
var request = HttpRequest.builder()
    .url("https://api.example.com/users")
    .method("post")                               // Compact Constructor 会 toUpperCase()
    .header("Content-Type", "application/json")
    .header("Authorization", "Bearer token123")   // 同一步骤调用两次
    .body("{\"name\":\"Alice\"}")
    .build();

System.out.println(request.method());             // "POST"（已被规范化）
```

**`with` 方法——基于现有对象派生**:

当你只需要改一两个字段时，从头走一遍 Builder 太啰嗦。`with` 方法返回新实例，原对象不变：

```java
public record Config(String host, int port, boolean ssl, int poolSize) {
    // "函数式更新"：返回新实例，原对象永不改变
    public Config withHost(String host)   { return new Config(host, port, ssl, poolSize); }
    public Config withPort(int port)      { return new Config(host, port, ssl, poolSize); }
    public Config withSsl(boolean ssl)    { return new Config(host, port, ssl, poolSize); }
}

Config base    = new Config("localhost", 5432, false, 10);
Config prod    = base.withHost("prod.db.com").withSsl(true);    // base 不变
Config staging = base.withHost("staging.db.com");               // 同一 base 派生不同环境
// 等价于 Python 的 dataclasses.replace(base, host="prod.db.com", ssl=True)
// 等价于 Kotlin data class 的 base.copy(host="prod.db.com", ssl=true)
```

---

## Python 实战: argparse · Pydantic · dataclass

### `argparse.ArgumentParser` — 经典步骤累积型 Builder

`ArgumentParser` 是 Python 标准库里最接近 GoF Builder 原味的例子：先逐步 `.add_argument()`，最后 `.parse_args()` 产出配置对象。

```python
import argparse

# 构造阶段：逐步添加参数定义
parser = argparse.ArgumentParser(description="数据导入工具")
parser.add_argument("--host", required=True, help="数据库主机")
parser.add_argument("--port", type=int, default=5432)
parser.add_argument("--dry-run", action="store_true")
parser.add_argument("files", nargs="+", help="待导入的文件列表")  # 位置参数可以多次

# 产出阶段：解析命令行，生成 Namespace 对象
args = parser.parse_args()
print(f"连接 {args.host}:{args.port}, dry_run={args.dry_run}")

# 为什么这里需要 Builder 而不是构造函数？
# 因为参数定义是"逐步累积"的，且同一方法（add_argument）需要多次调用
# 这和 Java HttpRequest.header() 多次调用是完全相同的模式
```

### Pydantic `BaseModel` — 关键字参数 + 自动校验

Pydantic 展示了 Python 为什么通常**不需要** Builder：关键字参数 + 类型注解已经足够表达力。

```python
from pydantic import BaseModel, field_validator, HttpUrl
from typing import Literal

class HttpRequest(BaseModel):
    url: HttpUrl                          # 自动校验 URL 格式
    method: Literal["GET", "POST", "PUT", "DELETE"] = "GET"
    headers: dict[str, str] = {}
    body: str | None = None
    timeout_seconds: int = 30

    @field_validator("method", mode="before")
    @classmethod
    def normalize_method(cls, v: str) -> str:
        return v.upper()                  # 等价于 record Compact Constructor 的规范化

# 调用侧：关键字参数，含义一目了然，无需 Builder
req = HttpRequest(
    url="https://api.example.com/users",
    method="post",                        # 自动规范化为 "POST"
    headers={"Content-Type": "application/json"},
    body='{"name": "Alice"}'
)

# Java 需要 10 行 Builder 链；Python 需要 0 行——直接构造
```

### `@dataclass` — 轻量替代方案

不想引入 Pydantic 时，标准库 `dataclass` 配合 `__post_init__` 可以实现相同效果：

```python
from dataclasses import dataclass, field

@dataclass(kw_only=True, slots=True)      # kw_only 强制关键字参数，slots 节省内存
class HttpRequest:
    url: str
    method: str = "GET"
    headers: dict[str, str] = field(default_factory=dict)  # 可变默认值必须用 field()
    body: str | None = None
    timeout_seconds: int = 30

    def __post_init__(self):              # 等价于 record Compact Constructor
        if not self.url:
            raise ValueError("url 不能为空")
        self.method = self.method.upper() # 规范化

# 和 Pydantic 一样，直接关键字构造，无需 Builder
req = HttpRequest(url="https://api.example.com", method="post")
print(req.method)  # "POST"

# 派生新对象（等价于 Java 的 with 方法）
import dataclasses
prod_req = dataclasses.replace(req, url="https://prod.example.com")
```

### Python 真正需要 Builder 的场景: SQL 查询构建

当构造过程有**顺序语义**或**同一步骤需要多次调用**时，`dataclass` 构造方法无能为力，只有 Builder 才能表达：

```python
class QueryBuilder:
    """SQL 查询 Builder——顺序语义 + 累积语义，是 Python 真正需要 Builder 的典型场景"""

    def __init__(self, table: str):
        self._table = table
        self._conditions: list[str] = []  # WHERE 子句可以多次添加，有顺序
        self._order_by: list[str] = []
        self._limit: int | None = None

    def where(self, condition: str) -> "QueryBuilder":
        self._conditions.append(condition)  # 累积语义：调用多次，每次追加
        return self

    def order_by(self, column: str, desc: bool = False) -> "QueryBuilder":
        direction = "DESC" if desc else "ASC"
        self._order_by.append(f"{column} {direction}")
        return self

    def limit(self, n: int) -> "QueryBuilder":
        self._limit = n
        return self

    def build(self) -> str:
        sql = f"SELECT * FROM {self._table}"
        if self._conditions:
            sql += " WHERE " + " AND ".join(self._conditions)
        if self._order_by:
            sql += " ORDER BY " + ", ".join(self._order_by)
        if self._limit is not None:
            sql += f" LIMIT {self._limit}"
        return sql

# 使用：链式调用，WHERE 多次叠加，顺序有意义
query = (
    QueryBuilder("users")
    .where("active = true")      # 第一个条件
    .where("age > 18")           # 第二个条件，顺序不能随意调换逻辑
    .order_by("created_at", desc=True)
    .limit(20)
    .build()
)
print(query)
# SELECT * FROM users WHERE active = true AND age > 18 ORDER BY created_at DESC LIMIT 20
```

`dataclass` 永远无法表达这种"同一参数传多次，顺序有语义"的构造过程——这就是 Python 真正需要 Builder 类的边界。

---

## 两种哲学的根源

Java 需要 Builder 不只是"设计优雅"，更是**语言缺陷的补偿**：

```
Java 的根本限制：
  1. 构造方法只有位置参数，无关键字参数
  2. 参数没有默认值（方法重载是笨拙的替代方案）
  3. new Request(null, "POST", 30, true, null, 3, null) ← 谁知道这七个参数是什么？

Builder 的补偿：
  new Request.Builder()
      .url("https://...")    // 具名
      .method("POST")        // 具名
      .timeout(30)           // 具名，其余参数自动用默认值
      .build();
```

Python 天然具备 Java 通过 Builder 才能实现的能力：

```python
# Python 构造方法 ≈ Java Builder 的使用侧
Request(url="https://...", method="POST", timeout=30)  # 具名、有默认值、顺序无关
```

| 维度 | Java | Python |
|------|------|--------|
| 关键字参数 | 无，Builder 来补偿 | 内置，直接使用 |
| 参数默认值 | 无，重载或 Builder 来补偿 | 内置，直接使用 |
| Builder 必要性 | 高频刚需，几乎每个复杂对象都需要 | 仅在顺序/累积语义时才需要 |
| 不可变对象 | `record` + 内嵌 Builder | `@dataclass(frozen=True)` |
| 构造后校验 | Compact Constructor | `__post_init__` |
| 基于现有对象修改 | `with` 方法（手写） | `dataclasses.replace()` |
| 链式风格 | 主流惯用法 | 存在但不普遍 |
| 标准库例子 | `HttpRequest`、`ProcessBuilder` | `argparse`、`QueryBuilder` |

**核心洞察**: Builder 在 Java 生态里的高频出现，有相当大一部分是在弥补语言本身的不足，而不是因为问题本身必须用 Builder 解决。理解这一点，才能在切换语言时做出正确判断——不会在 Python 里画蛇添足地写不必要的 Builder 类，也不会在 Java 里因为"Python 不用 Builder"就放弃它。

---

## 动手练习

### 10.1 Java — `HttpRequest` record + 内嵌 Builder

在 `src/ch07_Builder/` 中新建 `exercise/HttpRequest.java`：

- 字段：`url`（必填）、`method`（默认 `"GET"`）、`headers`（`Map<String, String>`，默认空）、`body`（可选）、`timeoutSeconds`（默认 30）、`retries`（默认 3）
- Compact Constructor 校验 `url` 不为空，并将 `method` 规范化为大写
- 内嵌 `Builder` 类，`header(k, v)` 方法可以多次调用（累积语义）
- `build()` 用 `Map.copyOf()` 转不可变 Map

思考：如果不用 Builder，改成 7 参数构造方法，调用侧的代码可读性会有多大差距？

### 10.2 Python — `@dataclass` vs 真实 Builder

**Part A**: 用 `@dataclass(kw_only=True)` 实现等价的 `HttpRequest`，添加 `__post_init__` 校验和规范化，对比 Java 版本的代码量差异。

**Part B**: 实现上面的 `QueryBuilder`，扩展以下功能：
- `select(*columns)` — 指定查询列（不传则默认 `*`）
- `join(table, on)` — 支持多次调用（累积 JOIN）
- `build()` 时校验：若有 `LIMIT` 但没有 `ORDER BY`，打印警告

思考：`QueryBuilder` 能不能用 `@dataclass` + `__post_init__` 改写？为什么？

### 10.3 边界思考 — Python 什么时候真的需要 Builder？

分析以下场景，判断各自用 Builder 还是 `@dataclass`，并说明理由：

1. HTML 页面生成器（可以嵌套 `<div>` 、`<ul>` 等多层结构）
2. 机器学习流水线配置（固定若干超参数，字段多但顺序无关）
3. 邮件构建器（收件人可以多次 `add_recipient()`，附件可以多次 `attach()`）
4. 数据库连接池配置（host、port、user、password 等，全部一次性提供）

---

## 回顾与连接

| 模式 | 关系 |
|------|------|
| **Factory Method (Ch06)** | Factory Method 一步产出对象；Builder 多步累积后产出。前者关注"由谁创建"，后者关注"如何构造" |
| **Prototype (Ch11, 下一章)** | Prototype 复制已有对象；Builder 从零构造新对象。当"构造代价高"时，两者可以配合：先 Builder 造出原型，再 Prototype 克隆 |
| **Abstract Factory (Ch18)** | Abstract Factory 创建**一族**相关对象（如 Button + Dialog 配套）；Builder 创建**一个**复杂对象（通过多步组装） |
| **Strategy (Ch02, 前置)** | Director 通过接口依赖 Builder，ConcreteBuilder 是"构造策略"的实现——Builder 模式的结构与 Strategy 完全同构，只是语义从"算法替换"变成了"构造方式替换" |

**一句话总结**: Builder 把"如何构造"从对象自身中分离出去，让同一个构造流程可以产出表现形式完全不同的产品——在 Java 里，它同时还承担了关键字参数缺失的补偿工作。
