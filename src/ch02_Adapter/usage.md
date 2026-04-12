# Adapter 模式 — 真实应用

核心：**把已有接口包装成目标接口，让不兼容的东西能协作。**

---

## 1. Java — SLF4J（日志门面）

SLF4J 本身就是一个巨型 Adapter。你的代码只依赖 SLF4J 接口，
背后可以无缝切换 Log4j2、Logback、JUL，不改业务代码。

```java
// 你的代码永远只写这个
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

Logger log = LoggerFactory.getLogger(MyService.class);
log.info("Processing order {}", orderId);

// SLF4J 内部：
// slf4j-to-log4j2.jar  → 把 SLF4J 调用适配到 Log4j2 API
// slf4j-to-logback.jar → 把 SLF4J 调用适配到 Logback API
// 换实现只换 jar，代码零修改
```

---

## 2. JavaScript/TypeScript — Axios Adapter

Axios 在浏览器用 `XMLHttpRequest`，在 Node.js 用 `http` 模块，
对外暴露完全相同的 API——这就是 Adapter。

```typescript
// axios/lib/adapters/xhr.ts（浏览器端，简化）
function xhrAdapter(config: AxiosRequestConfig): Promise<AxiosResponse> {
    return new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest();      // 旧 API
        xhr.open(config.method, config.url);
        xhr.onload = () => resolve(settle(xhr)); // 适配成统一的 Promise
        xhr.onerror = () => reject(...);
        xhr.send(config.data);
    });
}

// axios/lib/adapters/http.ts（Node.js 端，简化）
function httpAdapter(config): Promise<AxiosResponse> {
    return new Promise((resolve, reject) => {
        const req = http.request(config.url, ...); // Node.js 原生 API
        req.on('response', res => resolve(settle(res)));
    });
}

// 调用方：完全感知不到底层差异
const res = await axios.get('/api/users');
```

---

## 3. Python — `io` 模块

`io.TextIOWrapper` 把二进制流适配成文本流，
让只能处理字节的底层 API 对外表现成字符串接口。

```python
import io

# socket 只能收发 bytes，TextIOWrapper 适配成可以 readline() 的文本接口
sock = socket.create_connection(('example.com', 80))
stream = io.TextIOWrapper(sock.makefile('rb'), encoding='utf-8')

for line in stream:          # 看起来像在读文件
    print(line.strip())      # 实际底层是 TCP socket
```

---

## 4. TypeScript/NestJS — 数据库 ORM 适配

TypeORM / Prisma 把不同数据库的 SQL 方言适配成统一的 Repository 接口。

```typescript
// 你写的代码（与数据库无关）
@Injectable()
export class UserRepository {
    constructor(
        @InjectRepository(User)
        private repo: Repository<User>  // TypeORM 统一接口
    ) {}

    findByEmail(email: string) {
        return this.repo.findOne({ where: { email } });
    }
}

// 底层：
// MySQL    → 生成 MySQL 方言 SQL
// Postgres → 生成 Postgres 方言 SQL
// SQLite   → 生成 SQLite 方言 SQL
// 切换数据库只改配置，Repository 代码不动
```

---

## 5. Go — `http.Handler` 接口适配

Go 的标准库 `net/http` 用 `Handler` 接口统一请求处理，
第三方框架（Gin、Echo、Chi）都通过 Adapter 兼容这个接口。

```go
// 标准库接口
type Handler interface {
    ServeHTTP(ResponseWriter, *Request)
}

// Gin 框架用 Adapter 把自己的 HandlerFunc 适配成标准 http.Handler
// 这样 Gin 路由可以无缝挂载到任何标准 http.Server 上
ginEngine := gin.Default()
http.ListenAndServe(":8080", ginEngine) // ginEngine 实现了 http.Handler
```

---

## Python 生态

Python 用 `typing.Protocol` 实现**结构化子类型**（鸭子类型 + 类型检查），不需要继承就能描述"目标接口"，是最 Pythonic 的 Adapter 实现方式。

```python
from typing import Protocol, runtime_checkable

# 1. Protocol：定义目标接口（不需要继承）
@runtime_checkable
class TextRenderer(Protocol):
    def render(self, text: str) -> str: ...

# 旧接口：第三方库，无法修改
class LegacyFormatter:
    def format_text(self, content: str, uppercase: bool = False) -> str:
        return content.upper() if uppercase else content

# Adapter：包装旧接口，实现目标 Protocol
class FormatterAdapter:
    def __init__(self, formatter: LegacyFormatter):
        self._formatter = formatter

    def render(self, text: str) -> str:          # 实现 Protocol，无需 implements
        return self._formatter.format_text(text, uppercase=True)

adapter = FormatterAdapter(LegacyFormatter())
assert isinstance(adapter, TextRenderer)         # runtime_checkable 允许 isinstance 检查
print(adapter.render("hello"))                   # HELLO

# 2. __init_subclass__ + 注册表（让 Adapter 自动注册）
class DataAdapter:
    _registry: dict[str, type] = {}

    def __init_subclass__(cls, source_format: str, **kwargs):
        super().__init_subclass__(**kwargs)
        DataAdapter._registry[source_format] = cls  # 子类定义时自动注册

    def convert(self, data) -> dict: ...

class CSVAdapter(DataAdapter, source_format="csv"):
    def convert(self, data: str) -> dict:
        rows = data.strip().split("\n")
        keys = rows[0].split(",")
        return [dict(zip(keys, r.split(","))) for r in rows[1:]]

class XMLAdapter(DataAdapter, source_format="xml"):
    def convert(self, data: str) -> dict:
        import xml.etree.ElementTree as ET
        root = ET.fromstring(data)
        return {child.tag: child.text for child in root}

# 工厂函数：根据格式自动选择 Adapter
def get_adapter(fmt: str) -> DataAdapter:
    cls = DataAdapter._registry.get(fmt)
    if cls is None:
        raise ValueError(f"No adapter for format: {fmt}")
    return cls()

csv_data = "name,age\nAlice,30\nBob,25"
result = get_adapter("csv").convert(csv_data)

# 3. functools.wraps — 函数级 Adapter（保留原函数元数据）
import functools

def legacy_greet(name, greeting="Hello"):
    """旧接口：两个参数"""
    return f"{greeting}, {name}!"

@functools.wraps(legacy_greet)
def modern_greet(*, name: str) -> str:     # 新接口：只接受关键字参数
    """新接口：只暴露 name"""
    return legacy_greet(name)

print(modern_greet.__doc__)   # 保留原函数文档（wraps 的作用）
```

> **Python 洞察**：`Protocol` 是 Python 的"隐式 Adapter"——只要类有对应方法，就自动满足接口，
> 不需要显式声明 `implements`。这比 Java 的 Adapter 模式更灵活，但也更难追踪依赖关系。
> `mypy` / `pyright` 在静态检查时会验证 Protocol 的满足情况。

---

## 关键洞察

> 真实工程里换第三方库、换云厂商、换数据库是常态。
> 在边界上加一层 Adapter，业务代码就与具体实现解耦——
> **换掉 X 只需要写一个新 Adapter，不需要改任何业务逻辑。**
