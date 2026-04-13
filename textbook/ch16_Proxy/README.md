# Chapter 16: Proxy

> 类型: 结构 | 难度: ★★☆ | 原书: `src/ch21_Proxy/` | 前置: Ch09 (Decorator)

---

## 模式速览

**问题**: 有时你不想（或不能）直接访问一个对象。原因可能是：对象在远程机器上、对象创建代价极高（需要懒加载）、需要在访问前做权限检查、或者想缓存重复查询的结果。Proxy 的解法是：在客户端和真实对象之间放一个"替代者"——它实现相同接口，客户端无需感知它在和代理还是真实对象打交道。

```
Client ──────→ «interface»
                 Subject
                ┌────────────┐
                │ request()  │
                └─────┬──────┘
                      │ implements
          ┌───────────┴────────────┐
          │                        │
       Proxy                  RealSubject
  ┌──────────────────┐    ┌──────────────────┐
  │ - realSubject    │    │                  │
  │ request() {      │───▶│ request()        │
  │   // 前置逻辑    │    │ // 真正的实现    │
  │   real.request() │    └──────────────────┘
  │   // 后置逻辑   │
  │ }               │
  └──────────────────┘
```

**四个角色**:
- `Subject` — 抽象接口，定义 Proxy 和 RealSubject 共同遵守的契约
- `RealSubject` — 真实对象，持有真正的业务逻辑
- `Proxy` — 代理对象，持有 RealSubject 引用，在转发请求前后插入额外逻辑
- `Client` — 面向 Subject 接口编程，不感知自己在访问的是代理还是真实对象

**五种常见变体**:

| 变体 | 核心意图 | 典型例子 |
|------|---------|---------|
| 远程代理 (Remote) | 让本地代码调用远程对象 | gRPC stub、RMI |
| 虚拟代理 (Virtual/Lazy) | 延迟创建昂贵对象 | Hibernate 懒加载 |
| 保护代理 (Protection) | 在访问前做权限检查 | Spring Security |
| 缓存代理 (Caching) | 缓存重复请求结果 | `@Cacheable`、nginx cache |
| 日志代理 (Logging) | 记录所有访问 | AOP logging |

**核心洞察**: Proxy 和 Decorator 的类图结构几乎一模一样——都持有同接口的引用并委托调用。区别纯粹在于**意图**：Decorator 在调用前后*添加行为*；Proxy *控制访问*本身（是否调用、何时调用、谁能调用）。

---

## 本章新语言特性

| 特性 | Java | Python |
|------|------|--------|
| 运行时动态代理 | `java.lang.reflect.Proxy` + `InvocationHandler` | `__getattr__` 透明委托 |
| 弱引用代理 | `java.lang.ref.WeakReference` | `weakref.proxy()` |
| 保护代理 | `Collections.unmodifiableList()` | 描述符协议 (`__get__`, `__set__`) |
| 缓存代理 | 手动实现或 `@Cacheable` | `functools.cached_property` |
| 测试代理 | Mockito `mock()` | `unittest.mock.MagicMock` |

### `java.lang.reflect.Proxy` — 运行时动态创建代理类

Java 的动态代理在运行时（而非编译时）生成代理类的字节码，无需手写每个接口的包装类。只需实现 `InvocationHandler`，它拦截对代理对象的所有方法调用。

