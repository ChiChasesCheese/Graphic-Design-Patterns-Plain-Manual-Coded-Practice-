# State 模式 — 真实应用

核心：**对象在不同状态下行为不同，把每种状态的行为封装成独立类，用状态切换替代 if-else。**

---

## 1. XState — JavaScript 状态机库

XState 是目前最流行的前端状态机库，React/Vue/Svelte 都有集成。

```typescript
import { createMachine, assign } from 'xstate';

// 订单状态机
const orderMachine = createMachine({
    id: 'order',
    initial: 'pending',
    context: { orderId: '', retries: 0 },

    states: {
        pending: {
            on: {
                PAY: 'processing',
                CANCEL: 'cancelled',
            }
        },
        processing: {
            invoke: {
                src: 'processPayment',
                onDone: { target: 'confirmed' },
                onError: [
                    { guard: ctx => ctx.retries < 3, target: 'retrying',
                      actions: assign({ retries: ctx => ctx.retries + 1 }) },
                    { target: 'failed' }
                ]
            }
        },
        confirmed: {
            on: { SHIP: 'shipped' }
        },
        shipped: {
            on: { DELIVER: 'delivered' }
        },
        delivered: { type: 'final' },
        cancelled: { type: 'final' },
        failed:    { type: 'final' },
        retrying:  { after: { 2000: 'processing' } },  // 2秒后重试
    }
});

// 同一个 send('PAY') 在不同状态下行为完全不同——这就是 State 模式的核心
```

---

## 2. TCP 连接 — 协议级状态机

TCP 协议本身就是一个状态机，是 State 模式在网络协议层面的经典应用。

```
CLOSED
  │ connect()
  ▼
SYN_SENT ──── 超时 ──→ CLOSED
  │ SYN-ACK received
  ▼
ESTABLISHED  ←──── SYN received (server side)
  │ close()
  ▼
FIN_WAIT_1
  │ FIN-ACK
  ▼
FIN_WAIT_2
  │ FIN received
  ▼
TIME_WAIT
  │ 2MSL timeout
  ▼
CLOSED
```

```java
// Java NIO 里的 Channel 状态（简化）
SocketChannel channel = SocketChannel.open();
// 状态：UNCONNECTED → 只能调用 connect()

channel.connect(new InetSocketAddress("example.com", 80));
// 状态：CONNECTING → 只能调用 finishConnect()

channel.finishConnect();
// 状态：CONNECTED → 可以调用 read() / write()

channel.close();
// 状态：CLOSED → 所有操作抛 ClosedChannelException
```

---

## 3. React — `useState` + FSM 模式

React 的数据请求状态管理，用 State 模式替代多个独立 boolean。

```typescript
// ❌ 不好的写法：多个 boolean 状态，组合爆炸
const [isLoading, setIsLoading] = useState(false);
const [isError, setIsError] = useState(false);
const [isSuccess, setIsSuccess] = useState(false);
// isLoading=true 且 isSuccess=true 是合法状态吗？语义不清

// ✅ State 模式：用联合类型表达有限状态
type FetchState<T> =
    | { status: 'idle' }
    | { status: 'loading' }
    | { status: 'success'; data: T }
    | { status: 'error'; error: Error };

function useUser(id: string) {
    const [state, setState] = useState<FetchState<User>>({ status: 'idle' });

    const fetch = async () => {
        setState({ status: 'loading' });
        try {
            const data = await api.getUser(id);
            setState({ status: 'success', data });
        } catch (error) {
            setState({ status: 'error', error: error as Error });
        }
    };

    return { state, fetch };
}

// 使用：穷举状态，不可能漏掉情况
switch (state.status) {
    case 'idle':    return <button onClick={fetch}>Load</button>;
    case 'loading': return <Spinner />;
    case 'success': return <UserCard user={state.data} />;
    case 'error':   return <ErrorMessage error={state.error} />;
}
```

---

## 4. Spring Statemachine — 企业工作流

Spring 生态的状态机库，用于订单流转、审批流程、设备控制等复杂业务。

```java
@Configuration
@EnableStateMachine
public class OrderStateMachineConfig
        extends StateMachineConfigurerAdapter<OrderState, OrderEvent> {

    @Override
    public void configure(StateMachineStateConfigurer<OrderState, OrderEvent> states) throws Exception {
        states.withStates()
            .initial(PENDING)
            .states(EnumSet.allOf(OrderState.class))
            .end(DELIVERED).end(CANCELLED);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<OrderState, OrderEvent> transitions) throws Exception {
        transitions
            .withExternal().source(PENDING).target(PAID).event(PAYMENT_RECEIVED)
                .action(reserveInventory())
            .and()
            .withExternal().source(PAID).target(SHIPPED).event(SHIP)
                .action(sendShippingNotification())
            .and()
            .withExternal().source(PENDING).target(CANCELLED).event(CANCEL)
                .action(releaseInventory());
    }
}
```

---

## 5. Python — 文档审批流

业务系统里常见的多状态流转，用 State 模式避免 if-else 爆炸。

