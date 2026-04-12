# Builder 模式 — 真实应用

核心：**分步骤构造复杂对象，避免构造器参数爆炸，支持链式调用。**

---

## 1. Java — Lombok `@Builder`

Lombok 自动生成 Builder 代码，几乎是 Java 后端的标配。

```java
@Builder
@Data
public class CreateOrderRequest {
    private String userId;
    private List<OrderItem> items;
    private String couponCode;
    private Address shippingAddress;
    private PaymentMethod paymentMethod;
}

// 使用：链式调用，只设置需要的字段，顺序无所谓
CreateOrderRequest request = CreateOrderRequest.builder()
    .userId("user-123")
    .items(List.of(new OrderItem("sku-1", 2)))
    .shippingAddress(address)
    .paymentMethod(PaymentMethod.CREDIT_CARD)
    .build();  // 最后统一校验和构造

// 对比：没有 Builder 的构造器（参数多了之后完全不可读）
// new CreateOrderRequest("user-123", items, null, address, PaymentMethod.CREDIT_CARD)
//                                              ↑ null 是什么字段？谁知道
```

---

## 2. Java — OkHttp `Request.Builder`

Android 和 Java 生态最流行的 HTTP 客户端，Builder 是其核心 API。

```java
// OkHttp：每个部分独立设置，最后 build()
Request request = new Request.Builder()
    .url("https://api.example.com/users")
    .addHeader("Authorization", "Bearer " + token)
    .addHeader("Content-Type", "application/json")
    .post(RequestBody.create(json, MediaType.get("application/json")))
    .build();

OkHttpClient client = new OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .addInterceptor(new LoggingInterceptor())
    .build();
```

---

## 3. TypeScript — Prisma Query Builder

Prisma 的查询 API 就是 Builder 模式，每一步精细控制查询条件。

```typescript
// 分步骤构造复杂查询，每步返回 this 支持链式调用
const users = await prisma.user.findMany({
    where: {
        active: true,
        createdAt: { gte: new Date('2024-01-01') },
        role: { in: ['admin', 'editor'] },
    },
    include: {
        posts: { where: { published: true }, take: 5 },
        profile: true,
    },
    orderBy: { createdAt: 'desc' },
    skip: 0,
    take: 20,
});

// Knex.js（更传统的 Query Builder）
const query = knex('users')
    .select('id', 'name', 'email')
    .where('active', true)
    .whereIn('role', ['admin', 'editor'])
    .orderBy('created_at', 'desc')
    .limit(20);
```

---

## 4. Python — `dataclasses` + `__post_init__` 校验

Python 里 Builder 通常用 dataclass 配合工厂方法实现，
`__post_init__` 做构建完成后的校验。

```python
from dataclasses import dataclass, field
from typing import Optional

@dataclass
class HttpRequest:
    url: str
    method: str = "GET"
    headers: dict = field(default_factory=dict)
    body: Optional[str] = None
    timeout: int = 30

    def __post_init__(self):
        # 构建完成后统一校验（Builder 的 build() 阶段）
        if not self.url.startswith("http"):
            raise ValueError(f"Invalid URL: {self.url}")
        if self.method not in ("GET", "POST", "PUT", "DELETE", "PATCH"):
            raise ValueError(f"Invalid method: {self.method}")

# 使用
req = HttpRequest(
    url="https://api.example.com",
    method="POST",
    headers={"Authorization": "Bearer token"},
    body='{"name": "Alice"}',
)
```

---

## 5. Kubernetes — YAML Manifest（声明式 Builder）

K8s 的 YAML 本质上是 Builder 模式的声明式表达：
分块描述资源的各个部分，由 K8s API Server 统一 build 成实际对象。

```yaml
# 每个字段独立声明，顺序无所谓，K8s 读取并构建 Deployment 对象
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: my-service
  template:
    spec:
      containers:
      - name: app
        image: my-service:v1.2.3
        resources:
          requests: { memory: "128Mi", cpu: "250m" }
          limits:   { memory: "256Mi", cpu: "500m" }
        env:
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: url
```

---

## Python 生态

