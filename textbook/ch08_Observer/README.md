# Chapter 08: Observer

> 类型: 行为 | 难度: ★★☆ | 原书: `src/ch17_Observer/` | 前置: Ch02 (Strategy)

---

## 模式速览

**Observer 解决什么问题？**

当一个对象的状态变化时，若干其他对象需要响应。朴素做法是让状态对象直接调用每个依赖者——这造成**硬编码耦合**，新增或移除一个依赖者就要改动状态对象。

Observer 模式将"谁在关注"与"发出通知"解耦：依赖者向状态对象**注册**自己，状态对象只负责广播，不关心有多少人在听。这是**一对多依赖**的标准解法。

又称：Publish-Subscribe（发布-订阅）、Event-Listener（事件监听）、Callback。

**结构速览**

```
  ┌─────────────────────────────┐
  │         Subject             │   维护观察者列表
  │  - observers: List          │
  │  + attach(Observer)         │   注册
  │  + detach(Observer)         │   注销
  │  + notify()                 │   广播
  └──────────────┬──────────────┘
                 │ notifies
                 ▼
  ┌──────────────────────────┐
  │    <<interface>>         │
  │       Observer           │
  │  + update(subject)       │   统一回调入口
  └────────────┬─────────────┘
               │ implements
    ┌──────────┴──────────┐
    ▼                     ▼
┌──────────────┐   ┌──────────────┐
│ DigitObserver│   │ GraphObserver│   具体观察者，各自决定如何响应
└──────────────┘   └──────────────┘
```

核心关系：Subject **聚合** Observer（has-a list），Observer 反向持有 Subject 引用以拉取数据（Pull 模型）或直接从通知中获取数据（Push 模型）。

---

## 本章新语言特性

### Java: `record` 类型（Java 16+）

Observer 模式中，事件/通知本身是纯数据——只需携带"发生了什么"，不需要可变状态。`record` 正是为此而生：**不可变的数据载体**，编译器自动生成构造器、`equals`、`hashCode`、`toString`。

```java
// 1. 基本 record —— 一行声明所有字段
record PropertyChangedEvent(String propertyName, Object oldValue, Object newValue) {}

// 使用：编译器生成规范构造器和访问器方法（无 get 前缀）
var event = new PropertyChangedEvent("price", 100, 120);
System.out.println(event.propertyName()); // "price"
System.out.println(event.oldValue());     // 100
System.out.println(event);               // PropertyChangedEvent[propertyName=price, ...]

// 2. compact constructor（紧凑构造器）—— 用于校验，无需显式赋值
record TemperatureEvent(double celsius) {
    TemperatureEvent {                           // 不写参数列表，编译器自动赋值
        if (celsius < -273.15)
            throw new IllegalArgumentException("低于绝对零度: " + celsius);
    }
}

// 3. record 天然 immutable：无 setter，字段 final
// record 也可实现接口，常用于让事件实现标记接口
sealed interface DomainEvent permits PriceChanged, StockDepleted {}
record PriceChanged(String sku, double delta) implements DomainEvent {}
record StockDepleted(String sku)              implements DomainEvent {}
```

`record` 与 Observer 的配合原则：**事件对象用 record，观察者接口用 interface，Subject 用普通类**。这样事件不可变（线程安全），观察者可以有状态（如累计统计）。

### Python: `@dataclass` 与 `@dataclass(frozen=True)`

Python 的 `dataclass` 是 Java `record` 的对应物——自动生成样板方法，减少手写代码。

```python
from dataclasses import dataclass, field

# frozen=True —— 不可变，适合事件/消息对象；自动生成 __hash__，可放入 set/dict
@dataclass(frozen=True)
class PropertyChangedEvent:
    property_name: str
    old_value: object
    new_value: object

event = PropertyChangedEvent("price", 100, 120)
# event.old_value = 99  # 错误：FrozenInstanceError（运行时保护）

# __post_init__ —— 等价于 Java 紧凑构造器，用于校验
@dataclass(frozen=True)
class TemperatureEvent:
    celsius: float

    def __post_init__(self):
        if self.celsius < -273.15:
            raise ValueError(f"低于绝对零度: {self.celsius}")
```

