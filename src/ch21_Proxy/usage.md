# Proxy 模式 — 真实应用

核心：**用一个替代对象控制对真实对象的访问，可以在访问前后做额外操作。**

---

## 1. Spring AOP — `@Transactional` / `@Cacheable`

Spring 最核心的功能之一。Spring 自动为 Bean 创建代理，
在方法调用前后织入事务、缓存、安全等逻辑，你的业务代码完全无感。

```java
@Service
public class UserService {

    @Transactional   // Spring 为这个方法创建代理，自动管理事务
    public void transferMoney(String from, String to, BigDecimal amount) {
        accountRepo.debit(from, amount);
        accountRepo.credit(to, amount);
        // 如果抛异常，Spring 代理自动 rollback
        // 你写的代码里没有任何事务管理代码
    }

    @Cacheable(value = "users", key = "#id")  // Spring 代理：先查缓存
    public User findById(Long id) {
        return userRepo.findById(id).orElseThrow();
        // 实际调用：代理检查 Redis 缓存 → 命中直接返回 → 未命中才执行这里
    }
}

// Spring 内部（CGLib 动态代理，简化）：
// UserService$$SpringCGLIB$$0 extends UserService {
//     @Override
//     public void transferMoney(...) {
//         transactionManager.begin();
//         try {
//             super.transferMoney(...);  // 调用真实方法
//             transactionManager.commit();
//         } catch (Exception e) {
//             transactionManager.rollback();
//         }
//     }
// }
```

---

## 2. JavaScript — `Proxy` 对象

ES6 原生支持 Proxy，Vue 3 的响应式系统就基于它。

```typescript
// Vue 3 响应式系统核心（简化）
function reactive<T extends object>(target: T): T {
    return new Proxy(target, {
        get(obj, key) {
            track(obj, key);  // 代理：记录谁在读这个属性（收集依赖）
            return Reflect.get(obj, key);
        },
        set(obj, key, value) {
            Reflect.set(obj, key, value);
            trigger(obj, key);  // 代理：通知所有依赖此属性的组件重新渲染
            return true;
        }
    });
}

const state = reactive({ count: 0, name: 'Alice' });
state.count++;  // 触发 Proxy set → 通知 UI 更新（你感知不到代理的存在）
```

---

## 3. Hibernate — 懒加载代理

Hibernate 用 Proxy 实现懒加载，只有真正访问关联对象时才查数据库。

```java
@Entity
public class Order {
    @ManyToOne(fetch = FetchType.LAZY)  // 懒加载
    private User user;                  // 这里存的是 Proxy，不是真实 User
}

// 查询订单：SELECT * FROM orders WHERE id = ?
// user 字段是 Hibernate 生成的代理对象，还没查数据库
Order order = orderRepo.findById(orderId);

// 只有访问 user 属性时，代理才触发 SELECT * FROM users WHERE id = ?
String userName = order.getUser().getName();  // ← 此时代理才执行 DB 查询
// 如果你不访问 user，就节省了一次 DB 查询
```

---

## 4. Python — `__getattr__` 代理

Python 可以用 `__getattr__` 拦截属性访问，实现各种代理逻辑。

```python
class LazyLoader:
    """懒加载代理：只有真正访问时才初始化重型对象"""

    def __init__(self, factory):
        object.__setattr__(self, '_factory', factory)
        object.__setattr__(self, '_instance', None)

    def __getattr__(self, name):
        instance = object.__getattribute__(self, '_instance')
        if instance is None:
            factory = object.__getattribute__(self, '_factory')
            instance = factory()  # 第一次访问时才创建
            object.__setattr__(self, '_instance', instance)
        return getattr(instance, name)

# 使用：database_connection 只有被真正用到时才建立连接
db = LazyLoader(lambda: DatabaseConnection(config))
# ... 很多初始化代码 ...
result = db.query("SELECT 1")  # 此时才真正建立连接
```

---

## 5. nginx / CDN — 反向代理

基础设施层面的 Proxy，nginx 作为代理控制对后端服务的访问。

```nginx
# nginx 作为反向代理（Proxy 模式）
server {
    listen 80;

    location /api/ {
        # 代理：在转发前后做额外操作
        proxy_pass http://backend:8080;

        # 访问控制（Protection Proxy）
        limit_req zone=api_limit burst=20;

        # 缓存（Cache Proxy）
        proxy_cache api_cache;
        proxy_cache_valid 200 5m;

        # 日志（Logging Proxy）
        access_log /var/log/nginx/api.log combined;

        # 负载均衡（多个真实对象）
        # proxy_pass http://backend_pool;
    }
}
```

---

## Proxy 的几种变体

