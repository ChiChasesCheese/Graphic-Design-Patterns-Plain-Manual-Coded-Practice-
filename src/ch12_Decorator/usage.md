# Decorator 模式 — 真实应用

核心：**动态给对象添加职责，通过包装而非继承，可以叠加多层。**

---

## 1. Java — I/O 流

Java I/O 是 Decorator 模式最经典的实现，多层包装叠加功能。

```java
// 一层一层包装，每层添加一种能力
InputStream raw        = new FileInputStream("data.bin");     // 基础：读文件
InputStream buffered   = new BufferedInputStream(raw);        // +缓冲
InputStream unzipped   = new GZIPInputStream(buffered);       // +解压
DataInputStream data   = new DataInputStream(unzipped);       // +类型读取

// 读取：经过解压 → 缓冲 → 文件，调用方只用最外层接口
int value = data.readInt();

// 对比继承的爆炸问题：
// BufferedFileInputStream, GzipFileInputStream,
// BufferedGzipFileInputStream, ... 组合爆炸
// Decorator 只需要 N 个类，继承需要 2^N 个类
```

---

## 2. Python — `@decorator` 语法

Python 的装饰器语法是语言级别对 Decorator 模式的原生支持。

```python
import functools
import time
import logging

# 装饰器：计时
def timer(func):
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        start = time.perf_counter()
        result = func(*args, **kwargs)  # 调用原函数（被装饰的对象）
        elapsed = time.perf_counter() - start
        print(f"{func.__name__} took {elapsed:.3f}s")
        return result
    return wrapper

# 装饰器：重试
def retry(times=3):
    def decorator(func):
        @functools.wraps(func)
        def wrapper(*args, **kwargs):
            for attempt in range(times):
                try:
                    return func(*args, **kwargs)
                except Exception as e:
                    if attempt == times - 1: raise
                    time.sleep(2 ** attempt)  # 指数退避
        return wrapper
    return decorator

# 叠加多层装饰器（从下到上依次包装）
@timer
@retry(times=3)
def fetch_user(user_id: int):
    return requests.get(f"/api/users/{user_id}").json()
```

---

## 3. NestJS — `@Decorator` 元编程

NestJS（最流行的 Node.js 企业框架）大量使用 TypeScript 装饰器，
每个装饰器是一层包装，叠加权限、缓存、日志、验证。

```typescript
@Controller('/users')
@UseGuards(AuthGuard)         // 装饰：认证检查
@UseInterceptors(LoggingInterceptor)  // 装饰：日志记录
export class UserController {

    @Get('/:id')
    @Roles('admin', 'user')   // 装饰：权限控制
    @CacheKey('user')         // 装饰：缓存
    @CacheTTL(300)
    async getUser(@Param('id') id: string) {
        return this.userService.findById(id);
    }
}
// 每个 @Decorator 是一层包装，组合起来完成认证→授权→缓存→日志
```

---

## 4. Express.js — 中间件链

Express 的中间件是 Decorator 的变体：每个中间件包装 request/response，
叠加日志、认证、压缩、CORS 等能力。

```javascript
const app = express();

// 每个 use() 是一层装饰
app.use(helmet());                    // +安全 headers
app.use(compression());               // +gzip 压缩
app.use(morgan('combined'));          // +访问日志
app.use(express.json({ limit: '10mb' })); // +JSON 解析
app.use(rateLimit({ windowMs: 60000, max: 100 })); // +限流
app.use(cors({ origin: allowedOrigins }));          // +CORS

// 路由处理器接收的 req/res 已经经过所有层的包装
app.get('/users', authenticate, authorize('admin'), async (req, res) => {
    // req.user 是 authenticate 中间件注入的
    res.json(await User.findAll());
});
```

---

## 5. Go — `http.Handler` 包装链

Go 惯用的中间件模式，函数包装函数，每层添加一种能力。

```go
// 每个中间件是一个包装函数
func logging(next http.Handler) http.Handler {
    return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
        start := time.Now()
        next.ServeHTTP(w, r)  // 调用下一层
        log.Printf("%s %s %v", r.Method, r.URL.Path, time.Since(start))
    })
}

func auth(next http.Handler) http.Handler {
    return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
        token := r.Header.Get("Authorization")
        if !validateToken(token) {
            http.Error(w, "Unauthorized", 401)
            return
        }
        next.ServeHTTP(w, r)
    })
}

// 叠加包装：auth(logging(myHandler))
handler := logging(auth(myHandler))
http.ListenAndServe(":8080", handler)
```