**选择依据**：事件/通知 → `frozen=True`（不可变，可哈希）；有状态的 Subject/Observer → 普通 `@dataclass`。

---

## Java 实战: `PropertyChangeSupport` + `java.util.concurrent.Flow`

### 源码解析

**经典方案：`java.beans.PropertyChangeSupport`**

JDK 自带的 Observer 实现，Swing 组件大量使用，Spring 的 `ApplicationEvent` 体系也沿用了类似设计。

```java
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

// Subject：委托给 PropertyChangeSupport，无需手写 attach/detach/notify
public class Stock {
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private double price;

    public void addListener(PropertyChangeListener l)    { pcs.addPropertyChangeListener(l); }
    public void removeListener(PropertyChangeListener l) { pcs.removePropertyChangeListener(l); }

    public void setPrice(double newPrice) {
        double old = this.price;
        this.price = newPrice;
        // 广播通知：自动跳过 old == new 的情况
        pcs.firePropertyChange("price", old, newPrice);
    }
}

// Observer：lambda 即可，无需专门写类
Stock apple = new Stock();
apple.addListener(e ->
    System.out.printf("价格变动 [%s]: %.2f → %.2f%n",
        e.getPropertyName(), e.getOldValue(), e.getNewValue())
);
apple.setPrice(182.5);  // 触发通知
```

**现代方案：`java.util.concurrent.Flow`（Java 9+，响应式流）**

`Flow` 定义了四个接口，是 Reactive Streams 标准在 JDK 中的官方实现：

```java
import java.util.concurrent.Flow.*;
import java.util.concurrent.SubmissionPublisher;

// Publisher<T>  —— Subject，产生数据
// Subscriber<T> —— Observer，消费数据
// Subscription  —— 控制背压（backpressure）的令牌
// Processor<T,R>—— 既是 Subscriber 又是 Publisher（中间变换层）

// SubmissionPublisher 是 JDK 提供的 Publisher 实现
SubmissionPublisher<Double> publisher = new SubmissionPublisher<>();

// 注册 Subscriber
publisher.subscribe(new Subscriber<>() {
    private Subscription subscription;

    @Override
    public void onSubscribe(Subscription s) {
        this.subscription = s;
        s.request(Long.MAX_VALUE);  // 请求无限数据（无背压）
    }

    @Override
    public void onNext(Double price) {
        System.out.println("收到价格: " + price);
        subscription.request(1);    // 每次消费后再请求一条（背压控制）
    }

    @Override public void onError(Throwable t)  { t.printStackTrace(); }
    @Override public void onComplete()           { System.out.println("流结束"); }
});

publisher.submit(182.5);
publisher.submit(183.0);
publisher.close();  // 触发 onComplete
```

### 现代重写：record 事件 + Flow

将 record、sealed interface、Flow 组合，实现类型安全的事件总线：

```java
import java.util.concurrent.SubmissionPublisher;

// 用 sealed record 约束事件类型：编译器保证穷举处理
sealed interface MarketEvent permits PriceChanged, TradingHalted {}
record PriceChanged(String ticker, double from, double to) implements MarketEvent {}
record TradingHalted(String ticker, String reason)        implements MarketEvent {}

// Publisher<MarketEvent> 自动广播给所有 Subscriber
var bus = new SubmissionPublisher<MarketEvent>();

bus.subscribe(new Subscriber<>() {
    Subscription sub;
    @Override public void onSubscribe(Subscription s) { sub = s; s.request(Long.MAX_VALUE); }

    @Override
    public void onNext(MarketEvent e) {
        // switch 模式匹配（Java 21+）穷举所有事件类型
        switch (e) {
            case PriceChanged(var t, var f, var to) ->
                System.out.printf("%s 价格: %.2f → %.2f%n", t, f, to);
            case TradingHalted(var t, var r) ->
                System.out.printf("%s 交易暂停: %s%n", t, r);
        }
        sub.request(1);
    }

    @Override public void onError(Throwable t)  {}
    @Override public void onComplete()           {}
});

bus.submit(new PriceChanged("AAPL", 182.5, 184.0));
bus.submit(new TradingHalted("GME", "波动过大"));
bus.close();
```