| 变体 | 目的 | 例子 |
|------|------|------|
| 远程代理 | 访问另一台机器的对象 | gRPC stub、RMI |
| 虚拟代理 | 懒加载，延迟创建 | Hibernate 懒加载 |
| 保护代理 | 访问控制 | Spring Security |
| 缓存代理 | 缓存结果 | `@Cacheable`、nginx cache |
| 日志代理 | 记录访问日志 | AOP logging |

---

## Python 生态

Python 提供多种内置 Proxy 机制：`functools.cached_property`、`__getattr__`、`wrapt` 库实现透明代理。

```python
import functools
import time
from typing import Any

# 1. functools.cached_property — 惰性计算代理（Python 3.8+）
class DataReport:
    def __init__(self, raw_data: list[dict]):
        self._raw = raw_data

    @functools.cached_property
    def summary(self) -> dict:
        """第一次访问时计算，之后缓存（Virtual Proxy）"""
        print("Computing summary (expensive)...")
        return {
            "count": len(self._raw),
            "total": sum(r.get("amount", 0) for r in self._raw),
            "avg": sum(r.get("amount", 0) for r in self._raw) / len(self._raw),
        }

report = DataReport([{"amount": 100}, {"amount": 200}, {"amount": 300}])
print(report.summary)   # 计算（打印 "Computing summary..."）
print(report.summary)   # 直接返回缓存，不再计算

# 2. __getattr__ 代理 — 透明委托
class LoggingProxy:
    """日志代理：记录所有方法调用（不修改原对象）"""
    def __init__(self, target: Any):
        object.__setattr__(self, "_target", target)

    def __getattr__(self, name: str):
        attr = getattr(object.__getattribute__(self, "_target"), name)
        if callable(attr):
            def logged(*args, **kwargs):
                print(f"[LOG] Calling {type(object.__getattribute__(self, '_target')).__name__}.{name}")
                start = time.perf_counter()
                result = attr(*args, **kwargs)
                elapsed = time.perf_counter() - start
                print(f"[LOG] {name} completed in {elapsed:.3f}s")
                return result
            return logged
        return attr

class DatabaseService:
    def query(self, sql: str) -> list:
        time.sleep(0.01)   # 模拟查询
        return [{"id": 1}, {"id": 2}]

    def insert(self, data: dict) -> bool:
        return True

db = LoggingProxy(DatabaseService())
db.query("SELECT * FROM users")   # 自动记录日志
db.insert({"name": "Alice"})

# 3. wrapt 库 — 生产级函数代理（保留函数签名和元数据）
# pip install wrapt
# import wrapt
#
# @wrapt.decorator
# def retry(wrapped, instance, args, kwargs):
#     """wrapt 保证代理后函数的签名、__name__、__doc__ 完全透明"""
#     for attempt in range(3):
#         try:
#             return wrapped(*args, **kwargs)
#         except Exception as e:
#             if attempt == 2:
#                 raise
#     
# @retry
# def fetch_user(user_id: int) -> dict:
#     """Fetch user from database."""
#     ...

# 4. 访问控制代理（Protection Proxy）
class SecureProxy:
    """保护代理：检查权限后才转发请求"""
    def __init__(self, service: Any, allowed_roles: set[str]):
        self._service = service
        self._allowed_roles = allowed_roles
        self._current_role: str | None = None

    def set_role(self, role: str) -> None:
        self._current_role = role

    def __getattr__(self, name: str):
        if self._current_role not in self._allowed_roles:
            raise PermissionError(
                f"Role '{self._current_role}' cannot access '{name}'"
            )
        return getattr(self._service, name)

class AdminPanel:
    def delete_user(self, user_id: int) -> None:
        print(f"Deleted user {user_id}")

    def list_users(self) -> list:
        return [{"id": 1}, {"id": 2}]

panel = SecureProxy(AdminPanel(), allowed_roles={"admin", "superuser"})
panel.set_role("guest")
try:
    panel.list_users()     # PermissionError
except PermissionError as e:
    print(e)

panel.set_role("admin")
panel.list_users()         # OK
```

> **Python 洞察**：`functools.cached_property` 是 Python 最常用的内置 Proxy——
> 它把一个方法变成"第一次访问时计算，之后缓存"的属性，典型的 Virtual Proxy。
> `__getattr__` 是构建透明代理的核心机制，比显式继承更灵活（可以包装任何对象）。

---

## 关键洞察

> Proxy 是 AOP（面向切面编程）的基础实现机制。
> Spring 的 `@Transactional`、`@Cacheable`、`@PreAuthorize` 背后都是 Proxy。
> 理解 Proxy，你就理解了"为什么加个注解就能自动管理事务"——
> **Spring 偷偷把你的对象换成了代理对象。**
