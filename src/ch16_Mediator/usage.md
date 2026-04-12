# Mediator 模式 — 真实应用

核心：**用一个中介者协调多个对象之间的交互，对象之间不直接引用彼此。**

---

## 1. Redux — 全局状态中介

Redux 的 Store 就是中介者：所有组件通过 Store 通信，不直接相互调用。

```typescript
// 没有中介者时（组件直接通信，混乱）
// ComponentA → ComponentB → ComponentC → ComponentA（循环依赖）

// 有中介者（Redux Store）
// ComponentA dispatch action → Store 处理 → 通知所有订阅者
// ComponentB dispatch action → Store 处理 → 通知所有订阅者

// userSlice.ts
const userSlice = createSlice({
    name: 'user',
    initialState: { data: null, loading: false },
    reducers: {
        loginSuccess: (state, action) => { state.data = action.payload; },
        logout: (state) => { state.data = null; },
    },
});

// Header 组件：通过 Store 读取状态
const user = useSelector(state => state.user.data);

// LoginForm 组件：通过 Store 发送事件
dispatch(userSlice.actions.loginSuccess(userData));

// UserAvatar 组件：也通过 Store 读取，Header 和 UserAvatar 不知道彼此存在
const user = useSelector(state => state.user.data);
```

---

## 2. .NET — MediatR 库

MediatR 是 .NET 生态里 Mediator 模式最流行的实现，
在 CQRS（命令查询职责分离）架构中广泛使用。

```csharp
// 命令（请求）
public record CreateOrderCommand(string UserId, List<OrderItem> Items)
    : IRequest<OrderResult>;

// 处理器（处理逻辑集中在中介者，Controller 不关心实现）
public class CreateOrderHandler : IRequestHandler<CreateOrderCommand, OrderResult> {
    public async Task<OrderResult> Handle(
        CreateOrderCommand request, CancellationToken ct) {
        // 创建订单逻辑
        var order = await _orderService.Create(request.UserId, request.Items);
        await _eventBus.Publish(new OrderCreatedEvent(order.Id));
        return new OrderResult(order.Id);
    }
}

// Controller：只发命令，不关心谁处理、怎么处理
[HttpPost]
public async Task<IActionResult> CreateOrder(CreateOrderRequest req) {
    var result = await _mediator.Send(new CreateOrderCommand(req.UserId, req.Items));
    return Ok(result);
}
```

---

## 3. Node.js — EventEmitter

Node.js 的 `EventEmitter` 是一个简化版 Mediator：
发布者和订阅者通过事件总线通信，不直接引用。

```javascript
const EventEmitter = require('events');
const bus = new EventEmitter();  // 中介者

// 订单服务：发布事件（不知道谁会处理）
class OrderService {
    async createOrder(data) {
        const order = await db.orders.create(data);
        bus.emit('order:created', order);  // 只告诉中介者
        return order;
    }
}

// 邮件服务：订阅事件（不知道谁发的）
bus.on('order:created', async (order) => {
    await emailService.sendConfirmation(order.userId, order);
});

// 库存服务：也订阅同一事件
bus.on('order:created', async (order) => {
    await inventoryService.reserve(order.items);
});

// OrderService、EmailService、InventoryService 互不引用
```

---

## 4. Apache Kafka — 分布式事件中介

Kafka 是分布式系统中 Mediator 模式的极致体现：
生产者和消费者完全解耦，通过 Topic（中介）通信。

```python
# 生产者（不知道消费者是谁）
from kafka import KafkaProducer
import json

producer = KafkaProducer(bootstrap_servers='kafka:9092',
                         value_serializer=lambda v: json.dumps(v).encode())

# 订单服务发布事件
producer.send('order-events', {
    'type': 'ORDER_CREATED',
    'orderId': '123',
    'userId': 'user-456',
    'amount': 99.99
})

# 消费者（不知道生产者是谁）
from kafka import KafkaConsumer

consumer = KafkaConsumer('order-events', bootstrap_servers='kafka:9092')
for message in consumer:
    event = json.loads(message.value)
    if event['type'] == 'ORDER_CREATED':
        send_confirmation_email(event['userId'], event['orderId'])
```

---

## 5. React — Context API

React Context 是轻量级 Mediator：
提供者和消费者通过 Context 通信，不需要逐层传 props。