经典 Observer 与 Flow 的核心区别在于**背压（backpressure）**：Flow 的 `Subscription.request(n)` 让消费者控制速率，防止生产者过快导致内存溢出——这是经典 Observer 没有的能力。

---

## Python 实战: Django signals + blinker

### 源码解析

**Django signals：Web 框架内置的 Observer**

```python
from django.db.models.signals import post_save, pre_delete
from django.dispatch import receiver
from django.contrib.auth.models import User

# 方式一：装饰器注册（推荐）
@receiver(post_save, sender=User)
def on_user_saved(sender, instance: User, created: bool, **kwargs):
    if created:
        # 新用户注册后自动发欢迎邮件
        send_welcome_email(instance.email)

# 方式二：手动 connect（适合动态注册）
def audit_log(sender, instance, **kwargs):
    print(f"[删除前] {sender.__name__}: {instance.pk}")

pre_delete.connect(audit_log, sender=User)

# 发送自定义信号
from django.dispatch import Signal

# 声明自定义信号（相当于定义事件类型）
price_changed = Signal()  # 可通过 providing_args 文档化参数（3.1+ 废弃，改用 **kwargs）

# 发送
price_changed.send(sender=Stock, ticker="AAPL", old=182.5, new=184.0)

# 接收
@receiver(price_changed)
def on_price_changed(sender, ticker, old, new, **kwargs):
    print(f"{ticker}: {old} → {new}")
```

**blinker：轻量级信号库，不依赖任何框架**

```python
from blinker import signal

# 创建命名信号（全局单例，同名即同一信号）
price_changed = signal("price-changed")
order_placed   = signal("order-placed")

# 注册观察者
@price_changed.connect
def log_price(sender, **data):
    print(f"[日志] {sender}: {data}")

# 只监听特定 sender
@price_changed.connect_via("AAPL")
def alert_aapl(sender, **data):
    print(f"AAPL 警报: {data}")

# 发送信号
price_changed.send("AAPL", old=182.5, new=184.0)  # 触发两个观察者
price_changed.send("GOOG", old=150.0, new=148.0)  # 只触发 log_price
```

### Pythonic 重写：回调列表，无需接口

Python 不需要接口：函数本身就是对象，一个列表就能充当观察者容器。

```python
from dataclasses import dataclass, field
from typing import Callable

@dataclass(frozen=True)
class PriceEvent:
    ticker: str
    old_price: float
    new_price: float

    @property
    def delta(self) -> float:
        return self.new_price - self.old_price

@dataclass
class Stock:
    ticker: str
    _price: float = field(default=0.0, repr=False)
    _listeners: list[Callable[[PriceEvent], None]] = field(default_factory=list, repr=False)

    def subscribe(self, listener: Callable[[PriceEvent], None]) -> None:
        self._listeners.append(listener)

    @property
    def price(self) -> float:
        return self._price

    @price.setter
    def price(self, value: float) -> None:
        if value == self._price:
            return
        event = PriceEvent(self.ticker, self._price, value)
        self._price = value
        for listener in list(self._listeners):  # 复制列表防止监听器在回调中修改列表
            listener(event)

aapl = Stock("AAPL", 182.5)
aapl.subscribe(lambda e: print(f"[日志] {e.ticker}: {e.old_price:.2f} → {e.new_price:.2f}"))
aapl.subscribe(lambda e: print(f"[警报] 涨幅超 1%") if e.delta / e.old_price > 0.01 else None)
aapl.price = 185.0  # 同时触发两个观察者
```

