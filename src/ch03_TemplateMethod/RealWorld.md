# Template Method 现实应用

---

## 1. Spring Framework — `JdbcTemplate`

Spring 最经典的案例，名字里直接带 Template：

```java
// Spring 内部骨架（简化）
public abstract class JdbcTemplate {
    public final <T> List<T> query(String sql, RowMapper<T> mapper) {
        Connection conn = getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery();
        List<T> results = new ArrayList<>();
        while (rs.next()) {
            results.add(mapper.mapRow(rs));  // 变化点：你来决定怎么映射一行数据
        }
        closeConnection(conn);
        return results;
    }
}
```

你写的代码：
```java
List<User> users = jdbcTemplate.query(
    "SELECT * FROM users",
    (rs, rowNum) -> new User(rs.getLong("id"), rs.getString("name"))
);
```

重复的连接管理、异常处理、资源释放全在模板里，你只管怎么读一行数据。

---

## 2. Android — `Activity` 生命周期

```java
public abstract class Activity {
    final void performCreate() {
        onCreate();
        onStart();
        onResume();
    }
    protected void onCreate()  {}
    protected void onStart()   {}
    protected void onResume()  {}
}
```

```java
public class MainActivity extends Activity {
    @Override
    protected void onCreate() {
        setContentView(R.layout.main);
    }
}
```

Activity 的创建、销毁、内存回收顺序全由框架保证，你不可能写乱。

---

## 3. Java 标准库 — `AbstractList`

```java
public class MyList extends AbstractList<String> {
    @Override public String get(int index) { return data[index]; }
    @Override public int size() { return data.length; }
    // 自动获得 contains(), indexOf(), isEmpty(), iterator(), toArray()...
}
```

---

## 4. 数据处理流水线 — ETL

```java
public abstract class DataPipeline<T> {
    public final void run() {
        List<T> raw   = extract();
        List<T> clean = transform(raw);
        load(clean);
    }
    protected abstract List<T> extract();
    protected abstract List<T> transform(List<T> data);
    protected abstract void    load(List<T> data);
}
```

---

## 5. 单元测试框架 — JUnit

```java
abstract class TestCase {
    final void runTest() {
        setUp();
        test();
        tearDown();
    }
    protected void setUp()    {}
    protected void tearDown() {}
    protected abstract void test();
}
```

---

## 规律总结

| 特征 | 例子 |
|------|------|
| 流程固定，细节可变 | 生命周期、ETL、setUp/tearDown |
| 框架控制主流程，用户填空 | Spring、Android、JUnit |
| 避免重复的资源管理代码 | JdbcTemplate 的连接/关闭 |
| 类名带 `Abstract` 或 `Template` | `AbstractList`、`JdbcTemplate` |

> 碰到"骨架代码到处一样，只有中间某几步不同"——Template Method 就是答案。
