# Chapter 15: Chain of Responsibility

> 类型: 行为 | 难度: ★★☆ | 原书: `src/ch14_ChainOfResponsibility/` | 前置: Ch09 (Decorator), Ch13 (Command)

---

## 模式速览

**问题**: 一个请求需要被处理，但你不知道（或者不想指定）由谁来处理。比如一个技术支持系统，简单问题由一线客服解决，复杂问题升级到二线工程师，更复杂的问题再交给高级专家——发送方只负责提交问题，不需要知道最终由谁处理。如果你把处理逻辑全部写在发送方，每次新增处理层级都得修改发送方代码，违反开闭原则。

Chain of Responsibility 的解决方案：把每个处理者封装成独立对象，每个处理者持有下一个处理者的引用，形成一条链。请求沿链传递，每个节点自主决定"处理并停止"或"不处理并传给下一个"。发送方只持有链头，对链的结构和长度完全无感知。

```
Client
  │
  ▼
Handler A ──────────────► Handler B ──────────────► Handler C ──────► (链尾: null)
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│ handle(request) │       │ handle(request) │       │ handle(request) │
│  if 能处理:     │       │  if 能处理:     │       │  if 能处理:     │
│    处理，停止   │  否则  │    处理，停止   │  否则  │    处理，停止   │
│  else:          │──────►│  else:          │──────►│  else:          │──► 无人处理
│    next.handle()│       │    next.handle()│       │    next.handle()│
└─────────────────┘       └─────────────────┘       └─────────────────┘
```

**四个角色**:
- `Handler` — 抽象处理者，定义处理接口和持有下一个处理者的引用
- `ConcreteHandler` — 具体处理者，判断自己是否能处理，不能则交给后继者
- `Client` — 客户端，组装处理链，向链头发送请求
- `Request` — 请求对象，封装请求数据（有时是简单值类型）

**核心洞察**: 发送方与接收方完全解耦。链的结构可以在运行时动态改变——添加、删除、重排处理者都不影响客户端代码。代价是请求不保证被处理（可能沿链跑完没有节点接手）。

---

## 本章新语言特性

| 特性 | Java | Python |
|------|------|--------|
| 可选值链式处理 | `Optional.or()` / `Optional.flatMap()` | `or` 短路 / `next()` + `default` |
| 模式匹配 | `switch` 模式匹配（Java 21+） | `match` 语句 + guard 子句（Python 3.10+） |
| 函数式链 | `Function.andThen()` / `Stream.reduce()` | `functools.reduce()` / 列表推导 |
| 空安全 | `Optional<T>` 包裹可能缺席的处理结果 | `X if condition else None` + 海象运算符 |

### Java `Optional` 作为微型责任链

`Optional` 是 Java 8 引入的容器类型，专门表达"可能有值，可能没有"的情况。`Optional.or()` 在 Java 9 引入，允许在值缺席时提供一个备选的 `Optional`——这正是责任链的查找语义：先查缓存，缓存未命中再查数据库，数据库也没有再查远程。

```java
// or() 接受 Supplier<Optional<T>>，只在前一个 Optional 为空时才求值（惰性）
Optional<User> user =
    findInLocalCache(id)            // 先查一级缓存
        .or(() -> findInRedis(id))  // 缓存未命中，查 Redis
        .or(() -> findInDB(id));    // Redis 也没有，查数据库

// flatMap() 用于链式提取嵌套 Optional
Optional<String> city = findUser(id)
    .flatMap(User::getAddress)      // User 可能没有地址
    .flatMap(Address::getCity);     // 地址可能没有城市字段
```

`or()` 的惰性求值（`Supplier` 而非直接值）很关键——只有前一步返回 `empty()` 时才会调用后续的 lambda，避免了不必要的数据库查询。

### Python `match` + guard 子句

Python 3.10 引入的 `match` 语句支持 guard 子句（`case X if condition`），让每个处理分支能表达"匹配特定形状且满足条件"的双重约束，非常适合实现多条件路由的责任链节点。

