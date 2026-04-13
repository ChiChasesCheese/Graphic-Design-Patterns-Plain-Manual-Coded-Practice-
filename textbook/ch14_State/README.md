# Chapter 14: State

> 类型: 行为 | 难度: ★★☆ | 原书: `src/ch19_State/` | 前置: Ch02 (Strategy)

---

## 模式速览

**State 解决什么问题？**

当一个对象的行为随着内部状态的变化而显著改变时，朴素做法是在每个方法里写 `if/else` 或 `switch` 判断当前状态：

```java
// 反面教材：状态逻辑散落在每个方法中
void onButtonClick() {
    if (state == IDLE) {
        startProcessing();
    } else if (state == RUNNING) {
        pauseProcessing();
    } else if (state == PAUSED) {
        resumeProcessing();
    }
}

void onTimeout() {
    if (state == RUNNING) {
        handleRunningTimeout();
    } else if (state == PAUSED) {
        handlePausedTimeout();
    }
    // IDLE 时什么也不做
}
```

随着状态数量增加，这些条件分支会在每个方法里同步膨胀，极难维护。State 模式将每种状态封装成独立对象，把"当前状态下该做什么"的逻辑下沉到各个状态类中——Context 只需委托给当前状态对象即可。

**GoF 定义**：允许对象在其内部状态发生改变时改变自己的行为——对象看起来好像修改了它的类。

**结构速览**

```
         ┌──────────────────┐   委托    ┌─────────────────────┐
         │    Context       │ ────────► │  <<interface>>      │
         │                  │           │       State         │
         │ - state: State   │           │ + handle(ctx)       │
         │ + request()      │           └──────────┬──────────┘
         │ + setState(s)    │                       │
         └──────────────────┘           ┌───────────┴───────────┐
                                        ▼                       ▼
                                ┌───────────────┐     ┌────────────────┐
                                │ConcreteStateA │     │ConcreteStateB  │
                                │ + handle(ctx) │     │ + handle(ctx)  │
                                └───────────────┘     └────────────────┘
```

核心关系：Context **委托**行为给当前 State 对象；State 对象可以通过 `ctx.setState(...)` 触发状态转换。

**Strategy vs State：一字之差，意图迥异**

| 维度 | Strategy | State |
|------|----------|-------|
| 谁决定切换 | **客户端**主动注入不同策略 | **状态对象自身**（或 Context）在内部触发转换 |
| 状态之间 | 策略互不知晓 | 状态之间往往相互引用（知道下一个状态） |
| 典型场景 | 排序算法、压缩算法选择 | 订单状态机、TCP 连接、UI 组件生命周期 |
| 运行期间切换频率 | 通常切换一次（初始化时注入） | 频繁自动转换 |

---

## 本章新语言特性

### Java: `enum` 带抽象方法

Java 的 `enum` 不只是常量列表——每个枚举常量可以覆盖抽象方法，天然实现了"有限状态 + 各状态行为"的封装：

```java
// 每个枚举常量都是一个匿名子类，覆盖抽象方法
enum TrafficLight {
    RED {
        @Override
        public TrafficLight next() { return GREEN; }  // 红灯下一个是绿灯
    },
    GREEN {
        @Override
        public TrafficLight next() { return YELLOW; } // 绿灯下一个是黄灯
    },
    YELLOW {
        @Override
        public TrafficLight next() { return RED; }    // 黄灯下一个是红灯
    };

    // 抽象方法：每个常量必须提供实现，否则编译报错
    public abstract TrafficLight next();
}

// 使用：状态转换完全内聚在 enum 内部
TrafficLight light = TrafficLight.RED;
light = light.next();  // GREEN
light = light.next();  // YELLOW
```

**关键性质**：枚举常量是单例（JVM 保证），因此这种写法同时获得了 Singleton 的安全性——无论调用多少次，`TrafficLight.RED` 始终是同一个对象。

