# 为什么 Java 需要 Builder 模式

## 根本原因：Java 没有关键字参数

Java 的方法/构造方法只有**位置参数**，调用时必须按顺序传入所有参数：

```java
// 这 7 个参数分别是什么？没看文档根本不知道
new Request("POST", "https://api.example.com", null, 30, true, 3, null);
```

Python 有**关键字参数 + 默认值**，天然解决了这个问题：

```python
# 想传哪个传哪个，其余自动用默认值，含义一目了然
HTTPRequest(url="https://api.example.com", method="POST")
```

---

## Builder 模式本质上是在模拟关键字参数

```java
// Builder 的每一个方法调用 ≈ Python 的一个关键字参数
new Request.Builder()
    .url("https://api.example.com")   // ≈ url="..."
    .method("POST")                   // ≈ method="POST"
    .timeout(30)                      // ≈ timeout=30
    // 没传的字段自动用默认值         // ≈ Python 的 default value
    .build();
```

两者要解决的问题完全一样：
- 参数太多时，让调用方只传关心的字段
- 其余字段有合理默认值
- 调用处代码可读，不靠位置猜含义

---

## Java 为什么没有关键字参数

**历史原因**：Java 设计于 1995 年，刻意贴近 C/C++ 以降低学习门槛，未引入关键字参数。

**后来为什么不补**：Java 有**方法重载**机制——同名方法可以有不同参数列表。如果同时支持关键字参数，两套机制组合会产生严重的歧义和复杂性，语言设计者选择维持现状。

---

## 什么时候 Python 才真正需要链式 Builder

Python 的 dataclass/pydantic 构造函数能覆盖大多数场景，但有一种情况必须手写链式 Builder：**构造过程有顺序语义，或同一步骤需要调用多次**。

```python
# SQL 是典型案例：WHERE 可以叠加多次，且子句之间有顺序约束
query = (
    QueryBuilder("users")
    .where("active = true")   # 可以多次调用
    .where("age > 18")        # 顺序有意义
    .order_by("created_at", desc=True)
    .limit(20)
    .build()                  # 最终才生成 SQL 字符串
)
```

dataclass 构不出这种效果，因为它只能接受一次性的参数，无法表达"叠加"语义。

---

## Builder 用在 record / 数据对象上

record 是不可变的，所有字段必须在构造时一次性传入——字段一多就是"参数爆炸"问题，Builder 正好解决这个。

### 方式一：record 内嵌静态 Builder（最常见）

```java
public record HttpRequest(
    String url,
    String method,
    Map<String, String> headers,
    String body,
    int timeoutSeconds,
    int retries
) {
    public static final class Builder {
        private String url;
        private String method = "GET";                    // 默认值在这里定义
        private Map<String, String> headers = new HashMap<>();
        private String body;
        private int timeoutSeconds = 30;
        private int retries = 3;

        public Builder url(String url)           { this.url = url; return this; }
        public Builder method(String method)     { this.method = method; return this; }
        public Builder header(String key, String value) {
            this.headers.put(key, value);
            return this;                                  // header 可以多次调用——这是 Builder 相比构造方法的核心优势
        }
        public Builder body(String body)         { this.body = body; return this; }
        public Builder timeoutSeconds(int t)     { this.timeoutSeconds = t; return this; }
        public Builder retries(int r)            { this.retries = r; return this; }

        public HttpRequest build() {
            if (url == null) throw new IllegalStateException("url is required");
            return new HttpRequest(url, method, Map.copyOf(headers), body, timeoutSeconds, retries);
            //                                  ↑ 转不可变 Map，防止外部修改内部状态
        }
    }

    public static Builder builder() { return new Builder(); }
}

// 使用
HttpRequest request = HttpRequest.builder()
    .url("https://api.example.com/users")
    .method("POST")
    .header("Content-Type", "application/json")
    .header("Authorization", "Bearer token")   // 同一步骤多次调用
    .body("{\"name\":\"Alice\"}")
    .build();
```

### 方式二：`with` 方法（适合"基于现有对象改一个字段"）

```java
public record Config(String host, int port, boolean ssl, int poolSize) {
    // 不可变对象的"修改"——返回新实例，原对象不变
    public Config withHost(String host)      { return new Config(host, port, ssl, poolSize); }
    public Config withPort(int port)         { return new Config(host, port, ssl, poolSize); }
    public Config withSsl(boolean ssl)       { return new Config(host, port, ssl, poolSize); }
    public Config withPoolSize(int poolSize) { return new Config(host, port, ssl, poolSize); }
}

// 基于已有配置派生新配置，base 不被修改
Config base    = new Config("localhost", 5432, false, 10);
Config prod    = base.withHost("prod.db.com").withSsl(true);
Config staging = base.withHost("staging.db.com").withPoolSize(5);
```

这是 Kotlin `data class` 内置 `copy(field=newValue)` 的手写等价物，也是 Python `dataclasses.replace()` 的 Java 版本。

### 方式三：Compact Constructor 做校验（record 独有特性）

```java
public record Email(String address) {
    // Compact Constructor：不写参数列表，只做校验和规范化，字段自动赋值
    public Email {
        if (address == null || !address.contains("@")) {
            throw new IllegalArgumentException("Invalid email: " + address);
        }
        address = address.toLowerCase();  // 规范化：统一转小写
    }
}

// 构造时就校验，非法状态的 Email 对象根本不可能存在
var email = new Email("Alice@Example.COM");
System.out.println(email.address());  // alice@example.com
```

### 怎么选

| 场景 | 方案 |
|------|------|
| 字段多、有默认值、可选字段多 | 内嵌 Builder |
| 字段少（≤4）、全部必填 | 直接 record 构造方法 |
| 基于现有对象改一两个字段 | `with` 方法 |
| 需要在构造时校验或规范化 | Compact Constructor |

---

## 结论

| | Java | Python |
|---|---|---|
| 关键字参数 | ❌ 没有 | ✅ 内置 |
| 默认参数值 | ❌ 没有（重载模拟）| ✅ 内置 |
| Builder 的必要性 | ✅ 高频刚需 | 只在有顺序/叠加语义时才需要 |

> **GoF Builder 模式相当大一部分存在价值，是在弥补 Java 语言本身的不足。**
> 理解这一点，比死记"Builder 的定义"更重要——
> 它让你在看到 Java 的 Builder 时能立刻反应出"这是在模拟关键字参数"，
> 也让你在用 Python 时不会画蛇添足地写没必要的 Builder 类。
