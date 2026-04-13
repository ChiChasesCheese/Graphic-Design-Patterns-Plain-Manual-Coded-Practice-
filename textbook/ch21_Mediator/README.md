# Chapter 21: Mediator

> 类型: 行为 | 难度: ★★★ | 原书: `src/ch16_Mediator/` | 前置: Ch08 (Observer)

---

## 模式速览

**问题**: 当系统中有多个对象需要彼此协作时，如果让它们直接互相引用，就会产生"蜘蛛网"式的耦合——每个对象都持有其他多个对象的引用，任何一处改动都可能引发连锁反应。教科书中的登录对话框场景是教科书式的例子：Guest/Login 单选框、用户名文本框、密码文本框、OK 按钮、Cancel 按钮，它们的状态互相影响（勾选 Guest 时禁用用户名和密码框，用户名为空时禁用 OK 按钮……），如果让每个控件直接操作其他控件，耦合度会高到无法维护。

Mediator 的解法是：引入一个中介者，让所有 Colleague 只与中介者通信，由中介者集中处理协作逻辑。

```
      Colleague1 ──────┐
                        │
      Colleague2 ───────┤──→  Mediator
                        │    （集中协调）
      Colleague3 ──────┘
```

不引入 Mediator 时（蜘蛛网耦合）：

```
  Colleague1 ←──→ Colleague2
       ↑ ↘          ↗ ↑
       │   ↘      ↗   │
       ↓    ↘   ↗    ↓
  Colleague3 ←──→ Colleague4
```

**四个角色**:

- `Mediator` — 抽象中介者接口，定义接收 Colleague 通知的方法（如 `colleagueChanged()`）
- `ConcreteMediator` — 具体中介者，持有所有 Colleague 的引用，实现协调逻辑
- `Colleague` — 抽象同事接口，持有 Mediator 引用，事件发生时通知中介者
- `ConcreteColleague` — 具体同事（按钮、文本框等），自己只负责本职工作，复杂的联动逻辑委托给 Mediator

**与 Observer 的本质区别**:

| 维度 | Observer (Ch08) | Mediator (Ch21) |
|------|-----------------|-----------------|
| 通信拓扑 | 一对多（Subject → 多个 Observer） | 多对多（多个 Colleague ↔ 一个 Mediator） |
| 耦合方向 | Observer 不知道其他 Observer | Colleague 不知道其他 Colleague |
| 协调者 | 无集中协调，Subject 只负责广播 | Mediator 集中掌握所有协调逻辑 |
| 适用场景 | 事件广播、状态同步 | 多组件联动、工作流协调 |

---

## 本章新语言特性

| 特性 | Java | Python |
|------|------|--------|
| 现代并发中介者 | `ExecutorService`、`StructuredTaskScope`（Java 21 preview） | `asyncio.EventLoop` |
| 虚拟线程 | `Thread.ofVirtual()` (Java 21+) | `asyncio.create_task()` |
| 同步屏障 | `CountDownLatch`、`CyclicBarrier`、`Phaser` | `asyncio.Barrier`（Python 3.11+） |
| 密封类约束角色 | `sealed interface Colleague` | `@dataclass` + `Protocol` |
| 模式匹配分发 | `switch` 表达式 + 模式匹配 | `match` 语句（Python 3.10+） |

### Java 21+: `sealed interface` 约束 Colleague 角色

`sealed` 关键字让编译器知道 Colleague 的所有合法实现，结合 `switch` 模式匹配可以在 Mediator 的协调逻辑中做到穷举检查：

```java
// sealed 接口约束：Mediator 只会接收这三种 Colleague
sealed interface Colleague permits ButtonColleague, TextColleague, CheckColleague {
    void setMediator(Mediator mediator);
    void setEnabled(boolean enabled);
    String getId();
}

// Mediator 用 switch 模式匹配分发协调逻辑，编译器保证穷举
void handleChange(Colleague source) {
    switch (source) {
        case ButtonColleague btn   -> handleButtonChange(btn);
        case TextColleague   text  -> handleTextChange(text);
        case CheckColleague  check -> handleCheckChange(check);
        // 无需 default：sealed 保证穷举
    }
}
```

