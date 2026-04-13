# Chapter 13: Command

> 类型: 行为 | 难度: ★★☆ | 原书: `src/ch22_Command/` | 前置: Ch02 (Strategy)

---

## 模式速览

**问题**: 你希望把"做某件事"这个动作本身存储下来——记录历史、支持撤销、放入队列延迟执行、或者将一系列操作打包成宏。如果直接调用方法，动作就在调用那一刻消失了，无法被传递、存储或回放。

Command 模式的解法是：**把请求（request）封装成对象**。这个对象携带了"要做什么"和"操作谁"的全部信息，可以像普通对象一样被传递、保存、序列化、撤销。

**结构速览**

```
Client
  │ 创建 ConcreteCommand，传入 Receiver
  │
  ▼
Invoker                    <<interface>>
┌─────────────────┐        Command
│ - history: Deque│───────►┌──────────────┐
│ + execute(cmd)  │        │ + execute()  │
│ + undo()        │        └──────┬───────┘
└─────────────────┘               │实现
                       ┌──────────┴──────────┐
                       ▼                     ▼
              ConcreteCommandA      MacroCommand
              ┌──────────────┐     ┌──────────────────┐
              │ - receiver   │     │ - cmds: Deque    │
              │ + execute()  │     │ + execute()      │
              └──────────────┘     │ + undo()         │
                    │ 调用         └──────────────────┘
                    ▼
                Receiver
              ┌──────────────┐
              │ + action()   │
              └──────────────┘
```

**五个角色**:
- `Command` — 接口，声明 `execute()` 方法（有时也声明 `undo()`）
- `ConcreteCommand` — 具体命令，持有 Receiver 引用，实现 `execute()` 时委托给 Receiver
- `Receiver` — 真正执行业务逻辑的对象（知道如何完成请求）
- `Invoker` — 命令的触发者，持有命令对象，负责调用 `execute()`，可维护历史队列
- `Client` — 组装 ConcreteCommand + Receiver，把命令交给 Invoker

**核心洞察**: Invoker 完全不知道 Receiver 是什么，只通过 `Command` 接口调用。这让 Invoker 与业务逻辑彻底解耦——一个 UI 按钮（Invoker）可以绑定"发邮件"或"打印文档"，无需任何修改。

---

## 本章新语言特性

| 特性 | Java | Python |
|------|------|--------|
| 函数式 Command | `@FunctionalInterface` + lambda | `__call__` 协议 / 普通函数 |
| 参数绑定 | 闭包捕获 / 构造器注入 | `functools.partial` |
| 异步队列执行 | `CompletableFuture.supplyAsync()` | `concurrent.futures.Executor.submit()` |
| 带状态的命令 | 实现类持有字段 | callable 类持有实例变量 |

### Java `@FunctionalInterface` — 让接口拥抱 lambda

只有一个抽象方法的接口称为**函数式接口（functional interface）**，`@FunctionalInterface` 注解让编译器强制检查这一点，同时允许直接用 lambda 赋值：

```java
// @FunctionalInterface 标记：只允许一个抽象方法
// 违反（添加第二个抽象方法）会导致编译错误
@FunctionalInterface
interface Command {
    void execute();
    // void undo();  // 添加这行会报错——lambda 无法表达两个方法
}

// 匿名内部类写法（Java 8 之前）
Command oldStyle = new Command() {
    @Override
    public void execute() {
        System.out.println("旧写法：冗长");
    }
};

// lambda 写法（Java 8+）——等价，但只需一行
Command lambda = () -> System.out.println("新写法：简洁");

// 方法引用——直接引用已有方法作为命令
Command methodRef = System.out::println;
```

当 Command 需要 `undo()` 时，lambda 不够用，仍需实现类——这是 `@FunctionalInterface` 的局限，也是"何时用类、何时用 lambda"的判断依据。

### Python `__call__` 协议 — 任何对象都可以是命令