```java
// InvocationHandler 是动态代理的核心：拦截所有方法调用
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

// 1. 定义接口（Proxy 只能代理接口，不能代理具体类）
interface UserService {
    String findById(int id);
    void save(String name);
}

// 2. 真实实现
class UserServiceImpl implements UserService {
    @Override
    public String findById(int id) {
        return "User#" + id;
    }

    @Override
    public void save(String name) {
        System.out.println("保存用户: " + name);
    }
}

// 3. InvocationHandler：所有方法调用都经过 invoke()
class LoggingHandler implements InvocationHandler {

    private final Object target;   // 被代理的真实对象

    LoggingHandler(Object target) {
        this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 前置逻辑：记录调用
        System.out.printf("[LOG] 调用 %s.%s%n",
            target.getClass().getSimpleName(), method.getName());
        long start = System.nanoTime();

        // 转发给真实对象
        Object result = method.invoke(target, args);

        // 后置逻辑：记录耗时
        long elapsed = System.nanoTime() - start;
        System.out.printf("[LOG] %s 完成，耗时 %.3fms%n",
            method.getName(), elapsed / 1_000_000.0);

        return result;
    }
}

// 4. 创建代理实例
UserService real = new UserServiceImpl();
UserService proxy = (UserService) Proxy.newProxyInstance(
    real.getClass().getClassLoader(),   // 类加载器
    new Class<?>[]{ UserService.class }, // 代理需实现的接口列表
    new LoggingHandler(real)            // 拦截器
);

proxy.findById(42);    // 触发 invoke()，自动记录日志
proxy.save("Alice");
```

`Proxy.newProxyInstance()` 在运行时生成一个实现了 `UserService` 接口的类，该类的所有方法都路由到 `LoggingHandler.invoke()`。这是 Spring AOP、MyBatis Mapper 等框架的核心机制。

### Python 描述符协议 — 属性级代理

描述符协议（descriptor protocol）让你拦截对**单个属性**的读写删操作，实现属性级别的 Proxy。

```python
class ValidatedField:
    """
    描述符：对属性赋值时做类型和范围校验（保护代理语义）
    实现了 __get__ / __set__ 的对象叫做描述符（descriptor）
    """

    def __set_name__(self, owner, name):
        # Python 3.6+ 自动调用：owner 是持有此描述符的类，name 是属性名
        self._name = f"_{name}"   # 存储在实例的私有属性上，避免循环引用

    def __get__(self, obj, objtype=None):
        if obj is None:
            return self   # 通过类访问时返回描述符本身（如 Person.age）
        return getattr(obj, self._name, None)

    def __set__(self, obj, value: int):
        if not isinstance(value, int):
            raise TypeError(f"{self._name} 必须是整数，收到 {type(value).__name__}")
        if value < 0:
            raise ValueError(f"{self._name} 不能为负数")
        setattr(obj, self._name, value)


class Person:
    age = ValidatedField()   # 描述符实例作为类属性
    score = ValidatedField()

    def __init__(self, age: int, score: int):
        self.age = age       # 触发 ValidatedField.__set__
        self.score = score


p = Person(25, 90)
print(p.age)        # 触发 __get__，输出 25
# p.age = -1       # 触发 __set__，抛 ValueError
# p.age = "old"    # 触发 __set__，抛 TypeError
```

### Python `__getattr__` — 对象级透明代理

`__getattr__` 只在属性查找失败时才被调用，是实现透明委托代理的理想钩子。

```python
class TransparentProxy:
    """
    通用透明代理：把所有未知属性访问委托给被包装对象。
    使用 object.__setattr__ / object.__getattribute__ 绕过自身的 __getattr__，
    避免无限递归。
    """

    def __init__(self, target):
        object.__setattr__(self, "_target", target)   # 绕过 __setattr__

    def __getattr__(self, name: str):
        # 只有当 self 本身没有 name 属性时才被调用
        target = object.__getattribute__(self, "_target")
        return getattr(target, name)   # 委托给真实对象
```

---

## Java 实战: `java.lang.reflect.Proxy` + `Collections.unmodifiableList`

### 源码解析

#### 动态代理实现 AOP 风格的横切关注点

Spring AOP 的底层原理就是动态代理。下面用纯 JDK 实现一个支持日志 + 计时的通用代理工厂：

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/**
 * 通用日志代理工厂：为任意接口创建自动记录日志的代理
 * 使用 Java 21+ 的 record 简化 handler 封装
 */
public class ProxyFactory {