```python
from enum import Enum, auto
from abc import ABC, abstractmethod

class DocumentState(ABC):
    @abstractmethod
    def submit(self, doc): ...
    @abstractmethod
    def approve(self, doc): ...
    @abstractmethod
    def reject(self, doc): ...

class DraftState(DocumentState):
    def submit(self, doc):
        doc.state = PendingReviewState()
        print("Document submitted for review")
    def approve(self, doc): raise InvalidTransitionError("Draft cannot be approved directly")
    def reject(self, doc): raise InvalidTransitionError("Draft cannot be rejected")

class PendingReviewState(DocumentState):
    def submit(self, doc): raise InvalidTransitionError("Already submitted")
    def approve(self, doc):
        doc.state = ApprovedState()
        print("Document approved")
    def reject(self, doc):
        doc.state = DraftState()
        print("Document rejected, back to draft")

class Document:
    def __init__(self): self.state = DraftState()
    def submit(self): self.state.submit(self)
    def approve(self): self.state.approve(self)
    def reject(self): self.state.reject(self)
```

---

## Python 生态

Python 的 `transitions` 库是最流行的 FSM 库，`enum` + TypedDict 是轻量级状态机的惯用实现。

```python
# 1. enum + dict 转换表 — 轻量级状态机
from enum import Enum, auto
from typing import NamedTuple

class State(Enum):
    PENDING   = auto()
    PAID      = auto()
    SHIPPED   = auto()
    DELIVERED = auto()
    CANCELLED = auto()

class Event(Enum):
    PAY    = auto()
    SHIP   = auto()
    DELIVER = auto()
    CANCEL = auto()

# 转换表：(当前状态, 事件) → 下一状态
TRANSITIONS: dict[tuple[State, Event], State] = {
    (State.PENDING,   Event.PAY):     State.PAID,
    (State.PENDING,   Event.CANCEL):  State.CANCELLED,
    (State.PAID,      Event.SHIP):    State.SHIPPED,
    (State.PAID,      Event.CANCEL):  State.CANCELLED,
    (State.SHIPPED,   Event.DELIVER): State.DELIVERED,
}

class Order:
    def __init__(self, id: str):
        self.id = id
        self.state = State.PENDING

    def trigger(self, event: Event) -> bool:
        key = (self.state, event)
        if key not in TRANSITIONS:
            print(f"Invalid transition: {self.state.name} + {event.name}")
            return False
        self.state = TRANSITIONS[key]
        print(f"Order {self.id}: {self.state.name}")
        return True

order = Order("ORD-001")
order.trigger(Event.PAY)      # PAID
order.trigger(Event.CANCEL)   # CANCELLED（PAID → CANCEL 合法）
order.trigger(Event.SHIP)     # Invalid（已取消，无法发货）

# 2. transitions 库 — 功能完整的 Python FSM
# pip install transitions
# from transitions import Machine
#
# class TrafficLight:
#     states = ["red", "yellow", "green"]
#
#     def on_enter_red(self):    print("Stop!")
#     def on_enter_green(self):  print("Go!")
#     def on_enter_yellow(self): print("Slow down!")
#
# light = TrafficLight()
# machine = Machine(
#     model=light,
#     states=TrafficLight.states,
#     transitions=[
#         {"trigger": "go",   "source": "red",    "dest": "green"},
#         {"trigger": "slow", "source": "green",  "dest": "yellow"},
#         {"trigger": "stop", "source": "yellow", "dest": "red"},
#     ],
#     initial="red",
# )
# light.go()    # Green: Go!
# light.slow()  # Yellow: Slow down!
# light.stop()  # Red: Stop!

# 3. TypeScript 风格的 Python 状态类型（Python 3.10+ match）
from dataclasses import dataclass

@dataclass
class Idle: pass

@dataclass
class Loading:
    url: str

@dataclass
class Success:
    data: dict

@dataclass
class Error:
    message: str

FetchState = Idle | Loading | Success | Error

def render(state: FetchState) -> str:
    match state:
        case Idle():
            return "<button>Load</button>"
        case Loading(url=url):
            return f"<spinner>Loading {url}...</spinner>"
        case Success(data=data):
            return f"<div>{data}</div>"
        case Error(message=msg):
            return f"<error>{msg}</error>"

print(render(Loading(url="https://api.example.com/users")))
```

> **Python 洞察**：Python 3.10 的 `match/case` 是穷举状态的利器——
> 编辑器和类型检查器（mypy/pyright）会提示你是否漏掉了某个状态分支，
> 和 TypeScript 的联合类型 `switch` 效果相同，消除了状态管理中的隐式 bug。

---

## 关键洞察

> State 模式最重要的价值：**让非法状态在类型层面不可表达**。
> 当你看到代码里大量 `if status == 'A' and not is_processing` 这样的条件，
> 这就是状态管理混乱的信号。
> 现代最佳实践：用 TypeScript 联合类型 / Kotlin sealed class / Rust enum 表达有限状态，
> 编译器帮你检查状态穷举性。