Python 中任何实现了 `__call__` 方法的对象都可以像函数一样调用，称为 **callable**。这让带状态的命令对象与普通函数在调用方看来完全一致：

```python
# 普通函数——最简单的 Command
def greet():
    print("你好")

# callable 类——带状态的 Command
class Counter:
    def __init__(self):
        self.count = 0

    def __call__(self):
        # 每次调用都改变内部状态
        self.count += 1
        print(f"第 {self.count} 次调用")

counter = Counter()

# 两者的调用方式完全相同
greet()    # 你好
counter()  # 第 1 次调用
counter()  # 第 2 次调用

# callable() 内置函数可以检查对象是否可调用
print(callable(greet))    # True
print(callable(counter))  # True
print(callable(42))       # False
```

### Python `functools.partial` — 绑定参数，创造命令

`functools.partial` 将函数与部分参数"冻结"，返回一个新的 callable，相当于轻量级的 Command 工厂：

```python
from functools import partial

def send_message(channel: str, priority: int, text: str) -> None:
    print(f"[{priority}] -> {channel}: {text}")

# partial 绑定前两个参数，返回只需要 text 的新函数
alert = partial(send_message, "ops-channel", 1)
notify = partial(send_message, "general", 3)

# 调用时只需传剩余参数——就像调用无参命令
alert("服务器宕机！")   # [1] -> ops-channel: 服务器宕机！
notify("周报已发送")    # [3] -> general: 周报已发送
```

---

## Java 实战: Runnable + Callable + CompletableFuture

### 源码解析

原书（`src/ch22_Command/`）用绘图应用演示 Command：`DrawCommand` 记录每次在画布上的落点，`MacroCommand` 将所有 `DrawCommand` 串成队列，支持撤销（`undo()` 弹出最后一条记录）。

```java
// command/Command.java — 命令接口（原书骨架，用户自行实现）
package command;

public interface Command {
    void execute();  // 执行命令
}
```

```java
// command/MacroCommand.java — 宏命令：由多个子命令组成的复合命令
package command;

import java.util.ArrayDeque;
import java.util.Deque;

public class MacroCommand implements Command {
    // 用双端队列存储子命令历史，支持从尾部撤销
    private final Deque<Command> commands = new ArrayDeque<>();

    @Override
    public void execute() {
        // 依次执行所有子命令（重放整个历史）
        for (Command cmd : commands) {
            cmd.execute();
        }
    }

    // 追加一条子命令到队列末尾
    public void append(Command cmd) {
        // 防止将自身加入队列，避免无限递归
        if (cmd != this) {
            commands.addLast(cmd);
        }
    }

    // 撤销：移除最后一条子命令
    public void undo() {
        if (!commands.isEmpty()) {
            commands.removeLast();
        }
    }

    // 清空所有历史
    public void clear() {
        commands.clear();
    }
}
```

Java 标准库中 `Runnable` 就是 Command 模式的直接体现：

```java
// java.lang.Runnable 的源码（精简）
@FunctionalInterface
public interface Runnable {
    void run();  // execute() 的等价形式，无返回值，无 Receiver 引用
}

// java.util.concurrent.Callable<V> — 带返回值的 Command
@FunctionalInterface
public interface Callable<V> {
    V call() throws Exception;  // execute() 有返回值版本
}
```

`ExecutorService` 就是 Invoker：它接收 `Runnable`/`Callable` 命令，放入线程池队列异步执行：

```java
import java.util.concurrent.*;

// Invoker: ExecutorService 维护命令队列，按线程池调度执行
ExecutorService invoker = Executors.newFixedThreadPool(4);

// ConcreteCommand（lambda 形式）——捕获外部变量作为 Receiver
String url = "https://api.example.com/data";
Callable<String> fetchCmd = () -> {
    // 模拟 HTTP 请求——真正的 Receiver 逻辑在这里
    return "response from " + url;
};

// 提交命令：返回 Future，可以稍后获取结果
Future<String> future = invoker.submit(fetchCmd);
String result = future.get();  // 阻塞等待结果
```