    /**
     * 为 target 对象创建代理，在每次方法调用前后记录日志
     * @param interfaceType 要代理的接口
     * @param target        真实对象（必须实现 interfaceType）
     */
    @SuppressWarnings("unchecked")
    public static <T> T createLoggingProxy(Class<T> interfaceType, T target) {
        InvocationHandler handler = (proxy, method, args) -> {
            // 前置：打印方法名和参数
            System.out.printf("[PROXY] → %s(%s)%n",
                method.getName(),
                args == null ? "" : Arrays.toString(args));

            long start = System.nanoTime();
            try {
                Object result = method.invoke(target, args);
                // 后置：打印返回值
                System.out.printf("[PROXY] ← %s 返回 %s (%.2fms)%n",
                    method.getName(), result,
                    (System.nanoTime() - start) / 1_000_000.0);
                return result;
            } catch (Exception e) {
                // 异常后置：记录失败
                System.out.printf("[PROXY] ✗ %s 抛出 %s%n",
                    method.getName(), e.getCause().getMessage());
                throw e.getCause();   // 重新抛出原始异常
            }
        };

        return (T) Proxy.newProxyInstance(
            interfaceType.getClassLoader(),
            new Class<?>[]{ interfaceType },
            handler
        );
    }

    public static void main(String[] args) {
        // 为 UserService 创建日志代理
        var real = new UserServiceImpl();
        var proxy = createLoggingProxy(UserService.class, real);

        proxy.findById(1);       // 自动记录: → findById([1]) ... ← findById 返回 User#1
        proxy.save("Bob");       // 自动记录: → save([Bob]) ... ← save 返回 null
    }
}
```

#### `Collections.unmodifiableList` — 保护代理

`Collections.unmodifiableList()` 是 JDK 内置的保护代理。它包装原始列表，允许读操作、阻止写操作：

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

var mutable = new ArrayList<String>();
mutable.add("Alice");
mutable.add("Bob");
mutable.add("Charlie");

// 保护代理：对外暴露只读视图，防止外部代码意外修改
List<String> readOnly = Collections.unmodifiableList(mutable);

System.out.println(readOnly.get(0));    // 读操作：正常
System.out.println(readOnly.size());   // 读操作：正常
// readOnly.add("Dave");               // 写操作：运行时抛 UnsupportedOperationException

// 保护代理不是快照：原列表变化时，代理视图也跟着变
mutable.add("Dave");
System.out.println(readOnly.size());   // 输出 4（Dave 可见）

// Java 10+ 真正不可变列表（快照，防御性拷贝）
List<String> immutable = List.copyOf(mutable);
```

#### RMI — 远程代理的概念

远程方法调用（RMI）是远程代理的经典实现。客户端持有的 stub 对象在本地看来和普通对象没有区别，但每次方法调用都通过网络转发到远程服务器：

```java
// RMI 接口（必须 extends Remote，每个方法声明 RemoteException）
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface PrintService extends Remote {
    void print(String message) throws RemoteException;
    int getJobCount() throws RemoteException;
}

// 客户端代码：和调用本地对象没有区别
// stub 就是远程代理——它实现 PrintService，内部把调用序列化并通过 TCP 发送
PrintService stub = (PrintService) Naming.lookup("//server/PrintService");
stub.print("Hello from client");   // 透明地触发远程调用
```

### 现代重写：用动态代理实现事务管理

模拟 Spring `@Transactional` 的实现原理——动态代理拦截方法调用，在调用前后管理事务：