---

## Python 生态

Python 原生支持装饰器语法（`@decorator`），是语言级别的 Decorator 模式实现。

```python
import functools
import time
import logging
from typing import Callable, TypeVar, ParamSpec

P = ParamSpec("P")
R = TypeVar("R")

# 1. 函数装饰器 — 最常见的 Python Decorator 模式
def timer(func: Callable[P, R]) -> Callable[P, R]:
    @functools.wraps(func)           # wraps 保留原函数的 __name__、__doc__ 等元数据
    def wrapper(*args: P.args, **kwargs: P.kwargs) -> R:
        start = time.perf_counter()
        result = func(*args, **kwargs)
        elapsed = time.perf_counter() - start
        print(f"{func.__name__} took {elapsed:.3f}s")
        return result
    return wrapper

def retry(max_attempts: int = 3, delay: float = 1.0):
    """带参数的装饰器工厂"""
    def decorator(func: Callable[P, R]) -> Callable[P, R]:
        @functools.wraps(func)
        def wrapper(*args: P.args, **kwargs: P.kwargs) -> R:
            for attempt in range(max_attempts):
                try:
                    return func(*args, **kwargs)
                except Exception as e:
                    if attempt == max_attempts - 1:
                        raise
                    print(f"Attempt {attempt + 1} failed: {e}, retrying...")
                    time.sleep(delay)
        return wrapper
    return decorator

# 叠加多个装饰器（从下往上依次包装）
@timer
@retry(max_attempts=3, delay=0.5)
def fetch_data(url: str) -> dict:
    import httpx
    return httpx.get(url).json()

# 2. 类装饰器 — 给整个类添加功能
def add_repr(cls):
    """给类自动添加 __repr__ 方法"""
    def __repr__(self):
        fields = {k: v for k, v in self.__dict__.items() if not k.startswith("_")}
        return f"{cls.__name__}({', '.join(f'{k}={v!r}' for k, v in fields.items())})"
    cls.__repr__ = __repr__
    return cls

@add_repr
class Config:
    def __init__(self, host: str, port: int):
        self.host = host
        self.port = port

print(Config("localhost", 5432))   # Config(host='localhost', port=5432)

# 3. functools.lru_cache / cache — 缓存装饰器（最常用的内置 Decorator）
from functools import lru_cache, cache

@cache                              # Python 3.9+ 无界缓存
def fibonacci(n: int) -> int:
    if n < 2:
        return n
    return fibonacci(n - 1) + fibonacci(n - 2)

@lru_cache(maxsize=128)             # 有界 LRU 缓存
def expensive_query(user_id: int, date: str) -> dict:
    # 模拟数据库查询
    return {"user_id": user_id, "date": date, "data": "..."}

# 4. contextlib.contextmanager — 作为 Decorator 使用
from contextlib import contextmanager

@contextmanager
def log_operation(name: str):
    logging.info(f"Starting {name}")
    try:
        yield
        logging.info(f"Completed {name}")
    except Exception as e:
        logging.error(f"Failed {name}: {e}")
        raise

# contextmanager 既可以用 with，也可以用 @ 装饰函数
with log_operation("data_import"):
    pass  # 业务逻辑

# 5. dataclasses.dataclass — 类装饰器改变类结构
from dataclasses import dataclass

@dataclass(frozen=True, order=True)   # 装饰器添加 __init__、__eq__、__lt__ 等
class Point:
    x: float
    y: float

p1, p2 = Point(1.0, 2.0), Point(3.0, 4.0)
print(p1 < p2)   # True（order=True 自动生成比较方法）
```

> **Python 洞察**：Python 的 `@decorator` 语法就是 GoF Decorator 模式的语言级支持。
> `functools.wraps` 解决了 Decorator 的一个经典问题：包装后函数元数据丢失。
> `@dataclass`、`@property`、`@staticmethod` 都是类/方法级别的 Decorator。

---

## 关键洞察

> Decorator 是"开闭原则"最直接的体现：不修改原有代码，通过包装扩展功能。
> 现代框架里的"中间件"本质上都是 Decorator。
> Python 的 `@decorator`、Java 的 `@Annotation`、Express 的 middleware——
> **同一个思想，三种语言的不同表达。**