```python
def route_request(request: dict) -> str:
    match request:
        case {"method": "GET", "path": path} if path.startswith("/admin"):
            # 同时匹配结构（GET 方法）和条件（路径前缀）
            return "admin_handler"
        case {"method": "GET", "path": _}:
            return "public_get_handler"
        case {"method": "POST", "body": body} if len(body) > 1_000_000:
            # 大请求体，交给流式处理器
            return "streaming_handler"
        case {"method": "POST"}:
            return "post_handler"
        case _:
            return "not_found"
```

Guard 子句让每个 `case` 分支既能做结构解构，又能附加任意条件，避免了嵌套 `if` 的深度缩进。

---

## Java 实战: `java.util.logging` 父链 + `ClassLoader` 委派

### 源码解析

JDK 内部有两个经典的责任链实现，一个用于日志分级，一个用于类加载安全。

**`Logger` 父链**: JDK 的日志系统里，每个 `Logger` 都有一个 `parent` 引用，形成树形父子链。日志记录时，`LogRecord` 沿链向上冒泡，每个节点检查自身的 `Handler` 和 `Level`。

```java
import java.util.logging.*;

// JDK 日志体系的父链结构（简化后的源码逻辑）
//
// 实际链结构:
//   Logger("com.example.app") → Logger("com.example") → Logger("com") → RootLogger
//
// 每一级 Logger 独立决定：
//   1. 我的 Level 够吗？（如果本级 Level 为 null，继承父级 Level）
//   2. 我有 Handler 吗？如果有，交给 Handler 处理
//   3. useParentHandlers == true 时，继续向父级传递

Logger appLogger = Logger.getLogger("com.example.app");
Logger pkgLogger = Logger.getLogger("com.example");
Logger rootLogger = Logger.getLogger("");  // 根 Logger，名字为空字符串

// 只在根 Logger 挂一个 Handler，所有子 Logger 的日志都会冒泡上来
rootLogger.addHandler(new ConsoleHandler());
rootLogger.setLevel(Level.WARNING);

// 子 Logger 可以覆盖 Level，做更细粒度的控制
appLogger.setLevel(Level.FINE);

// 发一条日志：从 appLogger 开始，沿父链传递
appLogger.fine("调试信息");     // appLogger 处理（Level.FINE 够了），然后传到 rootLogger
appLogger.severe("严重错误");   // 每一级都处理
```

**`ClassLoader` 父委派模型**: 这是 JVM 安全机制的核心，与日志链的结构相同，但语义相反——不是"我先处理再传父"，而是"先委派给父，父失败才自己处理"。

```java
// ClassLoader 父委派链（简化后的 loadClass 核心逻辑）
//
// 链结构（从子到父）:
//   AppClassLoader → ExtClassLoader → BootstrapClassLoader
//
// 但实际执行顺序是 Bootstrap 优先：
//   AppClassLoader.loadClass("java.lang.String")
//     → 委派给 ExtClassLoader
//       → 委派给 BootstrapClassLoader
//         → Bootstrap 找到了 java.lang.String，返回
//         (防止应用代码伪造 java.* 类)

public class MyClassLoader extends ClassLoader {

    public MyClassLoader(ClassLoader parent) {
        super(parent);  // 注入父加载器，形成链
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // 只有父链所有节点都找不到时，才会调用这里
        // 这是责任链的"最后兜底"节点
        byte[] classBytes = loadFromCustomSource(name);
        if (classBytes == null) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, classBytes, 0, classBytes.length);
    }

    private byte[] loadFromCustomSource(String name) {
        // 从加密 jar、网络、数据库等非标准位置加载字节码
        return null;  // 示例：返回 null 表示找不到
    }
}
```

**`javax.servlet.Filter` 链**: Servlet 规范中最接近 GoF 原始定义的实现——`FilterChain` 就是链对象，每个 `Filter` 通过调用 `chain.doFilter()` 决定是否继续。

```java
@Component
@Order(1)  // 控制链中的顺序
public class JwtAuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req,
                         ServletResponse res,
                         FilterChain chain)    // 链对象，代表"剩余的处理链"
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        String token = request.getHeader("Authorization");

        if (token == null || !token.startsWith("Bearer ")) {
            // 在这里终止链，直接返回 401，后续 Filter 和 Servlet 都不会执行
            ((HttpServletResponse) res).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // 验证通过：把解析结果存入 request 属性，传给链中下一个节点
        request.setAttribute("userId", parseToken(token));
        chain.doFilter(req, res);  // 继续传递：下一个 Filter 或最终 Servlet
    }

    private String parseToken(String token) { return "user-123"; }
}
```

