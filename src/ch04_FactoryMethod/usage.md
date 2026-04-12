# Factory Method 模式 — 真实应用

核心：**把"创建哪种对象"的决定推迟到子类或调用方，调用方只知道接口，不知道具体类。**

---

## 1. Java — `java.util.Calendar.getInstance()`

根据运行环境（locale、timezone）返回不同的 Calendar 实现，
调用方拿到的是 `Calendar` 接口，不关心是哪种日历系统。

```java
// 工厂方法，内部根据 locale 决定返回哪个子类
Calendar cal = Calendar.getInstance();        // 大多数地区：GregorianCalendar
Calendar cal = Calendar.getInstance(         // 泰国：BuddhistCalendar
    new Locale("th", "TH"));

cal.get(Calendar.YEAR);  // 调用方只用 Calendar 接口，不关心具体类型
```

---

## 2. JDBC — `DriverManager.getConnection()`

经典的 Factory Method。传入 URL，框架决定用 MySQL Driver 还是 PostgreSQL Driver。

```java
// 工厂方法：根据 URL 前缀决定用哪个 Driver 实现
Connection conn = DriverManager.getConnection(
    "jdbc:postgresql://localhost:5432/mydb", user, pass
);
// 内部：扫描注册的 Driver，找到能处理 "jdbc:postgresql:" 的那个
// 调用方拿到的是 Connection 接口，不知道也不需要知道是 PostgreSQL

// 切换到 MySQL：只改 URL，不改任何业务代码
Connection conn = DriverManager.getConnection(
    "jdbc:mysql://localhost:3306/mydb", user, pass
);
```

---

## 3. Spring — `BeanFactory` / `ApplicationContext`

Spring 最核心的概念。`getBean()` 是工厂方法，
Spring 决定创建哪个实现类、是否单例、是否需要代理。

```java
@Configuration
public class PaymentConfig {

    @Bean
    public PaymentGateway paymentGateway() {
        // 工厂方法：根据配置决定返回哪种实现
        return switch (env.getProperty("payment.provider")) {
            case "stripe"  -> new StripeGateway(apiKey);
            case "paypal"  -> new PayPalGateway(clientId, secret);
            default        -> new MockGateway();  // 测试环境
        };
    }
}

// 调用方只依赖接口，完全不知道背后是 Stripe 还是 PayPal
@Autowired PaymentGateway gateway;
gateway.charge(amount);
```

---

## 4. Python — `logging.getLogger()`

Python 标准库日志系统的工厂方法。
同名 logger 返回同一个实例，层级关系由框架维护。

```python
import logging

# 工厂方法：根据名称创建或复用 Logger 实例
logger = logging.getLogger("myapp.service.user")
# "myapp.service.user" 自动继承 "myapp.service" 和 "myapp" 的配置

# 调用方不关心 Logger 的构造细节，也不需要持有引用
logging.getLogger("myapp").setLevel(logging.DEBUG)
# 上面一行对所有 "myapp.*" 的 logger 生效
```

---

## 5. TypeScript — Prisma Client 的 `$transaction`

Prisma（现代 Node.js ORM）的事务工厂：
根据传入的操作列表，创建正确类型的事务客户端。

```typescript
// Prisma 工厂方法：根据是否传数组，创建 sequential 或 interactive 事务
const [user, post] = await prisma.$transaction([
    prisma.user.create({ data: { name: 'Alice' } }),   // 顺序事务
    prisma.post.create({ data: { title: 'Hello' } }),
]);

// 交互式事务（工厂创建带事务上下文的 client）
await prisma.$transaction(async (tx) => {
    const user = await tx.user.findUnique({ where: { id: 1 } });
    await tx.account.update({
        where: { userId: user.id },
        data: { balance: { decrement: 100 } }
    });
});
```

---

## Python 生态

Python 用 `__init_subclass__` 实现**自动注册工厂**，是比显式注册表更优雅的工厂方法变体。

```python
# 1. __init_subclass__ 自动注册工厂
class Serializer:
    """工厂基类：子类定义时自动注册"""
    _registry: dict[str, type] = {}

    def __init_subclass__(cls, format: str, **kwargs):
        super().__init_subclass__(**kwargs)
        Serializer._registry[format] = cls     # 子类创建时自动注册

    def serialize(self, data: dict) -> str: ...
    def deserialize(self, text: str) -> dict: ...

    @classmethod
    def create(cls, format: str) -> "Serializer":   # 工厂方法
        if format not in cls._registry:
            raise ValueError(f"Unknown format: {format}")
        return cls._registry[format]()

class JSONSerializer(Serializer, format="json"):
    def serialize(self, data: dict) -> str:
        import json
        return json.dumps(data)

    def deserialize(self, text: str) -> dict:
        import json
        return json.loads(text)

class TOMLSerializer(Serializer, format="toml"):
    def serialize(self, data: dict) -> str:
        import tomllib  # Python 3.11+ 内置
        # toml 写入需要第三方库 tomli-w
        return str(data)

    def deserialize(self, text: str) -> dict:
        import tomllib
        return tomllib.loads(text)

# 调用方只知道格式名，不知道具体类
s = Serializer.create("json")
print(s.serialize({"name": "Alice"}))   # {"name": "Alice"}

# 2. classmethod 工厂（Python 惯用法）
from dataclasses import dataclass
from datetime import datetime

@dataclass
class Event:
    name: str
    timestamp: datetime
    source: str

    @classmethod
    def from_dict(cls, data: dict) -> "Event":        # 工厂方法：从字典创建
        return cls(
            name=data["name"],
            timestamp=datetime.fromisoformat(data["ts"]),
            source=data.get("source", "unknown"),
        )

    @classmethod
    def from_log_line(cls, line: str) -> "Event":     # 工厂方法：从日志行解析
        parts = line.split("|")
        return cls(name=parts[0], timestamp=datetime.now(), source=parts[1])

e = Event.from_dict({"name": "login", "ts": "2026-04-11T10:00:00"})

# 3. typing.overload + 工厂函数（根据参数类型返回不同子类）
from typing import overload, Union

class Shape: ...
class Circle(Shape):
    def __init__(self, radius: float): self.radius = radius
class Rectangle(Shape):
    def __init__(self, w: float, h: float): self.w, self.h = w, h

@overload
def make_shape(kind: str, radius: float) -> Circle: ...
@overload
def make_shape(kind: str, w: float, h: float) -> Rectangle: ...

def make_shape(kind: str, *args) -> Shape:
    if kind == "circle":
        return Circle(*args)
    elif kind == "rect":
        return Rectangle(*args)
    raise ValueError(f"Unknown shape: {kind}")
```

> **Python 洞察**：`__init_subclass__` + `classmethod` 是 Python 工厂方法的惯用组合。
> 子类在定义时就完成注册，无需手动维护注册表——"插件系统"通常就是这样实现的。
> `tomllib`（Python 3.11+）、`json`、`csv` 等标准库模块的 `load`/`loads` 都是工厂方法。

---

## 关键洞察

> Factory Method 的本质是**把 `new` 藏起来**。
> 调用方说"给我一个能处理支付的东西"，不说"给我一个 StripeGateway"。
> 这样当你换实现（换数据库、换支付渠道、换日志框架），
> 只改工厂，不改任何用到这个对象的代码。