```java
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Proxy;

// 标记需要事务管理的方法
@Retention(RetentionPolicy.RUNTIME)
@interface Transactional {}

// 事务管理器（简化）
class TransactionManager {
    void begin()    { System.out.println("[TX] begin"); }
    void commit()   { System.out.println("[TX] commit"); }
    void rollback() { System.out.println("[TX] rollback"); }
}

// 通用事务代理工厂
class TransactionalProxy {

    private static final TransactionManager TX = new TransactionManager();

    @SuppressWarnings("unchecked")
    static <T> T wrap(Class<T> iface, T target) {
        return (T) Proxy.newProxyInstance(
            iface.getClassLoader(),
            new Class<?>[]{ iface },
            (proxy, method, args) -> {
                // 仅对标注了 @Transactional 的方法开启事务
                if (method.isAnnotationPresent(Transactional.class)) {
                    TX.begin();
                    try {
                        Object result = method.invoke(target, args);
                        TX.commit();
                        return result;
                    } catch (Throwable e) {
                        TX.rollback();   // 出现异常时自动回滚
                        throw e.getCause();
                    }
                }
                return method.invoke(target, args);   // 无注解，直接调用
            }
        );
    }
}

// 业务接口与实现
interface BankService {
    @Transactional
    void transfer(String from, String to, double amount);

    double getBalance(String account);   // 无事务
}

class BankServiceImpl implements BankService {
    @Override
    public void transfer(String from, String to, double amount) {
        System.out.printf("转账 %.2f 从 %s 到 %s%n", amount, from, to);
        // 模拟转账逻辑（如果这里抛异常，代理自动回滚）
    }

    @Override
    public double getBalance(String account) {
        return 1000.0;
    }
}

// 使用
BankService service = TransactionalProxy.wrap(BankService.class, new BankServiceImpl());
service.transfer("Alice", "Bob", 100.0);
// 输出:
// [TX] begin
// 转账 100.00 从 Alice 到 Bob
// [TX] commit
```

---

## Python 实战: `__getattr__` + `weakref.proxy` + `unittest.mock`

### 源码解析

#### `weakref.proxy()` — 不阻止垃圾回收的代理

标准库的 `weakref.proxy()` 创建一个弱引用代理：代理对象的行为和原对象完全一致，但不会增加引用计数，从而不阻止垃圾回收：

```python
import weakref

class HeavyObject:
    """模拟一个占用大量内存的对象"""
    def __init__(self, name: str):
        self.name = name

    def compute(self) -> str:
        return f"{self.name} 计算结果"

    def __del__(self):
        print(f"{self.name} 被垃圾回收")


# 普通引用：obj 存活期间，heavy 不会被回收
heavy = HeavyObject("重型对象")
proxy = weakref.proxy(heavy)   # 弱引用代理

print(proxy.name)          # 透明访问属性: 重型对象
print(proxy.compute())     # 透明调用方法: 重型对象 计算结果

# 删除原始引用，弱引用代理失效
del heavy                  # 打印: 重型对象 被垃圾回收

try:
    print(proxy.name)      # 抛 ReferenceError: weakly-referenced object no longer exists
except ReferenceError as e:
    print(f"代理失效: {e}")
```

#### `unittest.mock.MagicMock` — 测试代理

`MagicMock` 是 Python 中最强大的代理——它动态响应任意方法调用，可以精确控制返回值和副作用，是测试中隔离依赖的利器：

```python
from unittest.mock import MagicMock, patch, call

# 场景：测试 OrderService，但不想真正调用 PaymentGateway
class PaymentGateway:
    def charge(self, amount: float, card: str) -> bool:
        # 真实实现会发起网络请求
        ...

class OrderService:
    def __init__(self, payment: PaymentGateway):
        self._payment = payment

    def place_order(self, item: str, price: float, card: str) -> str:
        success = self._payment.charge(price, card)
        if success:
            return f"订单成功: {item}"
        else:
            return "支付失败"


# MagicMock 作为 PaymentGateway 的代理
mock_payment = MagicMock(spec=PaymentGateway)   # spec 确保代理与真实接口一致
mock_payment.charge.return_value = True          # 设定代理的返回值

service = OrderService(mock_payment)
result = service.place_order("咖啡", 35.0, "4111-xxxx")

assert result == "订单成功: 咖啡"
mock_payment.charge.assert_called_once_with(35.0, "4111-xxxx")   # 验证调用参数
```

#### `functools.cached_property` — 懒加载缓存代理

`cached_property` 是虚拟代理（Virtual Proxy）的内置实现：属性第一次被访问时才计算，结果缓存在实例字典中，后续访问直接返回缓存：