### Java: `switch` 表达式（Java 14+）

`switch` 表达式配合密封接口（`sealed interface`）提供编译期穷举检查：

```java
// switch 表达式：箭头语法，自动推导返回值，无需 break
String display = switch (light) {
    case RED    -> "停止";
    case GREEN  -> "通行";
    case YELLOW -> "准备";
    // 若漏写某个 case，编译器报错——状态机穷举检查
};
```

### Python: `enum.Enum` 带方法

```python
from enum import Enum, auto

class TrafficLight(Enum):
    RED    = auto()
    GREEN  = auto()
    YELLOW = auto()

    def next(self) -> "TrafficLight":
        # 用字典映射替代 if/else
        _transitions = {
            TrafficLight.RED:    TrafficLight.GREEN,
            TrafficLight.GREEN:  TrafficLight.YELLOW,
            TrafficLight.YELLOW: TrafficLight.RED,
        }
        return _transitions[self]

    def display(self) -> str:
        return {"RED": "停止", "GREEN": "通行", "YELLOW": "准备"}[self.name]
```

### Python: `match`/`case` 状态分派（Python 3.10+）

```python
def handle_event(state: TrafficLight, event: str) -> str:
    match state:
        case TrafficLight.RED:
            return "红灯：禁止通行"
        case TrafficLight.GREEN if event == "pedestrian":
            return "绑定守卫条件：行人请等待"
        case TrafficLight.GREEN:
            return "绿灯：车辆通行"
        case TrafficLight.YELLOW:
            return "黄灯：减速慢行"
        # 无 default：Python 不强制穷举，但可加 case _ 兜底
```

---

## Java 实战: `Thread.State` + `enum` 状态机

### 源码解析

`java.lang.Thread.State` 是 JDK 内置的 State 模式典范，定义了线程生命周期的全部状态：

```java
// JDK 源码（java.lang.Thread）
public enum State {
    NEW,            // 线程已创建，尚未调用 start()
    RUNNABLE,       // 正在 JVM 中运行（或等待操作系统调度）
    BLOCKED,        // 等待 monitor 锁（synchronized 块）
    WAITING,        // 无限期等待另一个线程（Object.wait(), join()）
    TIMED_WAITING,  // 有限期等待（Thread.sleep(n), wait(n)）
    TERMINATED;     // 执行完毕
}
```

状态转换由 JVM 内部驱动，调用者不能直接设置 `state` 字段——这正是 State 模式"状态转换内聚"的体现。

用 `enum` 抽象方法实现一个完整的**订单状态机**，展示类 JDK 风格：

```java
package ch14_State;

/**
 * 订单状态机——用 enum 抽象方法实现 State 模式
 * 每个枚举常量代表一种状态，并封装该状态下允许的操作
 */
enum OrderState {

    PENDING("待支付") {
        @Override
        public OrderState pay() {
            System.out.println("支付成功，订单进入待发货状态");
            return PAID;                         // 转换到下一个状态
        }

        @Override
        public OrderState ship() {
            throw new IllegalStateException("未支付的订单不能发货");
        }

        @Override
        public OrderState cancel() {
            System.out.println("订单已取消");
            return CANCELLED;
        }
    },

    PAID("已支付") {
        @Override
        public OrderState pay() {
            throw new IllegalStateException("订单已支付，无需重复支付");
        }

        @Override
        public OrderState ship() {
            System.out.println("商品已发出，请等待收货");
            return SHIPPED;
        }

        @Override
        public OrderState cancel() {
            System.out.println("订单已取消，退款将在 3 个工作日内到账");
            return CANCELLED;
        }
    },

    SHIPPED("已发货") {
        @Override
        public OrderState pay() {
            throw new IllegalStateException("已发货订单无法再支付");
        }

        @Override
        public OrderState ship() {
            throw new IllegalStateException("订单已在配送中");
        }

        @Override
        public OrderState cancel() {
            throw new IllegalStateException("已发货订单无法取消，请收货后申请退货");
        }
    },

    CANCELLED("已取消") {
        @Override
        public OrderState pay()    { throw new IllegalStateException("已取消订单"); }

        @Override
        public OrderState ship()   { throw new IllegalStateException("已取消订单"); }

        @Override
        public OrderState cancel() { throw new IllegalStateException("订单已处于取消状态"); }
    };

    private final String label;

    OrderState(String label) { this.label = label; }

    // 抽象方法：强制每个状态实现所有事件处理，返回下一个状态
    public abstract OrderState pay();
    public abstract OrderState ship();
    public abstract OrderState cancel();

    public String label() { return label; }
}

/** Context：持有当前状态，对外暴露业务操作 */
class Order {
    private OrderState state = OrderState.PENDING; // 初始状态：待支付
    private final String id;

    Order(String id) { this.id = id; }

    public void pay()    { state = state.pay();    printState(); }
    public void ship()   { state = state.ship();   printState(); }
    public void cancel() { state = state.cancel(); printState(); }

    private void printState() {
        System.out.println("订单 [" + id + "] 当前状态: " + state.label());
    }
}
```