---

## 两种哲学

| 维度 | Java | Python |
|---|---|---|
| 观察者定义 | 实现 `Observer`/`Listener` 接口 | 任意可调用对象（函数、lambda、方法） |
| 事件对象 | `record`（不可变，编译期类型安全） | `@dataclass(frozen=True)`（运行时保护） |
| 异步支持 | `Flow` API，内置背压控制 | `asyncio` + `gather`，协作式并发 |
| 类型检查 | 编译器拦截签名不匹配的 Listener | 鸭子类型，签名错误在运行时才暴露 |
| 框架集成 | Spring `ApplicationEvent`，Java EE CDI | Django signals，Flask-Blinker |
| 取消注册 | `removeListener` 需持有原始对象引用 | `list.remove`，同样需要引用；弱引用可用 `weakref` |

**Push vs Pull 模型**

- **Push**：Subject 将变化数据直接打包进通知（`update(event)`）。Observer 被动接收，无需再查询 Subject。缺点：Subject 必须预判 Observer 想要什么数据。
- **Pull**：通知只携带 Subject 引用（`update(subject)`）。Observer 主动调用 Subject 的 getter 获取所需数据。缺点：Observer 与 Subject 接口耦合更紧。

现代实践（如 Java `PropertyChangeEvent`、Django signals 的 `**kwargs`）倾向于**混合**：通知携带常用数据（Push），同时传入 sender 引用供需要时拉取（Pull）。

---

## 动手练习

### 08.1 Java: 类型安全事件系统

用 `sealed interface` + `record` 定义一组市场事件，实现 `MarketEventBus`，支持按事件类型注册 Listener（`bus.on(PriceChanged.class, handler)`），用 `switch` 模式匹配分发。至少实现：日志 Listener、告警 Listener（涨幅 > 3% 报警）。

### 08.2 Python: 带优先级的 pub/sub

扩展本章 `Stock`，为 `subscribe` 增加 `priority: int = 0`，观察者按优先级从高到低触发。额外挑战：支持观察者返回 `False` 以中断后续通知链（类似 DOM 的 `stopPropagation`）。

### 08.3 Push vs Pull 对比实验

用 Push 和 Pull 两种模型实现温度传感器：`HighTempAlert`（超 35°C 报警）更适合哪种？`TrendAnalyzer`（需要过去 10 次读数）更适合哪种？写下理由。

---

## 回顾与连接

**与前序模式的关系**

- **Strategy (Ch02)**：Strategy 替换 **一个** 算法；Observer 通知 **多个** 监听者。两者都基于接口解耦，但关注的多重性不同。

**与后续模式的关联**

- **Mediator (Ch21)**：当 Observer 数量很多、互相之间也有依赖时，广播会引发"蝴蝶效应"。Mediator 集中管理通信，避免观察者之间直接引用——可以把 Mediator 理解为 Subject 和 Observer 网格的调度层。
- **Command (Ch13)**：Command 将请求封装为对象，可以排队、撤销、重放；Observer 的通知是即发即忘（fire-and-forget）。若需要"可撤销的事件"，可将两者结合：Observer 接收到通知后创建 Command 对象入队，而不是立即执行。
- **Proxy (Ch16)**：远程代理（Remote Proxy）常用 Observer 接口对调用方隐藏网络通信：本地注册 Listener，事件透明地从远端推送过来。

**实践中的三个常见坑**

1. **内存泄漏**：Listener 忘记取消注册，Subject 长期持有对象引用导致无法 GC。Java 可用 `WeakReference`，Python 可用 `weakref.ref`。
2. **异常隔离**：一个 Listener 抛出异常时，应捕获并记录，继续通知后续 Listener，不能让一个出错的观察者阻断整个广播链。
3. **并发安全**：多线程环境下遍历和修改 Listener 列表需要同步；或复制列表后再遍历（本章 Python 示例已演示）。