```python
import functools
import time

class DataReport:
    def __init__(self, raw_data: list[dict]):
        self._raw = raw_data

    @functools.cached_property
    def summary(self) -> dict:
        """虚拟代理：第一次访问时才执行昂贵计算，之后返回缓存结果"""
        print("[计算中] 正在生成报告摘要...")
        time.sleep(0.1)   # 模拟耗时计算（实际可能是大量数据库查询）
        total = sum(r.get("amount", 0) for r in self._raw)
        return {
            "count": len(self._raw),
            "total": total,
            "avg": total / len(self._raw) if self._raw else 0,
        }

    @functools.cached_property
    def top_items(self) -> list:
        """另一个昂贵属性，同样懒加载"""
        print("[计算中] 正在排序...")
        return sorted(self._raw, key=lambda r: r.get("amount", 0), reverse=True)[:5]


report = DataReport([{"amount": 100}, {"amount": 300}, {"amount": 200}])
print(report.summary)     # 第一次：触发计算，打印 "[计算中]..."
print(report.summary)     # 后续：直接从缓存返回，无打印
```

#### SQLAlchemy 懒加载 — 数据库层的虚拟代理

SQLAlchemy 对关联对象使用懒加载代理，只有真正访问关联属性时才发出 SQL 查询：

```python
from sqlalchemy.orm import DeclarativeBase, relationship, Mapped, mapped_column
from sqlalchemy import FetchType

class Base(DeclarativeBase):
    pass

class Order(Base):
    __tablename__ = "orders"

    id: Mapped[int] = mapped_column(primary_key=True)
    total: Mapped[float]

    # lazy="select"（默认）：user 属性是 SQLAlchemy 内部代理对象
    # 访问时才执行 SELECT * FROM users WHERE id = ?
    user: Mapped["User"] = relationship("User", lazy="select")

# 查询订单：只执行 SELECT * FROM orders WHERE id = ?
order = session.get(Order, 1)

# 此时 order.user 是代理，尚未查询数据库
# 只有访问 user 的属性时，代理才触发真正的 SELECT
print(order.user.name)   # ← 此时代理自动发出第二条 SQL
```

### Pythonic 重写：完整的懒加载代理

结合 `__getattr__` 和类型注解，实现一个生产可用的懒加载代理：

```python
from typing import TypeVar, Generic, Callable

T = TypeVar("T")

class LazyProxy(Generic[T]):
    """
    通用懒加载代理（Virtual Proxy）：
    - 构造时不初始化真实对象
    - 第一次属性访问时才调用 factory 创建
    - 之后所有访问透明委托给真实对象
    """

    def __init__(self, factory: Callable[[], T]):
        # 必须用 object.__setattr__ 绕过自定义的 __setattr__（如果有）
        object.__setattr__(self, "_factory", factory)
        object.__setattr__(self, "_instance", None)

    def _get_instance(self) -> T:
        instance = object.__getattribute__(self, "_instance")
        if instance is None:
            factory = object.__getattribute__(self, "_factory")
            print("[LazyProxy] 首次访问，正在初始化真实对象...")
            instance = factory()
            object.__setattr__(self, "_instance", instance)
        return instance

    def __getattr__(self, name: str):
        # __getattr__ 仅在正常属性查找失败时调用
        # 代理所有属性访问到真实对象
        return getattr(self._get_instance(), name)

    def __repr__(self) -> str:
        instance = object.__getattribute__(self, "_instance")
        if instance is None:
            return f"LazyProxy(未初始化)"
        return f"LazyProxy({instance!r})"


# 使用示例：延迟建立数据库连接
class DatabaseConnection:
    def __init__(self, url: str):
        print(f"[DB] 建立连接: {url}")
        self.url = url

    def query(self, sql: str) -> list:
        print(f"[DB] 执行: {sql}")
        return [{"id": 1}, {"id": 2}]


# 程序启动时创建代理（不建立真实连接）
db: DatabaseConnection = LazyProxy(
    lambda: DatabaseConnection("postgres://localhost/prod")
)

print("程序初始化完成，尚未连接数据库")
print(repr(db))         # LazyProxy(未初始化)

# 只有真正用到时才建立连接
results = db.query("SELECT * FROM users")   # ← 此时才初始化
results2 = db.query("SELECT * FROM orders") # 复用同一连接
```