### 现代重写：`sealed interface` + `switch` 表达式

当状态需要携带数据（如退款金额、快递单号），`enum` 就力不从心了——此时用 `sealed interface` 建模更合适：

```java
package ch14_State;

import java.util.Optional;

/**
 * 用 sealed interface 表达状态——每种状态可以携带不同的字段
 * Java 17+ 的密封接口保证子类集合有限，配合 switch 实现穷举检查
 */
sealed interface PaymentState permits
        PaymentState.Pending,
        PaymentState.Processing,
        PaymentState.Succeeded,
        PaymentState.Failed {

    // 每种状态携带自己的数据（record 天然不可变）
    record Pending()                              implements PaymentState {}
    record Processing(String transactionId)       implements PaymentState {}
    record Succeeded(String transactionId,
                     long   amount)               implements PaymentState {}
    record Failed(String reason,
                  int    retryCount)              implements PaymentState {}
}

class PaymentContext {
    private PaymentState state = new PaymentState.Pending();

    /** switch 表达式：编译器强制穷举所有 sealed 子类型 */
    public String describe() {
        return switch (state) {
            case PaymentState.Pending()         -> "等待用户确认支付";
            case PaymentState.Processing(var id) -> "处理中，流水号: " + id;
            case PaymentState.Succeeded(var id, var amt) ->
                    "支付成功，金额: " + amt + "，流水号: " + id;
            case PaymentState.Failed(var reason, var retry) ->
                    "支付失败：" + reason + "（已重试 " + retry + " 次）";
        };
        // 漏写任意一个 case → 编译错误，状态机穷举得到语言级保障
    }

    public void submit(String txId) {
        state = switch (state) {
            case PaymentState.Pending()  -> new PaymentState.Processing(txId);
            default -> throw new IllegalStateException("当前状态无法提交: " + state);
        };
    }
}
```

`enum` vs `sealed interface` 的选择：
- 状态**无数据**，只有行为差异 → 用 `enum` + 抽象方法，简洁
- 状态**携带不同数据**，需要模式匹配解构 → 用 `sealed interface` + `record`

---

## Python 实战: `enum.Enum` + `transitions` 库

### 源码解析

Python 的 `enum.Enum` 同样可以封装状态行为，但惯用法更倾向于把转换逻辑集中在 Context 或辅助函数里：