### 现代重写：`CompletableFuture` 链式命令

```java
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

// 每个 supplyAsync/thenApply 都是一个 Command 节点
// CompletableFuture 链就是 MacroCommand 的异步版本
CompletableFuture
    // 第一个命令：异步获取数据（Runnable/Supplier as Command）
    .supplyAsync(() -> fetchFromDatabase("user:42"))
    // 第二个命令：转换数据（Function as Command）
    .thenApply(data -> transform(data))
    // 第三个命令：发送结果（Consumer as Command）
    .thenAccept(result -> sendToClient(result))
    // 错误处理：异常命令的 fallback
    .exceptionally(ex -> {
        log("命令链执行失败: " + ex.getMessage());
        return null;
    });
```

`javax.swing.Action` 是 Command 的完整体现——不仅有 `actionPerformed()`（execute），还有 `isEnabled()`（条件执行）和属性变化通知（状态同步）：

```java
import javax.swing.*;

// Action 继承自 ActionListener，是 Swing 中的完整 Command 对象
Action saveAction = new AbstractAction("保存") {
    {
        // 初始化块：设置命令的元数据（快捷键、图标等）
        putValue(ACCELERATOR_KEY,
            KeyStroke.getKeyStroke("ctrl S"));
        putValue(SHORT_DESCRIPTION, "保存当前文档");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // execute()：真正的业务逻辑
        document.save();
    }
};

// 同一个 Action 可以绑定到菜单项和工具栏按钮——Invoker 可以有多个
JMenuItem menuItem = new JMenuItem(saveAction);
JButton toolbarBtn = new JButton(saveAction);
// 调用 saveAction.setEnabled(false) 会同时禁用两者
```

---

## Python 实战: functools.partial + callable objects + concurrent.futures

### 源码解析

Python 函数是一等对象，本身就可以作为 Command 传递。只有在需要状态（历史记录、撤销）时，才需要 callable 类：

```python
from __future__ import annotations
from collections import deque
from typing import Protocol

# Command 协议——Python 用 Protocol 代替 Java 接口
class Command(Protocol):
    def __call__(self) -> None:
        ...  # Protocol 方法体用省略号占位

# Receiver：真正知道如何绘制的对象
class Canvas:
    def __init__(self):
        self._points: list[tuple[int, int]] = []

    def draw_point(self, x: int, y: int) -> None:
        self._points.append((x, y))
        print(f"绘制点: ({x}, {y})")

    def erase_last(self) -> None:
        if self._points:
            self._points.pop()
            print("撤销最后一个点")

# ConcreteCommand：callable 类，持有 Receiver 引用和参数
class DrawCommand:
    def __init__(self, canvas: Canvas, x: int, y: int):
        self._canvas = canvas  # Receiver
        self._x = x
        self._y = y

    def __call__(self) -> None:
        # execute()：委托给 Receiver 完成真正的工作
        self._canvas.draw_point(self._x, self._y)

    def undo(self) -> None:
        self._canvas.erase_last()

# Invoker：维护命令历史，支持 undo
class CommandHistory:
    def __init__(self):
        self._history: deque[DrawCommand] = deque()

    def execute(self, cmd: DrawCommand) -> None:
        cmd()  # __call__ 触发 execute
        self._history.append(cmd)

    def undo(self) -> None:
        if self._history:
            cmd = self._history.pop()
            cmd.undo()
```

`functools.partial` 在不需要 `undo` 时是更轻量的 Command 工厂：

```python
from functools import partial

canvas = Canvas()

# partial 绑定 Receiver（canvas）和参数，创建无参命令
cmd1 = partial(canvas.draw_point, 10, 20)
cmd2 = partial(canvas.draw_point, 30, 40)
cmd3 = partial(canvas.draw_point, 50, 60)

# Invoker：命令队列，按序执行
command_queue: list[Command] = [cmd1, cmd2, cmd3]
for cmd in command_queue:
    cmd()  # 统一调用接口，不关心内部实现
```