### 现代写法：`Optional` 链式查找

当责任链的目的是"找到第一个能提供结果的来源"时，`Optional.or()` 比传统类链更简洁——不需要定义 Handler 接口和 successor 字段，纯函数组合即可表达链式查找语义。

```java
import java.util.Optional;
import java.util.Map;

public class UserRepository {

    // 模拟三级存储（从快到慢）
    private final Map<Long, User> localCache = Map.of(1L, new User(1L, "Alice"));
    private final Map<Long, User> redisCache  = Map.of(2L, new User(2L, "Bob"));
    private final Map<Long, User> database    = Map.of(3L, new User(3L, "Carol"));

    public Optional<User> findUser(long id) {
        return findInLocalCache(id)       // 第一个处理者：本地缓存
            .or(() -> findInRedis(id))    // 未命中，第二个处理者：Redis（惰性求值）
            .or(() -> findInDatabase(id)) // 还未命中，第三个处理者：数据库（惰性求值）
            // .or(() -> findInRemote(id)) 随时可以在链尾追加新处理者
            ;
    }

    private Optional<User> findInLocalCache(long id) {
        return Optional.ofNullable(localCache.get(id));
    }

    private Optional<User> findInRedis(long id) {
        System.out.println("  [Redis] 查找 id=" + id);  // 只有本地缓存未命中时才打印
        return Optional.ofNullable(redisCache.get(id));
    }

    private Optional<User> findInDatabase(long id) {
        System.out.println("  [DB] 查找 id=" + id);    // 只有前两级都未命中时才打印
        return Optional.ofNullable(database.get(id));
    }

    // 使用示例
    public static void main(String[] args) {
        var repo = new UserRepository();

        // id=1: 本地缓存命中，Redis 和 DB 的 lambda 根本不会执行
        repo.findUser(1).ifPresentOrElse(
            u -> System.out.println("找到: " + u),
            ()  -> System.out.println("未找到"));

        // id=4: 三级都未命中
        repo.findUser(4).ifPresentOrElse(
            u -> System.out.println("找到: " + u),
            ()  -> System.out.println("未找到"));
    }

    record User(long id, String name) {}
}
```

---

## Python 实战: Django 中间件 + FastAPI + Python MRO

### 源码解析

Python Web 生态中，责任链以"中间件栈"的形式无处不在。

**Django 中间件**: Django 的中间件列表按顺序形成一条链。请求从上往下流经每个中间件的 `__call__`，响应从下往上流回。

```python
# settings.py — 中间件列表就是链的定义，顺序即链的顺序
MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",          # 节点 1
    "django.contrib.sessions.middleware.SessionMiddleware",   # 节点 2
    "django.middleware.csrf.CsrfViewMiddleware",              # 节点 3
    "django.contrib.auth.middleware.AuthenticationMiddleware",# 节点 4
    "myapp.middleware.RateLimitMiddleware",                   # 节点 5（自定义）
]

# 自定义限流中间件
class RateLimitMiddleware:
    def __init__(self, get_response):
        # get_response 就是"下一个处理者"——Django 框架在启动时自动注入
        self.get_response = get_response
        self._counts: dict[str, int] = {}  # ip -> 请求次数（简化，不含时间窗口）

    def __call__(self, request):
        ip = request.META.get("REMOTE_ADDR", "unknown")
        self._counts[ip] = self._counts.get(ip, 0) + 1

        if self._counts[ip] > 100:
            from django.http import HttpResponse
            # 在此终止链：直接返回 429，后续中间件和 View 不执行
            return HttpResponse("Too Many Requests", status=429)

        # 继续传递给链中下一个处理者（可能是另一个中间件，也可能是 View）
        return self.get_response(request)
```

**FastAPI 中间件**: FastAPI 基于 Starlette，中间件用 `@app.middleware("http")` 注册，链的传递通过 `await call_next(request)` 完成。