### Python: `asyncio.EventLoop` 作为协程中介者

Python 的异步事件循环本质上就是一个 Mediator：协程（Colleague）不直接互相调用，而是通过 `await` 将控制权交还给事件循环（Mediator），由事件循环统一调度。

```python
import asyncio

async def worker(name: str, queue: asyncio.Queue) -> None:
    """协程 Colleague：通过 Queue（中介者信道）通信，不直接互相引用"""
    while True:
        task = await queue.get()   # 把控制权交还给事件循环
        print(f"[{name}] 处理任务: {task}")
        await asyncio.sleep(0.1)   # 模拟耗时操作
        queue.task_done()

async def main() -> None:
    queue: asyncio.Queue[str] = asyncio.Queue()
    # 事件循环（asyncio.get_event_loop()）是真正的中介者
    # 它协调 producer 和多个 worker 的执行顺序
    workers = [asyncio.create_task(worker(f"W{i}", queue)) for i in range(3)]

    for i in range(9):
        await queue.put(f"任务-{i}")

    await queue.join()
    for w in workers:
        w.cancel()
```

---

## Java 实战: ExecutorService + CountDownLatch + CyclicBarrier

### `ExecutorService` — 线程调度的中介者

`ExecutorService` 是 Java 并发中最重要的 Mediator：生产者（提交任务的代码）和消费者（执行任务的线程）都不直接接触对方，所有协调工作由 ExecutorService 负责。

```java
import java.util.concurrent.*;
import java.util.List;

/**
 * ExecutorService 作为中介者：
 * 任务提交方不知道哪个线程执行，线程不知道谁提交了任务
 * 中介者负责线程分配、队列管理、拒绝策略
 */
public class ExecutorMediator {

    public static void main(String[] args) throws Exception {
        // 创建线程池中介者：核心4线程，最大8线程，队列容量100
        var executor = new ThreadPoolExecutor(
            4, 8,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            Thread.ofVirtual().factory(),      // Java 21+：虚拟线程
            new ThreadPoolExecutor.CallerRunsPolicy()  // 队列满时降级策略
        );

        // 任务提交方：不关心谁来执行
        var futures = new java.util.ArrayList<Future<String>>();
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            futures.add(executor.submit(() -> {
                // 执行方：不关心谁提交的任务
                Thread.sleep(50);
                return "任务 " + taskId + " 完成，线程: " + Thread.currentThread().getName();
            }));
        }

        // 收集结果
        for (var future : futures) {
            System.out.println(future.get());
        }

        executor.shutdown();
    }
}
```

### `CountDownLatch` — 同步屏障中介者

`CountDownLatch` 是一次性的同步中介者：多个线程向它报到（`countDown()`），等待方通过它阻塞（`await()`），由中介者统一放行。

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * 场景：并行初始化多个子系统，全部完成后才启动主服务
 * CountDownLatch 充当协调者——子系统之间无需互相引用
 */
public class StartupCoordinator {

    private static final int SUBSYSTEM_COUNT = 3;

    public static void main(String[] args) throws InterruptedException {
        // 中介者：等待 3 个子系统全部完成初始化
        var latch = new CountDownLatch(SUBSYSTEM_COUNT);
        var executor = Executors.newVirtualThreadPerTaskExecutor();

        // 各子系统独立初始化，完成后向中介者报到
        executor.submit(() -> initSubsystem("数据库连接池", 200, latch));
        executor.submit(() -> initSubsystem("缓存预热",   350, latch));
        executor.submit(() -> initSubsystem("消息队列",   150, latch));

        System.out.println("主线程：等待所有子系统初始化...");
        latch.await();   // 阻塞直到 count 归零
        System.out.println("主线程：所有子系统就绪，开始接受请求！");

        executor.shutdown();
    }