`concurrent.futures` 是 Python 中 ExecutorService 的等价物——Command 提交给 Executor，异步执行：

```python
from concurrent.futures import ThreadPoolExecutor, Future
from functools import partial

def fetch_data(url: str, timeout: int) -> str:
    # 模拟网络请求
    return f"data from {url}"

with ThreadPoolExecutor(max_workers=4) as executor:
    # partial 创建命令，submit 提交给 Invoker（线程池）
    cmd_a = partial(fetch_data, "https://api.a.com", 5)
    cmd_b = partial(fetch_data, "https://api.b.com", 10)

    # Invoker 异步调度，返回 Future（类似 Java 的 CompletableFuture）
    future_a: Future[str] = executor.submit(cmd_a)
    future_b: Future[str] = executor.submit(cmd_b)

    # 稍后获取结果
    print(future_a.result())
    print(future_b.result())
```

Django 管理命令是框架级别的 Command 模式——`BaseCommand` 就是 Invoker 定义的 Command 接口：

```python
# myapp/management/commands/send_report.py
from django.core.management.base import BaseCommand

class Command(BaseCommand):
    help = "发送每日统计报告"  # 命令的元数据描述

    def add_arguments(self, parser):
        # 声明命令参数——类似构造器注入 Receiver
        parser.add_argument("--email", type=str, required=True)

    def handle(self, *args, **options):
        # handle() 就是 execute()——框架（Invoker）调用它
        email = options["email"]
        report = generate_report()
        send_email(email, report)
        self.stdout.write(self.style.SUCCESS(f"报告已发送至 {email}"))

# 调用：python manage.py send_report --email=admin@example.com
# Django 框架作为 Invoker，解析命令行参数，然后调用 handle()
```

### Pythonic 重写：闭包作为轻量级命令

当命令不需要 `undo` 时，闭包比 callable 类更简洁：

```python
from collections import deque

def make_draw_command(canvas: Canvas, x: int, y: int):
    """工厂函数：返回一个闭包，捕获 canvas、x、y 作为绑定状态"""
    def execute():
        canvas.draw_point(x, y)
    return execute

# 使用：与 callable 类调用方式完全相同
history: deque = deque()
for x, y in [(10, 20), (30, 40), (50, 60)]:
    cmd = make_draw_command(canvas, x, y)
    cmd()
    history.append(cmd)
```

当需要 `undo` 时，callable 类比闭包更清晰——状态显式存储在实例变量中，而非隐藏在闭包的自由变量里：

```python
class TextCommand:
    """带撤销的文本编辑命令——状态显式，意图清晰"""

    def __init__(self, editor: "TextEditor", text: str):
        self._editor = editor
        self._text = text
        self._cursor_before: int = 0  # undo 需要记录执行前的状态

    def __call__(self) -> None:
        self._cursor_before = self._editor.cursor
        self._editor.insert(self._text)

    def undo(self) -> None:
        # 精确恢复到执行前的状态
        self._editor.delete(len(self._text))
        self._editor.cursor = self._cursor_before
```

---

## 两种哲学

| 维度 | Java | Python |
|------|------|--------|
| 基础 Command | `@FunctionalInterface` 接口 + 实现类 | 普通函数（函数即命令） |
| 轻量 Command | `@FunctionalInterface` + lambda | `functools.partial` / 闭包 |
| 有状态 Command | 实现类持有字段 + `undo()` 方法 | callable 类（`__call__` + `undo()`）|
| 异步队列 | `ExecutorService` / `CompletableFuture` | `concurrent.futures.Executor` |
| 框架集成 | `javax.swing.Action`（UI） | Django `BaseCommand`（CLI） |
| 何时需要 Command 类 | 需要 `undo` 或携带复杂元数据时 | 需要 `undo` 或显式状态时 |

