# Observer 模式 — 真实应用

核心：**对象状态变化时，自动通知所有依赖它的观察者，发布者不知道观察者是谁。**

---

## 1. RxJS — 响应式编程

RxJS 把 Observer 模式推到极致，是 Angular 的核心依赖，也广泛用于 React。

```typescript
import { fromEvent, interval } from 'rxjs';
import { debounceTime, switchMap, map, filter } from 'rxjs/operators';

// 搜索框：用户输入 → 防抖 → 发请求 → 更新结果
const searchInput = document.getElementById('search');

fromEvent(searchInput, 'input').pipe(    // 把 DOM 事件转成 Observable
    map(e => (e.target as HTMLInputElement).value),
    filter(query => query.length > 2),   // 至少 3 个字符
    debounceTime(300),                   // 停止输入 300ms 后才发请求
    switchMap(query =>                   // 取消上一个请求，发新请求
        fetch(`/api/search?q=${query}`).then(r => r.json())
    )
).subscribe(results => {                 // 观察者
    renderResults(results);
});
```

---

## 2. Vue.js — 响应式系统

Vue 3 的响应式系统基于 `Proxy`，是 Observer 模式的语言级实现：
数据变化自动通知依赖它的组件重新渲染。

```typescript
// Vue 3 Composition API
import { ref, watch, computed } from 'vue';

const count = ref(0);           // 被观察的数据（Subject）
const doubled = computed(() => count.value * 2);  // 观察者：自动更新

// 显式观察者
watch(count, (newVal, oldVal) => {
    console.log(`count changed: ${oldVal} → ${newVal}`);
    // 发送埋点、触发副作用等
});

// 模板里的渲染也是观察者，count 变化自动重渲染
// <template>{{ count }} {{ doubled }}</template>
count.value++;  // 通知所有观察者
```

---

## 3. Node.js — `EventEmitter`

Node.js 核心模块的基础，Stream、HTTP Server、WebSocket 都基于它。

```javascript
const { EventEmitter } = require('events');

class OrderService extends EventEmitter {
    async createOrder(data) {
        const order = await db.create(data);
        this.emit('created', order);   // 通知所有观察者
        return order;
    }

    async cancelOrder(id) {
        const order = await db.update(id, { status: 'cancelled' });
        this.emit('cancelled', order);
        return order;
    }
}

const orderService = new OrderService();

// 注册观察者（可以有多个）
orderService.on('created', (order) => emailService.sendConfirmation(order));
orderService.on('created', (order) => inventoryService.reserve(order.items));
orderService.on('cancelled', (order) => paymentService.refund(order.paymentId));
// OrderService 不知道也不关心谁在监听
```

---

## 4. Java — Spring 事件系统

Spring 内置事件发布/订阅机制，是 Observer 模式的企业级实现。

```java
// 自定义事件（被观察的变化）
public record UserRegisteredEvent(String userId, String email) implements ApplicationEvent {}

// 发布者（Subject）
@Service
public class UserService {
    @Autowired ApplicationEventPublisher eventPublisher;

    public User register(RegisterRequest req) {
        User user = userRepository.save(new User(req));
        eventPublisher.publishEvent(new UserRegisteredEvent(user.getId(), user.getEmail()));
        return user;
    }
}

// 观察者1：发欢迎邮件
@Component
public class WelcomeEmailListener {
    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        emailService.sendWelcome(event.email());
    }
}

// 观察者2：初始化用户资料（异步）
@Component
public class ProfileInitializer {
    @EventListener
    @Async
    public void onUserRegistered(UserRegisteredEvent event) {
        profileService.initDefault(event.userId());
    }
}
```

---

## 5. Kotlin — `StateFlow` / `SharedFlow`（Android)

Android 现代开发用 Kotlin Flow 替代 LiveData，是响应式 Observer 的最新实践。

```kotlin
// ViewModel（Subject）
class UserViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()  // 只读暴露

    fun loadUser(id: String) = viewModelScope.launch {
        _uiState.value = UiState.Loading
        try {
            val user = repository.getUser(id)
            _uiState.value = UiState.Success(user)
        } catch (e: Exception) {
            _uiState.value = UiState.Error(e.message)
        }
    }
}

// Fragment（观察者）
viewLifecycleOwner.lifecycleScope.launch {
    viewModel.uiState.collect { state ->   // 订阅
        when (state) {
            is UiState.Loading -> showSpinner()
            is UiState.Success -> showUser(state.user)
            is UiState.Error   -> showError(state.message)
        }
    }
}
```