    private static void initSubsystem(String name, long delayMs, CountDownLatch latch) {
        try {
            Thread.sleep(delayMs);
            System.out.println("[" + name + "] 初始化完成");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            latch.countDown();   // 向中介者报到，无论成功或失败
        }
    }
}
```

### `CyclicBarrier` — 可重用的阶段同步中介者

`CyclicBarrier` 是可重用的同步中介者：每一轮等到所有参与者都到达屏障点，才统一放行，然后自动重置，适合多阶段并行计算。

```java
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;

/**
 * 场景：并行处理多阶段流水线（数据加载 → 处理 → 汇总），
 * 每个阶段结束时所有 worker 同步等待，由 CyclicBarrier 统一放行
 */
public class PipelineCoordinator {

    public static void main(String[] args) throws Exception {
        int workerCount = 4;
        // 中介者：所有 worker 到达后执行一次汇总动作，然后重置
        var barrier = new CyclicBarrier(workerCount,
            () -> System.out.println("=== 阶段完成，进入下一阶段 ===")
        );

        var executor = Executors.newFixedThreadPool(workerCount);
        for (int i = 0; i < workerCount; i++) {
            final int workerId = i;
            executor.submit(() -> {
                try {
                    // 第一阶段：数据加载
                    System.out.printf("Worker-%d: 加载数据%n", workerId);
                    Thread.sleep(100L * (workerId + 1));
                    barrier.await();   // 等所有 worker 完成加载

                    // 第二阶段：数据处理
                    System.out.printf("Worker-%d: 处理数据%n", workerId);
                    Thread.sleep(80L * (workerId + 1));
                    barrier.await();   // 等所有 worker 完成处理

                    // 第三阶段：结果写入
                    System.out.printf("Worker-%d: 写入结果%n", workerId);
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
    }
}
```

### Java 21 结构化并发 — 现代化的任务中介者

Java 21 引入的 `StructuredTaskScope`（preview）是结构化并发的中介者：它管理一组子任务的生命周期，确保父任务不会在子任务完成前退出，并集中处理取消和错误传播。

```java
import java.util.concurrent.StructuredTaskScope;

/**
 * StructuredTaskScope：结构化并发的中介者
 * 子任务的生命周期由 scope 统一管理，不会泄漏
 */
public class StructuredMediator {

    record UserProfile(String name) {}
    record UserOrders(int count) {}
    record PageData(UserProfile profile, UserOrders orders) {}

    static UserProfile fetchProfile(int userId) throws InterruptedException {
        Thread.sleep(100);   // 模拟远程调用
        return new UserProfile("用户_" + userId);
    }

    static UserOrders fetchOrders(int userId) throws InterruptedException {
        Thread.sleep(150);   // 模拟远程调用
        return new UserOrders(userId * 3);
    }

    // 并行获取用户画像和订单，任意一个失败则取消另一个
    static PageData fetchPageData(int userId) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            // scope 作为中介者，协调两个子任务的执行和取消
            var profileTask = scope.fork(() -> fetchProfile(userId));
            var ordersTask  = scope.fork(() -> fetchOrders(userId));

            scope.join().throwIfFailed();   // 等待所有子任务，失败则抛出

            return new PageData(profileTask.get(), ordersTask.get());
        }
        // try 块退出时，scope 确保所有子任务都已完成或被取消
    }

    public static void main(String[] args) throws Exception {
        var data = fetchPageData(42);
        System.out.println("画像: " + data.profile());
        System.out.println("订单: " + data.orders());
    }
}
```

---

## Python 实战: asyncio.EventLoop + Django signals

### `asyncio.EventLoop` — 协程的中介者

Python 的异步事件循环是协作式并发的核心 Mediator。所有协程都通过事件循环协调，互相之间不直接交互：

```python
import asyncio
from dataclasses import dataclass, field
from typing import Callable, Awaitable

@dataclass
class AsyncMediator:
    """
    基于 asyncio 的异步中介者：
    多个协程 Colleague 通过队列向中介者汇报，
    中介者根据消息类型路由和协调
    """
    _handlers: dict[str, list[Callable]] = field(default_factory=dict)
    _queue: asyncio.Queue = field(default_factory=asyncio.Queue)

    def on(self, event: str, handler: Callable[..., Awaitable]) -> None:
        """注册事件处理器（类似 Observer 的 subscribe）"""
        self._handlers.setdefault(event, []).append(handler)

    async def emit(self, event: str, **kwargs) -> None:
        """Colleague 通过此方法向中介者汇报事件"""
        await self._queue.put((event, kwargs))

    async def run(self) -> None:
        """事件循环：中介者统一处理所有事件"""
        while True:
            event, kwargs = await self._queue.get()
            handlers = self._handlers.get(event, [])
            # 并发执行所有订阅了该事件的处理器
            await asyncio.gather(*(h(**kwargs) for h in handlers))
            self._queue.task_done()


# 使用示例：订单处理流程的异步协调
async def main() -> None:
    mediator = AsyncMediator()

    # 各模块（Colleague）向中介者注册响应函数
    async def send_confirmation(order_id: int, email: str) -> None:
        await asyncio.sleep(0.05)   # 模拟发送邮件
        print(f"[邮件] 订单 {order_id} 确认邮件已发送至 {email}")

    async def update_inventory(order_id: int, items: list) -> None:
        await asyncio.sleep(0.03)   # 模拟库存更新
        print(f"[库存] 订单 {order_id} 库存已扣减: {items}")

    async def notify_warehouse(order_id: int, **_) -> None:
        await asyncio.sleep(0.02)   # 模拟仓库通知
        print(f"[仓库] 订单 {order_id} 已推送拣货任务")

    # 注册：order_placed 事件触发多个模块，但模块之间互不知晓
    mediator.on("order_placed", send_confirmation)
    mediator.on("order_placed", update_inventory)
    mediator.on("order_placed", notify_warehouse)

    # 启动中介者
    asyncio.create_task(mediator.run())

    # Colleague（下单模块）只需向中介者发送事件
    await mediator.emit("order_placed", order_id=1001,
                        email="alice@example.com", items=["书", "咖啡"])
    await mediator.emit("order_placed", order_id=1002,
                        email="bob@example.com",   items=["键盘"])

    await asyncio.sleep(0.2)   # 等待事件处理完成


asyncio.run(main())
```

### Django signals — Web 框架中的 Mediator

Django 的信号机制（`django.dispatch`）是 Mediator 模式在 Web 框架中的典型应用。发送方（Signal sender）和接收方（receiver）完全解耦，由 Signal 中介者负责路由：

```python
from django.db import models
from django.db.models.signals import post_save, pre_delete
from django.dispatch import receiver, Signal

# 自定义信号：订单支付完成
order_paid = Signal()   # 中介者信号对象


class Order(models.Model):
    user_email = models.EmailField()
    total = models.DecimalField(max_digits=10, decimal_places=2)
    status = models.CharField(max_length=20, default="pending")

    def mark_paid(self) -> None:
        self.status = "paid"
        self.save()
        # 发送方只需向中介者发送信号，不关心谁在监听
        order_paid.send(sender=self.__class__, order=self)


# 接收方 1：发送确认邮件（不知道还有其他接收方）
@receiver(order_paid)
def send_confirmation_email(sender, order: Order, **kwargs) -> None:
    print(f"[邮件服务] 向 {order.user_email} 发送支付确认")


# 接收方 2：更新库存（不知道还有其他接收方）
@receiver(order_paid)
def update_stock(sender, order: Order, **kwargs) -> None:
    print(f"[库存服务] 订单 {order.pk} 扣减库存")


# 接收方 3：记录审计日志（不知道还有其他接收方）
@receiver(order_paid)
def audit_log(sender, order: Order, **kwargs) -> None:
    print(f"[审计] 订单 {order.pk} 支付事件已记录")


# 使用内置信号：模型保存后自动触发
@receiver(post_save, sender=Order)
def on_order_saved(sender, instance: Order, created: bool, **kwargs) -> None:
    if created:
        print(f"[通知] 新订单 {instance.pk} 已创建")
```

### `multiprocessing.Manager` — 跨进程共享状态的中介者

`multiprocessing.Manager` 在多进程场景中充当共享状态的中介者：各进程不直接共享内存，而是通过 Manager 服务器进程协调访问共享对象。

```python
import multiprocessing
from multiprocessing import Manager
from typing import Any

def worker_process(worker_id: int, shared_dict: dict, shared_list: list,
                   lock: Any) -> None:
    """
    子进程 Colleague：通过 Manager 提供的代理对象访问共享数据，
    不直接与其他进程通信
    """
    with lock:
        # Manager 代理对象将操作序列化并发送给 Manager 服务器进程
        shared_dict[f"worker_{worker_id}"] = worker_id * 10
        shared_list.append(f"Worker-{worker_id} 完成")
        print(f"Worker-{worker_id}: 状态已更新")


def main() -> None:
    with Manager() as manager:
        # Manager 是中介者：创建跨进程可访问的共享对象
        shared_dict = manager.dict()    # 代理字典
        shared_list = manager.list()   # 代理列表
        lock = manager.Lock()          # 跨进程锁

        processes = [
            multiprocessing.Process(
                target=worker_process,
                args=(i, shared_dict, shared_list, lock)
            )
            for i in range(4)
        ]

        for p in processes:
            p.start()
        for p in processes:
            p.join()

        print("汇总结果:", dict(shared_dict))
        print("完成列表:", list(shared_list))


if __name__ == "__main__":
    main()
```

---

## 两种哲学

| 维度 | Java | Python |
|------|------|--------|
| 并发模型 | 多线程（抢占式），需要显式中介者协调 | 协程（协作式），事件循环天然是中介者 |
| 典型中介者 | `ExecutorService`、`CyclicBarrier`、`Phaser` | `asyncio.EventLoop`、`asyncio.Queue` |
| 中介者粒度 | 精细：针对不同协调需求有不同工具 | 统一：一个事件循环处理所有 I/O 和调度 |
| 错误传播 | `Future.get()` 抛出，需显式处理 | `await` 自然传播，`try/except` 捕获 |
| 结构化并发 | `StructuredTaskScope`（Java 21 preview） | `asyncio.TaskGroup`（Python 3.11+） |
| 框架层中介者 | Spring `ApplicationEventPublisher` | Django signals、FastAPI 依赖注入 |

**核心对比**:

```java
// Java：线程是独立调度单元，需要显式中介者协调它们的交汇点
var latch = new CountDownLatch(3);   // 中介者：等三个线程报到
// 各线程各自运行，完成时 countDown()，主线程 await()
```

```python
# Python：协程主动让出控制权，事件循环作为隐式中介者调度
async def task():
    await asyncio.sleep(1)   # 让出控制权给事件循环（中介者）
    # 事件循环决定何时恢复这个协程
```

**关键洞察**：Java 的多线程模型让线程自主运行，冲突时需要显式的同步原语（Latch、Barrier、Semaphore）作为中介者。Python 的协作式并发让事件循环成为天然的中介者——协程每次 `await` 都是在向中介者交权，中介者再决定下一步运行谁。两者都是 Mediator 模式，只是颗粒度和可见性不同。

---

## 动手练习

**21.1 Java — 聊天室 Mediator**

实现一个基于 Mediator 的文本聊天室：

```java
interface ChatMediator {
    void register(ChatUser user);
    void send(String message, ChatUser from);
}

interface ChatUser {
    String getName();
    void receive(String message, String fromName);
    void send(String message);
}
// 要求：
// - ConcreteMediator 持有所有 ChatUser 的列表
// - 用户发送消息时只调用 mediator.send()，不直接引用其他用户
// - 支持私聊：send("@Bob 你好", alice) 只发给 Bob
// - 支持广播：普通消息发给除发送者外的所有人
```

**21.2 Java — 工作流引擎**

实现一个简单的并行任务工作流，用 `CountDownLatch` 和 `CyclicBarrier` 组合实现两阶段执行：
- 第一阶段：3 个数据加载任务并行执行，全部完成才进入第二阶段
- 第二阶段：2 个数据处理任务并行执行，全部完成后打印汇总报告
- 任意任务失败时，整个工作流标记为失败并停止

**21.3 Python — asyncio 聊天室**

用 `asyncio.Queue` 实现异步聊天室中介者：

```python
import asyncio

class AsyncChatRoom:
    """asyncio 聊天室中介者：用 Queue 解耦发送方和接收方"""
    def __init__(self):
        self._queues: dict[str, asyncio.Queue] = {}
        # 实现 join、leave、broadcast、whisper 方法
        # 每个用户有独立的接收队列，中介者负责路由消息
```

**21.4 思考题 — Mediator vs Observer vs Facade**

三种模式都能减少组件间的直接耦合，但场景不同：

- 你在实现一个 GUI 对话框，多个控件的状态互相影响（选中 A 时禁用 B，填写 C 时才启用 D）——你用哪种模式？为什么不用 Observer？
- 你的用户注册模块完成后需要触发：发确认邮件、记录审计日志、发放新人优惠券——这三个动作独立，注册模块不关心谁来做。你用哪种模式？为什么不用 Mediator？
- 你想给一组复杂的子系统提供一个简单的统一入口——你用哪种模式？

---

## 回顾与连接

**三种"解耦"模式的本质区分**:

- **Mediator vs Observer (Ch08)**: Observer 是"广播模型"——Subject 变化时通知所有 Observer，Observer 之间没有协调逻辑，各自独立响应。Mediator 是"协调模型"——Colleague 变化时通知 Mediator，Mediator 根据所有 Colleague 的当前状态做出协调决策。当组件之间存在复杂的**相互依赖**（A 的状态决定 B 是否可用，B 的值决定 C 的范围），应该用 Mediator；当只是简单的**状态广播**（"我变了，有需要的自取"），用 Observer。

- **Mediator vs Facade (Ch07)**: Facade 是单向的——客户端调用 Facade，Facade 调用子系统，子系统不知道 Facade 的存在。Mediator 是双向的——Colleague 知道 Mediator 并主动向它汇报，Mediator 也会主动调用 Colleague。Facade 简化接口，Mediator 协调行为。

- **Mediator vs Chain of Responsibility (Ch15)**: Chain 是线性的——请求沿着链传递，每个节点决定是否处理；Mediator 是星形的——所有请求汇聚到中心节点，由中心统一决策如何分发。

**设计要点**:

1. **Mediator 可能变成上帝对象**: Mediator 集中了所有协调逻辑，随着 Colleague 增多，Mediator 本身可能变得过于复杂。对策：按职责拆分多个 Mediator，或在 Mediator 内部用策略模式分发协调逻辑。

2. **Colleague 的通知粒度**: 教科书中所有 Colleague 共用同一个 `colleagueChanged()` 通知，Mediator 需要通过参数或类型判断发生了什么。实践中可以设计多个通知方法（如 `onTextChanged()`、`onSelectionChanged()`），牺牲一点接口简洁性换取 Mediator 实现的清晰度。

3. **并发中介者的错误处理**: `CountDownLatch` 不支持取消——如果某个子任务抛出异常，其他任务仍然等待。生产代码应该结合 `CompletableFuture.allOf()` 或 Java 21 的 `StructuredTaskScope.ShutdownOnFailure()`，让任意失败触发整体取消。

4. **asyncio 的单线程约束**: Python 的事件循环是单线程的 Mediator。CPU 密集型任务（不能 `await`）会阻塞整个事件循环，需要用 `loop.run_in_executor()` 或 `asyncio.to_thread()` 卸载到线程池，让事件循环重新获得控制权。

5. **测试中介者**: 测试 Mediator 时，用 Mock 替换所有 Colleague，验证当某个 Colleague 通知 Mediator 时，Mediator 是否正确调用了其他 Colleague 的方法。这比测试直接耦合的组件简单得多——Mediator 是一个可以独立实例化和测试的对象。