**核心分歧**: Java 中函数不是一等对象，必须用接口+实现类来"包装"一个动作，Command 模式是**必要的基础设施**。Python 中函数本身已是一等对象，可直接传递——Command 模式只在需要附加状态（`undo` 历史、重试计数）时才值得引入完整的 callable 类。

**一句话判断**: 只需 `execute()`？用函数/lambda/partial。还需要 `undo()` 或状态？用 callable 类。

---

## 动手练习

### 13.1 Java: 带撤销的文本编辑器

实现一个简单的命令行文本编辑器，支持以下命令：
- `InsertCommand(editor, text)` — 在光标处插入文本，`undo()` 删除刚插入的内容
- `DeleteCommand(editor, length)` — 删除光标前 N 个字符，`undo()` 恢复被删除内容
- `MacroCommand` — 将多步操作合并为一步（如"替换"= 先删除后插入）

`Invoker` 维护 `Deque<Command>` 历史栈，`ctrl+Z` 触发 `undo()`。

```
期望行为:
  insert("Hello")  → "Hello"
  insert(", World") → "Hello, World"
  undo()           → "Hello"
  undo()           → ""
```

### 13.2 Python: 任务调度器

用 callable 类实现一个带优先级的任务调度器：

```python
class Task:
    def __init__(self, name: str, priority: int, fn: Callable):
        ...
    def __call__(self) -> None:
        ...  # 执行 fn，记录开始/结束时间

class Scheduler:
    def submit(self, task: Task) -> None: ...  # 按优先级排队
    def run_all(self) -> None: ...             # 从高优先级开始执行
    def cancel(self, name: str) -> bool: ...  # 从队列中移除（未执行的命令）
```

用 `partial` 从同一个函数创建三个不同优先级的 `Task`，提交后验证执行顺序。

### 13.3 概念辨析：Command vs Strategy

两个模式都用接口封装"行为"，但意图截然不同。填写下表：

| 场景 | 应选 Command 还是 Strategy？ | 理由 |
|------|--------------------------|------|
| 排序算法可在运行时切换 | | |
| 记录用户操作以支持重放 | | |
| 将操作放入队列延迟执行 | | |
| 同一接口有多种压缩算法 | | |

---

## 回顾与连接

**与其他模式的关系**:

| 模式 | 关系 |
|------|------|
| Strategy (Ch02) | Strategy 封装**算法**，运行时替换；Command 封装**请求**，支持队列/撤销。区别在于意图：算法交换 vs 请求对象化 |
| Memento (Ch20) | Command 的 `undo()` 往往需要 Memento 保存执行前的完整状态快照，两者经常协作 |
| Composite (Ch12) | `MacroCommand` 就是 Composite 模式在 Command 上的应用：树形结构的命令组合 |
| Observer (Ch08) | 事件系统常结合两者：Observer 监听事件，将事件包装为 Command 放入队列处理 |
| Chain of Responsibility (Ch15) | 都是解耦请求发送者与处理者；CoR 寻找第一个能处理的处理者，Command 明确绑定接收者 |

**什么时候选 Command**:
- 需要支持**撤销/重做**（undo/redo）操作
- 需要把操作**序列化**存储（日志、持久化命令历史）
- 需要把操作放入**队列**异步执行或延迟执行
- 需要把多步操作**组合成宏**（MacroCommand / Composite Command）
- UI 中同一操作绑定到多个触发点（菜单、快捷键、工具栏），Invoker 与业务逻辑解耦

**关键洞察**: Command 模式的本质是**时间解耦**——请求的创建时刻与执行时刻分离。这一点是 Strategy 做不到的：Strategy 替换"如何做"，而 Command 控制"何时做、做多少次、能否撤销"。现代异步编程（`Future`、`Promise`、`async/await`）的核心思想，正是 Command 模式在并发语境下的自然延伸。