---

## Python 生态

Python 用 `@property` setter 和 `__set_name__` 描述符实现属性级 Observer，`watchdog` 库监控文件系统变化。

```python
from __future__ import annotations
from typing import Callable, Any
from collections import defaultdict

# 1. @property setter — 属性级 Observer
class Observable:
    """混入类：让任何类的属性变化都能通知订阅者"""
    def __init__(self):
        self._observers: dict[str, list[Callable]] = defaultdict(list)

    def observe(self, attr: str, callback: Callable[[Any, Any], None]) -> None:
        self._observers[attr].append(callback)

    def _notify(self, attr: str, old_val: Any, new_val: Any) -> None:
        for cb in self._observers[attr]:
            cb(old_val, new_val)

class User(Observable):
    def __init__(self, name: str, email: str):
        super().__init__()
        self._name = name
        self._email = email

    @property
    def email(self) -> str:
        return self._email

    @email.setter
    def email(self, value: str) -> None:
        old = self._email
        self._email = value
        self._notify("email", old, value)   # 通知观察者

user = User("Alice", "alice@old.com")
user.observe("email", lambda old, new: print(f"Email changed: {old} → {new}"))
user.email = "alice@new.com"   # 触发通知

# 2. 描述符（Descriptor）— 可复用的 Observable 属性
class ObservableAttr:
    """可复用的 Observable 描述符"""
    def __set_name__(self, owner, name):
        self._name = name
        self._storage = f"_{name}_value"
        self._listeners = f"_{name}_listeners"

    def __get__(self, obj, objtype=None):
        if obj is None:
            return self
        return getattr(obj, self._storage, None)

    def __set__(self, obj, value):
        old = getattr(obj, self._storage, None)
        setattr(obj, self._storage, value)
        for listener in getattr(obj, self._listeners, []):
            listener(old, value)

    def subscribe(self, obj, callback: Callable) -> None:
        if not hasattr(obj, self._listeners):
            setattr(obj, self._listeners, [])
        getattr(obj, self._listeners).append(callback)

class Product:
    price = ObservableAttr()
    stock = ObservableAttr()

    def __init__(self, name: str, price: float, stock: int):
        self.name = name
        self.price = price
        self.stock = stock

p = Product("Laptop", 999.0, 10)
Product.price.subscribe(p, lambda old, new: print(f"Price: {old} → {new}"))
Product.stock.subscribe(p, lambda old, new: print(f"Stock: {old} → {new}"))

p.price = 899.0    # Price: 999.0 → 899.0
p.stock = 8        # Stock: 10 → 8

# 3. watchdog — 文件系统 Observer
# pip install watchdog
# from watchdog.observers import Observer
# from watchdog.events import FileSystemEventHandler, FileModifiedEvent
#
# class ConfigReloader(FileSystemEventHandler):
#     def on_modified(self, event: FileModifiedEvent):
#         if event.src_path.endswith("config.yaml"):
#             print(f"Config changed: {event.src_path}, reloading...")
#             reload_config()
#
# observer = Observer()
# observer.schedule(ConfigReloader(), path="./config", recursive=False)
# observer.start()

# 4. Django signals — 模型级 Observer
# from django.db.models.signals import post_save
# from django.dispatch import receiver
# from myapp.models import User
#
# @receiver(post_save, sender=User)
# def on_user_created(sender, instance, created, **kwargs):
#     if created:
#         send_welcome_email(instance.email)
#         create_user_profile(instance)
```

> **Python 洞察**：`@property` setter 是 Python 中实现响应式属性的标准方式——
> 修改属性时自动触发回调，和 Vue 的 `reactive()` 原理类似。
> Django 的 `signals` 是模型层的 Observer，`post_save`/`pre_delete` 等信号解耦了模型和副作用逻辑。

---

## 关键洞察

> Observer 是所有"响应式"框架的基础——Vue 响应式、RxJS、Redux、Kafka 都是它。
> 核心价值：**解耦"状态变化"和"变化后做什么"**，
> 新增副作用只需加一个观察者，不需要修改发布者。
> 现代叫法：事件驱动、响应式编程、pub/sub——本质都是 Observer。