```python
from enum import Enum, auto
from typing import Optional

class ConnectionState(Enum):
    """模拟 TCP 连接的状态机"""
    CLOSED      = auto()
    LISTEN      = auto()
    SYN_SENT    = auto()
    ESTABLISHED = auto()
    FIN_WAIT    = auto()
    TIME_WAIT   = auto()

    def is_terminal(self) -> bool:
        """终止状态：连接已彻底关闭"""
        return self == ConnectionState.CLOSED

class TcpConnection:
    """Context：封装当前状态，驱动状态转换"""

    # 合法转换表：{当前状态: {事件: 下一状态}}
    _transitions: dict[ConnectionState, dict[str, ConnectionState]] = {
        ConnectionState.CLOSED:      {"listen": ConnectionState.LISTEN,
                                      "connect": ConnectionState.SYN_SENT},
        ConnectionState.LISTEN:      {"syn_received": ConnectionState.ESTABLISHED},
        ConnectionState.SYN_SENT:    {"ack_received": ConnectionState.ESTABLISHED},
        ConnectionState.ESTABLISHED: {"close": ConnectionState.FIN_WAIT},
        ConnectionState.FIN_WAIT:    {"ack_received": ConnectionState.TIME_WAIT},
        ConnectionState.TIME_WAIT:   {"timeout": ConnectionState.CLOSED},
    }

    def __init__(self) -> None:
        self._state = ConnectionState.CLOSED

    @property
    def state(self) -> ConnectionState:
        return self._state

    def trigger(self, event: str) -> None:
        """触发事件，驱动状态转换"""
        allowed = self._transitions.get(self._state, {})
        if event not in allowed:
            raise ValueError(
                f"当前状态 {self._state.name} 不允许事件 '{event}'"
            )
        next_state = allowed[event]
        print(f"  {self._state.name} --[{event}]--> {next_state.name}")
        self._state = next_state

# 使用
conn = TcpConnection()
conn.trigger("connect")       # CLOSED → SYN_SENT
conn.trigger("ack_received")  # SYN_SENT → ESTABLISHED
conn.trigger("close")         # ESTABLISHED → FIN_WAIT
```

**`transitions` 库**——声明式状态机，生产项目常用：

```python
# pip install transitions
from transitions import Machine

class TrafficLightMachine:
    """用 transitions 库定义红绿灯状态机"""

    # 声明所有状态
    states = ["red", "green", "yellow"]

    # 声明所有转换：trigger 是触发方法名，会自动生成到实例上
    transitions_config = [
        {"trigger": "go",    "source": "red",    "dest": "green"},
        {"trigger": "slow",  "source": "green",  "dest": "yellow"},
        {"trigger": "stop",  "source": "yellow", "dest": "red"},
    ]

    def __init__(self) -> None:
        self.machine = Machine(
            model=self,
            states=self.states,
            transitions=self.transitions_config,
            initial="red",
        )

    # 回调钩子：进入某状态时自动调用（命名规则: on_enter_<state>）
    def on_enter_green(self) -> None:
        print("绿灯亮起，车辆可以通行")

    def on_enter_red(self) -> None:
        print("红灯亮起，车辆停止")

light = TrafficLightMachine()
print(light.state)    # red
light.go()            # 触发转换 red → green，自动调用 on_enter_green
light.slow()          # green → yellow
light.stop()          # yellow → red，自动调用 on_enter_red
```

**Django 中的状态字段模式**——最简化的 State 应用：

```python
from django.db import models

class Order(models.Model):
    class Status(models.TextChoices):
        PENDING   = "pending",   "待支付"
        PAID      = "paid",      "已支付"
        SHIPPED   = "shipped",   "已发货"
        CANCELLED = "cancelled", "已取消"

    status = models.CharField(
        max_length=20,
        choices=Status.choices,
        default=Status.PENDING,
    )

    def pay(self) -> None:
        if self.status != self.Status.PENDING:
            raise ValueError(f"当前状态 {self.status} 不允许支付")
        self.status = self.Status.PAID
        self.save()

    def ship(self) -> None:
        if self.status != self.Status.PAID:
            raise ValueError(f"当前状态 {self.status} 不允许发货")
        self.status = self.Status.SHIPPED
        self.save()
```

### Pythonic 重写：`match`/`case` 状态机 vs 类层次 State