### 保护代理与访问控制

```python
from typing import Any

class ProtectionProxy:
    """
    保护代理：基于角色的访问控制（RBAC）
    在将请求转发给真实对象前检查权限
    """

    # 定义哪些方法需要哪些角色
    _PERMISSIONS: dict[str, set[str]] = {
        "delete_user": {"admin", "superuser"},
        "update_user": {"admin", "superuser", "manager"},
        "list_users":  {"admin", "superuser", "manager", "viewer"},
    }

    def __init__(self, service: Any):
        object.__setattr__(self, "_service", service)
        object.__setattr__(self, "_role", None)

    def authenticate(self, role: str) -> None:
        object.__setattr__(self, "_role", role)

    def __getattr__(self, name: str):
        role = object.__getattribute__(self, "_role")
        permissions = ProtectionProxy._PERMISSIONS

        if name in permissions:
            allowed_roles = permissions[name]
            if role not in allowed_roles:
                raise PermissionError(
                    f"角色 '{role}' 无权访问 '{name}'，"
                    f"需要: {allowed_roles}"
                )

        service = object.__getattribute__(self, "_service")
        return getattr(service, name)


class UserAdmin:
    def delete_user(self, user_id: int) -> None:
        print(f"删除用户 {user_id}")

    def update_user(self, user_id: int, data: dict) -> None:
        print(f"更新用户 {user_id}: {data}")

    def list_users(self) -> list:
        return [{"id": 1, "name": "Alice"}, {"id": 2, "name": "Bob"}]


# 用保护代理包装真实服务
proxy = ProtectionProxy(UserAdmin())
proxy.authenticate("viewer")

print(proxy.list_users())    # OK：viewer 有权限
try:
    proxy.delete_user(1)     # PermissionError：viewer 无权删除
except PermissionError as e:
    print(e)

proxy.authenticate("admin")
proxy.delete_user(1)         # OK：admin 有权限
```

---

## 两种哲学

| 维度 | Java | Python |
|------|------|--------|
| 静态代理 | 手动实现接口，逐个方法委托 | 手动实现 `__getattr__`，一次性委托所有 |
| 动态代理 | `java.lang.reflect.Proxy` + `InvocationHandler` | `__getattr__` 天然就是动态代理 |
| 接口要求 | 动态代理**必须**基于接口（CGLib 可代理类） | 鸭子类型，无需声明接口 |
| 类型安全 | 编译期接口约束 | `spec=` 参数 + 类型注解 |
| 弱引用代理 | `WeakReference<T>`（需手动解引用） | `weakref.proxy()`（透明，自动转发） |
| 测试代理 | Mockito `mock(Interface.class)` | `unittest.mock.MagicMock` |
| 懒加载 | 手动 null-check 双重检验锁 | `functools.cached_property` |
| 典型框架用途 | Spring AOP、MyBatis Mapper 接口、Hibernate | SQLAlchemy 懒加载、Django ORM |

**结构对比**:

```java
// Java 动态代理：代码需要显式创建，调用方仍面向接口
UserService proxy = (UserService) Proxy.newProxyInstance(
    loader, new Class[]{ UserService.class }, handler
);
// 调用方只看到 UserService 接口，不知道背后是代理
```

```python
# Python __getattr__ 代理：不需要接口声明，鸭子类型天然支持
proxy = TransparentProxy(real_service)
# 所有属性访问自动转发，调用方无需感知代理的存在
proxy.any_method()    # __getattr__ 自动处理
```

**关键差异**：Java 的动态代理要求被代理对象实现接口，这既是约束也是保障（类型安全、IDE 补全）。Python 的 `__getattr__` 可以代理任何对象，灵活性更高，但代理边界更模糊。Spring 用 CGLib 绕过接口限制直接代理类，代价是性能和复杂度略有增加。

---

## 动手练习

**16.1 Java — 图片懒加载代理**

