# Command 模式 — 真实应用

核心：**把"请求"封装成对象，支持撤销、队列、日志、延迟执行。**

---

## 1. Redux — Action 即 Command

Redux 的每个 Action 就是一个 Command 对象，
Store 是 Invoker，Reducer 是 Receiver。

```typescript
// Command 对象（Action）
const addToCart = (productId: string, qty: number) => ({
    type: 'CART/ADD_ITEM' as const,
    payload: { productId, qty },
});

const removeFromCart = (productId: string) => ({
    type: 'CART/REMOVE_ITEM' as const,
    payload: { productId },
});

// Invoker（Store）：接收 command，决定何时执行
dispatch(addToCart('sku-001', 2));

// Receiver（Reducer）：真正执行命令的逻辑
const cartReducer = (state = initialState, action: CartAction) => {
    switch (action.type) {
        case 'CART/ADD_ITEM':    return addItem(state, action.payload);
        case 'CART/REMOVE_ITEM': return removeItem(state, action.payload);
    }
};

// Redux DevTools 保存所有 Command 历史 → 时间旅行调试（Undo/Redo）
```

---

## 2. Java — `Runnable` / `CompletableFuture`

Java 的 `Runnable`、`Callable`、`Supplier` 都是 Command：
把操作封装成对象，交给 Executor 决定何时、在哪个线程执行。

```java
// Runnable 是最基础的 Command
Runnable sendEmail = () -> emailService.send(user, template);
Runnable updateCache = () -> cacheService.invalidate(userId);

// Executor（Invoker）决定执行时机
ExecutorService pool = Executors.newFixedThreadPool(4);
pool.submit(sendEmail);    // 异步执行
pool.submit(updateCache);

// CompletableFuture：延迟执行 + 链式命令
CompletableFuture
    .supplyAsync(() -> userRepo.findById(userId))     // Command 1
    .thenApply(user -> enrichWithProfile(user))       // Command 2
    .thenAccept(user -> cache.put(userId, user))      // Command 3
    .exceptionally(ex -> { log.error(ex); return null; });
```

---

## 3. SQL — 查询对象

SQL 查询本身就是 Command 模式：把操作描述为对象，由数据库引擎决定如何执行。

```typescript
// TypeORM / QueryBuilder：构建 Command 对象（SQL），延迟执行
const query = userRepo
    .createQueryBuilder('user')
    .leftJoinAndSelect('user.orders', 'order')
    .where('user.active = :active', { active: true })
    .andWhere('order.createdAt > :date', { date: lastMonth })
    .orderBy('user.createdAt', 'DESC')
    .take(20);

// Command 对象可以被：
const sql = query.getSql();          // 1. 序列化（调试/日志）
const explain = await query.explain(); // 2. 分析（不执行）
const users = await query.getMany(); // 3. 执行
```

---

## 4. Python — Celery 任务队列

Celery 把函数调用封装成 Command，序列化后放入队列，由 Worker 异步执行。

```python
from celery import Celery

app = Celery('tasks', broker='redis://localhost:6379')

# 定义 Command（Task）
@app.task(bind=True, max_retries=3)
def send_order_confirmation(self, order_id: str, user_email: str):
    try:
        order = Order.objects.get(id=order_id)
        email_service.send_confirmation(user_email, order)
    except Exception as exc:
        raise self.retry(exc=exc, countdown=60)  # 失败重试（Command 模式的优势）

# 发布 Command（不立即执行）
send_order_confirmation.delay(order.id, user.email)
# 或者延迟执行
send_order_confirmation.apply_async(
    args=[order.id, user.email],
    countdown=300,  # 5分钟后执行
    eta=datetime(2024, 12, 25),  # 指定时间执行
)
# Command 被序列化存入 Redis，Worker 进程取出后执行
```

---

## 5. Git — 每个操作是可撤销的命令

Git 的每次 `commit`、`merge`、`rebase` 都是 Command，支持撤销（`revert`/`reset`）。

```bash
# Command 对象（历史可查）
git log --oneline
# a3f8c21 feat: add checkout flow
# b7d4e10 fix: payment validation

# Undo（撤销最后一个 command，保留历史）
git revert HEAD

# 多步 Undo（回到 N 步之前的状态）
git reset --soft HEAD~3   # 撤销 3 个 commit，保留修改

# 重放 Command（cherry-pick 把某个 command 应用到当前分支）
git cherry-pick a3f8c21
```