```python
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
import time

app = FastAPI()

# 每个 @app.middleware 装饰的函数是链上的一个节点
@app.middleware("http")
async def timing_middleware(request: Request, call_next):
    """计时中间件：包裹整个处理链，记录耗时"""
    start = time.perf_counter()
    response = await call_next(request)  # 传给链中后续节点
    elapsed = time.perf_counter() - start
    response.headers["X-Process-Time"] = f"{elapsed:.4f}"
    return response  # 响应从这里回流给客户端

@app.middleware("http")
async def auth_middleware(request: Request, call_next):
    """认证中间件：无 token 则在链中终止"""
    token = request.headers.get("Authorization")
    if not token:
        # 终止链，直接返回 401，后续节点不会执行
        return JSONResponse({"error": "Unauthorized"}, status_code=401)
    return await call_next(request)  # 认证通过，继续传递
```

**Python MRO + `super()` 即责任链**: Python 的多重继承解析顺序（Method Resolution Order）本身就是一条责任链。每个类的方法可以选择调用 `super()` 把控制权传给 MRO 链中的下一个类，也可以选择在这里终止。

```python
class LogMixin:
    """链节点 1：打印日志，然后传给下一个"""
    def handle(self, request: str) -> str | None:
        print(f"[LOG] 处理请求: {request}")
        return super().handle(request)  # 传给 MRO 链中的下一个类

class AuthMixin:
    """链节点 2：检查权限，失败则终止"""
    def handle(self, request: str) -> str | None:
        if request.startswith("FORBIDDEN"):
            print(f"[AUTH] 拒绝请求: {request}")
            return None  # 终止链，不调用 super()
        return super().handle(request)  # 通过，继续传递

class BaseHandler:
    """链尾：真正执行业务逻辑"""
    def handle(self, request: str) -> str | None:
        return f"处理完成: {request}"

# MRO 决定链的顺序: LogMixin → AuthMixin → BaseHandler → object
class SecureHandler(LogMixin, AuthMixin, BaseHandler):
    pass

handler = SecureHandler()
print(handler.handle("GET /api/data"))      # 日志 → 认证通过 → 处理完成
print(handler.handle("FORBIDDEN /secret"))  # 日志 → 认证拒绝 → None
```

**`logging.Handler` 链**: Python 标准库的 `logging` 模块与 JDK 的 `Logger` 父链对称——`Logger` 对象本身持有 `parent` 引用，日志记录向上冒泡。

```python
import logging

# 创建三级 Logger 层次
root_logger = logging.getLogger()                  # 根节点
app_logger  = logging.getLogger("myapp")           # 中间节点
db_logger   = logging.getLogger("myapp.database")  # 叶节点

# 只在根 Logger 挂 Handler
root_logger.setLevel(logging.WARNING)
root_logger.addHandler(logging.StreamHandler())

# db_logger 没有 Handler，但 propagate=True（默认），日志会冒泡到 app_logger 再到 root
db_logger.setLevel(logging.DEBUG)  # 本级 Level 放宽，但根 Logger 还是过滤 WARNING 以下

db_logger.debug("SQL 查询开始")     # 被根 Logger 的 Level 过滤掉，不输出
db_logger.warning("慢查询警告")     # 冒泡到 root_logger，输出
db_logger.error("连接失败")        # 冒泡到 root_logger，输出

# propagate=False 可以截断冒泡（在链中终止）
app_logger.propagate = False  # app_logger 的日志不再向上传递给 root_logger
```

### Pythonic 写法：`match` + guard 实现多级审批链

用 Python 3.10 的 `match` 语句把多级审批逻辑写成声明式风格，每个 `case` 分支就是一个处理节点：