**`match`/`case` 版**——适合状态数量少、逻辑简单的场景：

```python
from dataclasses import dataclass, field
from enum import Enum, auto

class Phase(Enum):
    IDLE     = auto()
    RUNNING  = auto()
    PAUSED   = auto()
    STOPPED  = auto()

@dataclass
class Worker:
    phase: Phase = Phase.IDLE
    progress: int = 0

    def handle(self, event: str) -> None:
        """用 match/case 实现事件分派，清晰且可穷举"""
        match (self.phase, event):
            case (Phase.IDLE, "start"):
                self.phase = Phase.RUNNING
                print("任务开始")
            case (Phase.RUNNING, "pause"):
                self.phase = Phase.PAUSED
                print(f"已暂停，进度 {self.progress}%")
            case (Phase.RUNNING, "stop") | (Phase.PAUSED, "stop"):
                self.phase = Phase.STOPPED
                print("任务终止")
            case (Phase.PAUSED, "resume"):
                self.phase = Phase.RUNNING
                print("继续运行")
            case _:
                print(f"当前状态 {self.phase.name} 不支持事件 '{event}'")
```

**类层次 State 版**——适合每种状态行为复杂、需要独立测试的场景：

```python
from abc import ABC, abstractmethod
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from worker_context import WorkerContext  # 避免循环导入

class WorkerState(ABC):
    """抽象状态：声明所有可能的事件"""

    @abstractmethod
    def start(self, ctx: "WorkerContext") -> None: ...

    @abstractmethod
    def pause(self, ctx: "WorkerContext") -> None: ...

    @abstractmethod
    def stop(self, ctx: "WorkerContext") -> None: ...


class IdleState(WorkerState):
    def start(self, ctx: "WorkerContext") -> None:
        print("从空闲开始运行")
        ctx.state = RunningState()  # 内部触发状态转换

    def pause(self, ctx: "WorkerContext") -> None:
        print("空闲状态无法暂停")

    def stop(self, ctx: "WorkerContext") -> None:
        print("空闲状态已是停止")


class RunningState(WorkerState):
    def start(self, ctx: "WorkerContext") -> None:
        print("已经在运行中")

    def pause(self, ctx: "WorkerContext") -> None:
        print("暂停运行")
        ctx.state = PausedState()

    def stop(self, ctx: "WorkerContext") -> None:
        print("停止运行")
        ctx.state = IdleState()


class PausedState(WorkerState):
    def start(self, ctx: "WorkerContext") -> None:
        print("从暂停恢复运行")
        ctx.state = RunningState()

    def pause(self, ctx: "WorkerContext") -> None:
        print("已经在暂停状态")

    def stop(self, ctx: "WorkerContext") -> None:
        print("从暂停直接停止")
        ctx.state = IdleState()
```

**`match/case` vs 类层次**的选择：
- 状态 ≤ 5，每种状态逻辑 ≤ 10 行 → `match/case`，一目了然
- 状态较多，或每种状态需要独立单元测试 → 类层次，每个状态类单独 mock/测试
- 生产项目且状态转换复杂 → `transitions` 库，声明式更易维护

---

## 两种哲学

| 维度 | Java | Python |
|------|------|--------|
| 状态建模 | `enum` 抽象方法 / `sealed interface` | `enum.Enum` 带方法 / 类层次 |
| 穷举检查 | 编译期（`switch` 表达式 + sealed） | 运行期（`match/case` 不强制穷举） |
| 状态携带数据 | `sealed interface` + `record` | dataclass / NamedTuple |
| 声明式状态机 | 无官方支持，通常手写 | `transitions` 库，生产常用 |
| 典型场景 | 订单状态、线程生命周期 | Django 模型状态、异步任务 |
| 状态转换定义位置 | 状态类内部（enum 常量体） | Context 转换表 或 状态类内部均可 |

**核心差异在于编译期安全 vs 运行期灵活。**