Python 的 `dataclasses` 和第三方库 `attrs` / `pydantic` 提供了声明式 Builder 替代方案，比手写链式 Builder 更简洁。

```python
# 1. 链式 Builder（手写，方法返回 self）
class QueryBuilder:
    def __init__(self, table: str):
        self._table = table
        self._conditions: list[str] = []
        self._columns: list[str] = ["*"]
        self._limit: int | None = None
        self._order_by: str | None = None

    def select(self, *columns: str) -> "QueryBuilder":
        self._columns = list(columns)
        return self

    def where(self, condition: str) -> "QueryBuilder":
        self._conditions.append(condition)
        return self

    def order_by(self, column: str, desc: bool = False) -> "QueryBuilder":
        self._order_by = f"{column} DESC" if desc else column
        return self

    def limit(self, n: int) -> "QueryBuilder":
        self._limit = n
        return self

    def build(self) -> str:
        sql = f"SELECT {', '.join(self._columns)} FROM {self._table}"
        if self._conditions:
            sql += " WHERE " + " AND ".join(self._conditions)
        if self._order_by:
            sql += f" ORDER BY {self._order_by}"
        if self._limit:
            sql += f" LIMIT {self._limit}"
        return sql

query = (
    QueryBuilder("users")
    .select("id", "name", "email")
    .where("active = true")
    .where("age > 18")
    .order_by("created_at", desc=True)
    .limit(20)
    .build()
)
# SELECT id, name, email FROM users WHERE active = true AND age > 18
# ORDER BY created_at DESC LIMIT 20

# 2. dataclass + field — 声明式 Builder（Python 惯用法）
from dataclasses import dataclass, field

@dataclass
class HTTPRequest:
    url: str
    method: str = "GET"
    headers: dict[str, str] = field(default_factory=dict)
    body: bytes | None = None
    timeout: float = 30.0
    retries: int = 3

# 直接构造，不需要 Builder 类——dataclass 的关键字参数就是 Builder
request = HTTPRequest(
    url="https://api.example.com/users",
    method="POST",
    headers={"Content-Type": "application/json"},
    body=b'{"name": "Alice"}',
)

# 3. attrs 库 — 比 dataclass 更强大的声明式 Builder
# import attr
# @attr.s(auto_attribs=True)
# class Pipeline:
#     name: str
#     steps: list = attr.Factory(list)
#     max_retries: int = attr.ib(default=3, validator=attr.validators.instance_of(int))
#     timeout: float = 60.0

# 4. Pydantic v2 — 带验证的 Builder
from pydantic import BaseModel, field_validator, model_validator

class EmailConfig(BaseModel):
    smtp_host: str
    smtp_port: int = 587
    username: str
    password: str
    use_tls: bool = True
    max_connections: int = 10

    @field_validator("smtp_port")
    @classmethod
    def validate_port(cls, v: int) -> int:
        if not (1 <= v <= 65535):
            raise ValueError(f"Invalid port: {v}")
        return v

    @model_validator(mode="after")
    def validate_tls_port(self) -> "EmailConfig":
        if self.use_tls and self.smtp_port == 25:
            raise ValueError("Port 25 not recommended for TLS")
        return self

# Builder 模式：逐步填充参数，最终验证
config = EmailConfig(
    smtp_host="smtp.gmail.com",
    username="user@gmail.com",
    password="secret",
)
```

> **Python 洞察**：Python 很少需要手写 Builder 类——`dataclass` / `pydantic` 的构造函数
> 本身就是 Builder（支持默认值、关键字参数、验证）。
> 真正需要手写 Builder 的场景是构造过程有**顺序依赖**或**条件分支**时（如 SQL Builder）。

---

## 关键洞察

> Builder 解决的核心问题是**构造器参数爆炸**。
> 当一个对象有 5+ 个参数，尤其是有大量可选参数时，Builder 是最清晰的解法。
> 现代语言特性（Kotlin 具名参数、Python dataclass、TypeScript 对象字面量）
> 部分替代了 Builder，但复杂对象的分步构造 + 最终校验的模式永远有效。