```python
from dataclasses import dataclass

@dataclass
class ApprovalRequest:
    amount: float       # 申请金额
    requester: str      # 申请人
    reason: str         # 申请原因

def approve(request: ApprovalRequest) -> str:
    """
    审批链：根据金额路由到不同审批人。
    match + guard 把每个处理条件表达得一目了然。
    """
    match request:
        case ApprovalRequest(amount=amt) if amt <= 1_000:
            # 小额：组长直接审批
            return f"组长审批通过: ¥{amt:.2f} ({request.requester})"

        case ApprovalRequest(amount=amt) if amt <= 10_000:
            # 中额：需要部门经理
            return f"经理审批通过: ¥{amt:.2f} ({request.requester})"

        case ApprovalRequest(amount=amt) if amt <= 100_000:
            # 大额：需要总监
            return f"总监审批通过: ¥{amt:.2f} ({request.requester})"

        case ApprovalRequest(amount=amt, reason=reason) if "紧急" in reason:
            # 任意金额，只要理由含"紧急"，走紧急审批通道
            return f"紧急审批通道: ¥{amt:.2f} — 需 CEO 签字"

        case _:
            # 链尾兜底：超出所有处理者能力范围
            return f"金额 ¥{request.amount:.2f} 超出审批权限，请提交董事会"


# 构建函数式责任链（运行时动态组装，不依赖类继承）
from typing import Callable

Handler = Callable[[ApprovalRequest], str | None]

def build_chain(*handlers: Handler) -> Handler:
    """
    把多个处理函数组装成责任链。
    每个 handler 返回 None 表示"我不处理，交给下一个"，
    返回字符串表示"我处理了，链终止"。
    """
    def chain(request: ApprovalRequest) -> str | None:
        for handler in handlers:
            if (result := handler(request)) is not None:  # 海象运算符
                return result
        return None  # 所有处理者都放弃，请求未被处理

    return chain


def manager_handler(req: ApprovalRequest) -> str | None:
    return f"经理批准: ¥{req.amount}" if req.amount <= 5_000 else None

def director_handler(req: ApprovalRequest) -> str | None:
    return f"总监批准: ¥{req.amount}" if req.amount <= 50_000 else None

def vp_handler(req: ApprovalRequest) -> str | None:
    return f"VP 批准: ¥{req.amount}" if req.amount <= 200_000 else None


# 组装链：可以在运行时动态调整顺序或插入新节点
approval_chain = build_chain(manager_handler, director_handler, vp_handler)

print(approval_chain(ApprovalRequest(3_000, "张三", "差旅费")))    # 经理批准
print(approval_chain(ApprovalRequest(30_000, "李四", "设备采购"))) # 总监批准
print(approval_chain(ApprovalRequest(500_000, "王五", "大项目")))  # None（超出所有权限）
```

---

## 两种哲学

| 维度 | Java | Python |
|------|------|--------|
| 链的结构 | 类继承层次 + `next` 引用（对象链表） | 函数列表 / 装饰器栈 / MRO 链 |
| 链的组装 | 构造函数注入，或框架（如 Servlet 容器）自动组装 | 列表顺序 / `@app.middleware` 注册顺序 |
| 终止信号 | 不调用 `next.handle()` / 不调用 `chain.doFilter()` | 不调用 `call_next()` / 返回 `None` / 不调用 `super()` |
| 请求未处理 | 返回 `null` 或抛 `UnsupportedOperationException` | 返回 `None` 或抛 `ValueError` |
| 类型安全 | 编译期检查 Handler 接口实现 | 运行期检查，可用 `Protocol` 约束 |
| 典型容器 | Servlet FilterChain / Spring SecurityFilterChain | WSGI/ASGI 中间件栈 / Django MIDDLEWARE |

**链的两种形状**:

```java
// Java 风格：显式链表，每个节点持有 next 引用
Support chain = new LimitSupport(10);     // 链头
chain.setNext(new OddSupport())           // 节点 2
     .setNext(new SpecialSupport(429))    // 节点 3
     .setNext(new LimitSupport(Integer.MAX_VALUE)); // 链尾兜底

chain.support(new Trouble(7));  // 从链头出发，沿 next 引用传递
```

```python
# Python 风格：函数列表，build_chain 动态组装
chain = build_chain(
    limit_handler(10),         # 节点 1
    odd_handler,               # 节点 2
    special_handler(429),      # 节点 3
    limit_handler(float('inf')) # 链尾兜底
)
chain(Trouble(7))  # 从列表头部出发，依次尝试
```