---

## Command 模式带来的能力

| 能力 | 例子 |
|------|------|
| 异步执行 | `ExecutorService`、Celery |
| 延迟执行 | `apply_async(eta=...)`、`setTimeout` |
| 撤销/重做 | Editor undo、Git revert |
| 队列/优先级 | 任务队列、消息队列 |
| 日志/审计 | 记录所有操作历史 |
| 事务 | 把多个 Command 打包原子执行 |

---

## Python 生态

Python 的函数是一等公民，Command 天然就是函数对象；`queue.Queue` + `threading` 构建同步命令队列，`asyncio` 构建异步版本。

```python
import queue
import threading
import time
from dataclasses import dataclass, field
from typing import Callable, Any
from collections import deque

# 1. 函数即 Command（最 Pythonic）
class CommandQueue:
    """同步命令队列：生产者发布 Command，消费者线程执行"""
    def __init__(self):
        self._queue: queue.Queue[Callable] = queue.Queue()
        self._worker = threading.Thread(target=self._run, daemon=True)
        self._worker.start()

    def submit(self, command: Callable, *args, **kwargs) -> None:
        self._queue.put(lambda: command(*args, **kwargs))

    def _run(self) -> None:
        while True:
            cmd = self._queue.get()
            try:
                cmd()
            except Exception as e:
                print(f"Command failed: {e}")
            finally:
                self._queue.task_done()

    def join(self) -> None:
        self._queue.join()

def send_email(to: str, subject: str) -> None:
    print(f"Email → {to}: {subject}")

def resize_image(path: str, size: tuple) -> None:
    print(f"Resizing {path} to {size}")

executor = CommandQueue()
executor.submit(send_email, "alice@example.com", "Welcome!")
executor.submit(resize_image, "photo.jpg", (800, 600))
executor.submit(send_email, "bob@example.com", "Your order shipped")
executor.join()

# 2. 可撤销 Command（带 undo 的对象版）
@dataclass
class Command:
    execute: Callable[[], Any]
    undo: Callable[[], Any]
    description: str = ""

class CommandHistory:
    def __init__(self):
        self._done: deque[Command] = deque()
        self._undone: deque[Command] = deque()

    def execute(self, cmd: Command) -> Any:
        result = cmd.execute()
        self._done.append(cmd)
        self._undone.clear()    # 新操作清空 redo 历史
        return result

    def undo(self) -> bool:
        if not self._done:
            return False
        cmd = self._done.pop()
        cmd.undo()
        self._undone.append(cmd)
        return True

    def redo(self) -> bool:
        if not self._undone:
            return False
        cmd = self._undone.pop()
        cmd.execute()
        self._done.append(cmd)
        return True

# 使用
doc = {"content": "Hello"}
history = CommandHistory()

append_cmd = Command(
    execute=lambda: doc.update({"content": doc["content"] + " World"}),
    undo=lambda: doc.update({"content": doc["content"][:-6]}),
    description="Append ' World'",
)
history.execute(append_cmd)
print(doc["content"])   # Hello World
history.undo()
print(doc["content"])   # Hello
history.redo()
print(doc["content"])   # Hello World

# 3. asyncio — 异步 Command 队列
import asyncio

async def async_command_processor():
    q: asyncio.Queue[Callable] = asyncio.Queue()

    async def worker():
        while True:
            cmd = await q.get()
            await cmd()
            q.task_done()

    async def fetch_user(user_id: int):
        await asyncio.sleep(0.1)   # 模拟 I/O
        print(f"Fetched user {user_id}")

    # 启动 worker
    task = asyncio.create_task(worker())

    # 提交命令
    for i in range(3):
        await q.put(lambda uid=i: fetch_user(uid))

    await q.join()
    task.cancel()

asyncio.run(async_command_processor())
```

> **Python 洞察**：Python 的 `lambda` 和 `functools.partial` 是最轻量的 Command——
> 把函数调用封装成对象，不需要定义类。
> `queue.Queue` 是同步任务队列的标准实现，`asyncio.Queue` 是异步版本，
> 二者都天然支持"命令的发布与执行解耦"。

---

## 关键洞察

> Command 模式把"做什么"和"什么时候做、谁来做"分离。
> 一旦操作变成对象，就可以被存储、传输、排队、撤销——
> 这是消息队列、任务调度、事件溯源（Event Sourcing）架构的基础。
