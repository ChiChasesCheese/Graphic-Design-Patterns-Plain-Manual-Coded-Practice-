package ch01_Iterator;

import java.util.ArrayList;
import java.util.List;

// ── 原始写法 ──────────────────────────────────────────────────────────────
// public class BookShelf implements Aggregate {
//     private final Book[] books;   ← 固定容量，超出就越界
//     private int last;
//     public BookShelf(int capacity) {
//         this.books = new Book[capacity];
//         this.last = 0;
//     }
// }
// ─────────────────────────────────────────────────────────────────────────

// 改用 ArrayList<Book>：
//   1. 动态扩容，不需要调用方提前指定容量
//   2. 内置 size()，不需要自己维护 last 计数器
//   3. 面向接口编程：字段声明为 List<Book> 而非 ArrayList<Book>
//      → 将来换成 LinkedList 只改这一行，其余代码不动

// 同时实现 java.lang.Iterable<Book>：
//   使 BookShelf 支持 for-each 语法，是实际工程中最常见的做法
//   注意：Iterable 要求返回 java.util.Iterator，与我们自定义的 Iterator<T> 同名但不同类
// 注意：无法同时实现 Aggregate<Book> 和 Iterable<Book>——两者都要求 iterator()
// 但返回类型分别是自定义 Iterator<Book> 和 java.util.Iterator<Book>，
// Java 不允许同名同参数列表不同返回类型的方法共存（签名冲突）。
// 若想支持 for-each，应让自定义 Iterator<T> 继承 java.util.Iterator<T>，
// 或直接放弃自定义 Aggregate，只实现标准 Iterable<Book>。
public class BookShelf implements Aggregate<Book> {

    private final List<Book> books = new ArrayList<>();

    public void append(Book book) {
        books.add(book);
    }

    public Book getBookAt(int index) {
        return books.get(index);  // 越界时抛 IndexOutOfBoundsException，比数组更友好
    }

    public int getLength() {
        return books.size();
    }

    // 实现自定义 Aggregate<Book> —— 返回我们自己的迭代器（教材模式）
    @Override
    public Iterator<Book> iterator() {
        return new BookShelfIterator(this);
    }

    // 实现标准库 Iterable<Book> —— 让 for-each 语法生效
    // 委托给 ArrayList 自带的迭代器，无需自己实现
    // 注意：这里返回的是 java.util.Iterator，与上面的自定义 Iterator 不同
    public java.util.Iterator<Book> standardIterator() {
        return books.iterator();
    }

    // Java 9+ List.copyOf：返回不可变副本，外部无法通过此引用修改书架
    public List<Book> snapshot() {
        return List.copyOf(books);
    }
}