Python 的函数式风格优势在于：链的节点可以是任意可调用对象（函数、lambda、类实例），组装方式极其灵活，不需要继承任何基类。

---

## 动手练习

**15.1 Java — 三级审批链**

实现一个费用审批系统，用传统类继承风格（`abstract class Approver`）：
- `Manager`：批准 1 万以下的申请
- `Director`：批准 10 万以下的申请
- `VP`：批准 100 万以下的申请
- 超出所有权限时，打印"无法审批，请提交董事会"

用 `record ExpenseRequest(String requester, double amount, String reason)` 作为请求对象。用 `Optional<String>` 作为处理结果类型，链尾返回 `Optional.empty()`。

**15.2 Python — 中间件链**

实现一个简化的 HTTP 请求处理管道（不依赖任何框架）：

```python
# 每个中间件签名
Handler = Callable[[dict, Callable], dict]

# 需要实现的中间件节点：
# 1. logging_middleware：打印请求方法和路径
# 2. auth_middleware：检查 headers["Authorization"]，缺失则返回 {"status": 401}
# 3. rate_limit_middleware：简单计数，超过 5 次返回 {"status": 429}
# 4. business_middleware：真正的业务逻辑，返回 {"status": 200, "body": "OK"}
```

用 `build_chain(*handlers)` 组装，验证每个节点的终止和传递行为。

**15.3 思考题 — CoR vs Decorator**

观察以下两段代码，哪个是 Decorator，哪个是 Chain of Responsibility？判断依据是什么？

```python
# 代码 A
def a(request, next):
    request["logged"] = True  # 总是执行
    result = next(request)    # 总是调用下一个
    result["timing"] = 0.01   # 总是执行
    return result

# 代码 B
def b(request, next):
    if not request.get("token"):
        return {"status": 401}  # 条件满足时终止，不调用 next
    return next(request)
```

再思考：为什么 Django 的 `MIDDLEWARE` 既像 Decorator（每层都执行）又像 CoR（某些中间件会短路）？这两个模式的边界是否真的清晰？

---

## 回顾与连接

**与相关模式的区别**:

- **CoR vs Decorator (Ch09)**: 这是最容易混淆的一对。两者都是链式结构，每个节点持有下一个节点的引用，接口相同。**意图是分水岭**：Decorator 总是调用下一个（透明地叠加功能），CoR 可以在任意节点终止（寻找合适的处理者）。Django 中间件横跨两者：日志中间件像 Decorator，认证中间件像 CoR。

- **CoR vs Command (Ch13)**: Command 把"一个操作"封装成对象；CoR 把"一系列候选处理者"连成链。两者可以组合——`CommandQueue` 可以是一条责任链，每个节点检查自己是否能执行该命令。

- **CoR vs Strategy (Ch02)**: Strategy 是"选哪个算法"，选择在外部完成，算法对象本身不知道其他选项；CoR 是"谁来处理"，选择在链内部完成，每个处理者自主判断。

- **CoR vs Observer (Ch08)**: Observer 是广播——所有订阅者都会收到通知；CoR 是传递——通常只有一个处理者最终处理请求（短路）。若 CoR 的每个节点都不短路，则退化为 Observer 语义。

**设计要点**:

1. **链尾必须有兜底**: 如果请求可能沿链跑完没人处理，链尾需要有一个"默认处理者"，或者调用方需要检查返回值是否为 `null`/`None`，否则请求静默消失，难以调试。

2. **链长与性能**: 链越长，每个请求的处理开销越大（最坏情况要遍历全链）。HTTP 中间件链通常不超过 10 个节点，过长的链应考虑合并职责或用其他模式。

3. **动态可变链**: CoR 最大的优势是链可以在运行时重组。Java 中通过改变 `next` 引用实现，Python 中通过修改处理函数列表实现。若链是静态的，简单的 `if-else` 链往往更清晰。

4. **请求对象设计**: 请求对象最好是不可变的（`record` / `dataclass(frozen=True)`），防止链中某个节点修改请求后影响后续节点的判断，造成难以追踪的 bug。若需要在链中累积信息，使用独立的上下文对象（如 Servlet 的 `request.setAttribute()`）而非修改原始请求。