实现一个 `ImageProxy` 类，代理 `Image` 接口（方法：`display()`、`getWidth()`、`getHeight()`）：
- 构造时只记录图片路径，不加载图片文件
- 第一次调用 `display()` 时才真正加载（用 `Thread.sleep(100)` 模拟耗时）
- 之后的 `display()` 直接使用已加载的图片

```java
interface Image {
    void display();
    int getWidth();
    int getHeight();
}
// 实现 ImageProxy，使得创建 10 个 ImageProxy 对象的开销可以忽略不计，
// 只有真正调用 display() 时才触发加载
```

**16.2 Java — 动态代理计时器**

使用 `java.lang.reflect.Proxy` 为任意接口创建一个计时代理：
- 记录每个方法的调用次数和总耗时
- 提供 `printStats()` 方法打印统计结果
- 让代理对同一接口的多个实例可复用

**16.3 Python — 缓存代理**

实现一个通用缓存代理 `CachingProxy`：
- 用 `__getattr__` 拦截所有方法调用
- 对可哈希参数的调用结果进行缓存（参数不可哈希时直接转发）
- 支持 `cache_info()` 方法查看命中率
- 支持 `cache_clear()` 方法清空缓存

**16.4 思考题 — Proxy vs Decorator vs Adapter**

三种模式的类图结构几乎相同，但意图截然不同：

- 有一个 `DatabaseConnection` 类，你想在每次 SQL 执行前后打印日志——你用哪种模式？为什么？
- 有一个第三方 `LegacyPrinter` 类，它的方法签名和你的 `Printable` 接口不兼容——你用哪种模式？
- 有一个 `RemoteFileSystem` 类，只有在首次访问文件时才真正建立网络连接——你用哪种模式？

---

## 回顾与连接

**三种"包装"模式的本质区分**:

- **Proxy vs Decorator (Ch09)**: 结构相同，意图不同。Proxy *控制访问*——它可以拒绝访问、延迟访问、缓存访问；Decorator *增强功能*——它总是把请求转发出去，只是在前后加料。实践经验法则：如果包装层可以决定"不调用"或"何时调用"真实对象，它是 Proxy；如果它总是调用并仅在前后添加行为，它是 Decorator。

- **Proxy vs Adapter (Ch04)**: Adapter *转换接口*（把 A 接口变成 B 接口）；Proxy *保持接口不变*（代理和真实对象实现同一接口）。如果包装前后接口发生了变化，你在用 Adapter。

- **Proxy vs Facade (Ch07)**: Facade 简化一组复杂子系统的接口（多变一）；Proxy 代理单个对象的访问（一对一）。

**设计要点**:

1. **接口是关键**: Proxy 必须和 RealSubject 实现同一接口，否则 Client 会感知到差异。Java 的 `java.lang.reflect.Proxy` 在编译期强制这一约束；Python 依赖鸭子类型，约束由开发者自律。

2. **动态代理的代价**: `java.lang.reflect.Proxy` 通过反射调用方法，性能低于直接调用约 10 倍。对高频路径（百万 QPS 级别）要考虑代价，但对普通业务代码（事务管理、权限检查）完全可以接受。

3. **`__getattr__` 的陷阱**: Python 的 `__getattr__` 只在属性未找到时触发，因此代理对象自身定义的属性（如 `_target`）不会被转发。写透明代理时必须用 `object.__setattr__` / `object.__getattribute__` 直接操作，否则容易陷入无限递归。

4. **测试时善用代理**: `unittest.mock.MagicMock` 是最强大的测试代理——它动态响应任意调用，让你在不启动真实依赖的情况下测试业务逻辑。`spec=` 参数确保 Mock 的接口与真实对象一致，避免测试代码和生产代码脱节。

5. **Spring 的教训**: Spring 中的 `@Transactional` 只对被 Spring 容器管理的 Bean 有效，且只对通过 Spring 代理调用的方法有效——类内部的 `this.method()` 调用绕过了代理，事务不会生效。这是动态代理透明性的边界，理解它能避免大量生产 bug。