```typescript
// 创建中介者（Context）
const ThemeContext = createContext<Theme>({ mode: 'light', color: 'blue' });

// 提供者：设置共享状态
function App() {
    const [theme, setTheme] = useState({ mode: 'dark', color: 'blue' });
    return (
        <ThemeContext.Provider value={{ theme, setTheme }}>
            <Layout />  {/* 中间层不需要知道 theme，也不传 props */}
        </ThemeContext.Provider>
    );
}

// 任意深度的消费者：直接从 Context 取，不经过中间组件
function DeepButton() {
    const { theme, setTheme } = useContext(ThemeContext);
    return (
        <button
            style={{ background: theme.mode === 'dark' ? '#333' : '#fff' }}
            onClick={() => setTheme(t => ({ ...t, mode: 'light' }))}>
            Toggle Theme
        </button>
    );
}
```

---

## Python 生态

Python 的 `asyncio.Queue` 天然就是异步 Mediator，生产者和消费者互不知晓彼此。

```python
import asyncio
from dataclasses import dataclass, field
from typing import Callable, Any
from collections import defaultdict

# 1. 事件总线 Mediator（同步版）
class EventBus:
    """中介者：组件之间通过事件总线通信，互不直接引用"""
    def __init__(self):
        self._handlers: dict[str, list[Callable]] = defaultdict(list)

    def subscribe(self, event: str, handler: Callable) -> None:
        self._handlers[event].append(handler)

    def unsubscribe(self, event: str, handler: Callable) -> None:
        self._handlers[event].remove(handler)

    def publish(self, event: str, **data) -> None:
        for handler in self._handlers[event]:
            handler(**data)

bus = EventBus()

class InventoryService:
    def __init__(self, bus: EventBus):
        bus.subscribe("order.created", self.on_order_created)

    def on_order_created(self, order_id: str, items: list, **_):
        print(f"[Inventory] Reserving items for order {order_id}: {items}")

class EmailService:
    def __init__(self, bus: EventBus):
        bus.subscribe("order.created", self.on_order_created)
        bus.subscribe("order.shipped", self.on_order_shipped)

    def on_order_created(self, order_id: str, user_email: str, **_):
        print(f"[Email] Confirmation sent to {user_email} for order {order_id}")

    def on_order_shipped(self, order_id: str, tracking: str, **_):
        print(f"[Email] Shipping notification: order {order_id}, tracking {tracking}")

inventory = InventoryService(bus)
email     = EmailService(bus)

# 发布者不知道谁会处理事件
bus.publish("order.created", order_id="ORD-001",
            items=["SKU-A", "SKU-B"], user_email="alice@example.com")

# 2. asyncio.Queue — 异步 Mediator（生产者/消费者解耦）
async def producer(queue: asyncio.Queue, name: str, items: list):
    for item in items:
        await queue.put({"source": name, "data": item})
        print(f"[Producer:{name}] queued {item}")
        await asyncio.sleep(0.1)
    await queue.put(None)   # 哨兵值：通知消费者结束

async def consumer(queue: asyncio.Queue, worker_id: int):
    while True:
        item = await queue.get()
        if item is None:
            queue.task_done()
            break
        print(f"[Consumer:{worker_id}] processing {item['data']} from {item['source']}")
        await asyncio.sleep(0.05)   # 模拟处理耗时
        queue.task_done()

async def main():
    queue = asyncio.Queue(maxsize=10)   # Queue 是 Mediator：生产者/消费者互不知晓
    await asyncio.gather(
        producer(queue, "web-scraper", ["url1", "url2", "url3"]),
        consumer(queue, worker_id=1),
    )

asyncio.run(main())

# 3. PyDispatcher / blinker — 第三方信号库（Flask 用 blinker）
# from blinker import signal
#
# user_registered = signal("user-registered")   # 命名信号
#
# @user_registered.connect
# def send_welcome_email(sender, user, **kwargs):
#     print(f"Welcome email sent to {user['email']}")
#
# @user_registered.connect
# def create_profile(sender, user, **kwargs):
#     print(f"Profile created for {user['id']}")
#
# # Flask 内部也用信号（Mediator）通知请求生命周期事件
# from flask import request_started, request_finished
```

> **Python 洞察**：`asyncio.Queue` 是 Python 异步编程中最核心的 Mediator——
> 生产者只管往队列放数据，消费者只管从队列取数据，二者完全解耦。
> Flask 的 `blinker` 信号、Django 的 `signals` 框架都是事件总线 Mediator 的生产实现。

---

## 关键洞察

> Mediator 解决的是**多对多通信的复杂度爆炸**问题。
> N 个对象直接通信需要 N×(N-1) 条连接，引入中介者只需要 N 条。
> Redux、Kafka、EventBus 都是这个思想在不同规模下的体现：
> 组件间 → Redux；服务间 → Kafka；进程内 → EventEmitter。