Java 的 `enum` + 抽象方法和 `sealed interface` + `switch` 共同提供了**编译期穷举保证**：新增一种状态后，编译器会强制你处理所有分支，遗漏即报错。这是大型工程中状态机的重要安全网。

Python 的 `match/case` 默认不强制穷举（遗漏分支直接跳过），但灵活性更强——`transitions` 这类库可以在运行时动态注册状态和转换，适合规则驱动的配置化场景。

两种语言都应遵守同一个原则：**状态转换逻辑必须集中，不能散落在业务代码各处**。不论用 `enum`、`sealed interface`、转换表还是状态类，都要能在一处看到所有合法的状态和转换，这是状态机可维护性的根基。

---

## 动手练习

### 14.1 Java: 自动售货机状态机

用 `enum` 抽象方法实现一个自动售货机，具有以下状态和事件：

```
状态: NO_COIN（无硬币）、HAS_COIN（已投币）、SOLD（售出中）、SOLD_OUT（售罄）
事件: insertCoin()、ejectCoin()、turnCrank()、dispense()
```

```java
// 骨架（只写 package 声明，其余自己实现）
// 目标：
// - 每个枚举常量实现 4 个抽象方法（对应 4 个事件）
// - 非法操作打印提示（不抛异常），合法操作完成状态转换
// - VendingMachine 类持有当前状态和库存数量
// - 库存为 0 时，任何状态收到 dispense() 都转入 SOLD_OUT
package ch14_State;
```

参考输出：
```
当前状态: NO_COIN
> insertCoin: 已投入硬币
当前状态: HAS_COIN
> turnCrank: 咔哒！正在出货...
当前状态: SOLD
> dispense: 请取走您的商品
当前状态: NO_COIN
> ejectCoin: 没有可退回的硬币
```

### 14.2 Python: 用 `match`/`case` 实现红绿灯

```python
# 第一步：用 match/case 实现红绿灯状态机
# - 3 种状态：RED / GREEN / YELLOW
# - 1 种事件：tick()（每次 tick 推进到下一个状态）
# - 每种状态打印停留时长（红灯 60s，绿灯 45s，黄灯 5s）

# 第二步：用 transitions 库重写
# （提示：on_enter_<state> 钩子可以打印停留时长）

# 思考：如果新增"闪烁黄灯"（BLINK）状态，两种实现各需要改哪些地方？
```

### 14.3 思考题: 状态转换由谁触发？

> GoF 书中提到，状态转换可以由 **Context** 决定，也可以由**状态对象本身**决定。两种方式各有什么优缺点？

参考思路：
1. **Context 决定**：转换逻辑集中，易于一览全貌；但 Context 需要了解所有状态，状态增多时 Context 变复杂
2. **状态对象决定**：每个状态封装自己的后继，Context 保持简单；但状态之间产生耦合（A 状态知道 B 状态的存在）
3. **转换表**（Python `_transitions` 字典风格）：将转换规则外部化，最利于配置化和运行时修改

---

## 回顾与连接

**State vs Strategy（前置章节）**

这两个模式的类图几乎完全相同，区别纯粹在于**意图和使用方式**：

| | Strategy | State |
|---|---|---|
| 切换时机 | 客户端主动替换 | 内部事件自动触发 |
| 状态间耦合 | 策略互不知晓 | 状态知道后继状态 |
| 核心问题 | "用哪种算法？" | "当前能做什么？" |

**State + Singleton**

每种状态如果不持有实例变量，那么整个应用只需要一个 State 对象（无状态对象可以共享）。`enum` 常量天然是 Singleton，这也是为什么"enum 状态机"被广泛采用——它免费获得了线程安全的单例状态对象。

**一句话总结**

> State 把"随状态变化的行为"从条件分支里抽出来，封装进独立的状态对象——让对象的行为跟着状态走，而不是跟着 `if/else` 走。
