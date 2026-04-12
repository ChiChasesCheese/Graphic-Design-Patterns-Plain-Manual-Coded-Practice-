package ch01_Iterator;

// ── 原始写法的问题 ────────────────────────────────────────────────────────
// Book book1 = new Book("AAA");
// book1.setName("NEW");               ← mutable：改 book1 同时影响书架里的引用
// var bookShelf = new BookShelf(5);   ← 需要预设容量
// Iterator it = bookShelf.iterator(); ← 原始类型（raw type），无泛型保障
// ((Book) it.next()).getName()        ← 每次都要强转
// it.resetIdx();                      ← 非标准，复用了迭代器
// ─────────────────────────────────────────────────────────────────────────

public class Main {
    public static void main(String[] args) {

        // var（Java 10+）：编译器推断类型，减少重复；类型仍是静态的，不是动态类型
        var shelf = new BookShelf();

        // record 不可变：没有 setName，"修改"产生新对象
        shelf.append(new Book("AAA"));
        shelf.append(new Book("BBB"));
        shelf.append(new Book("CCC"));
        shelf.append(new Book("DDD"));

        // ── 写法一：自定义 Iterator（教材模式） ──────────────────────────
        System.out.println("── 自定义 Iterator ──");
        Iterator<Book> it = shelf.iterator();
        while (it.hasNext()) {
            System.out.println(it.next().name()); // 泛型：无需强转；record：用 name() 而非 getName()
        }

        // "重置"的正确做法：丢弃旧迭代器，重新获取新的
        System.out.println("── 再次遍历（新迭代器） ──");
        for (var it2 = shelf.iterator(); it2.hasNext(); ) {
            System.out.println(it2.next().name());
        }

        // ── 写法二：Stream API（Java 8+，函数式风格） ─────────────────────
        // 适合需要 filter / map / collect 等链式操作的场景
        System.out.println("── Stream ──");
        shelf.snapshot()
             .stream()
             .map(Book::name)          // 方法引用，等价于 b -> b.name()
             .forEach(System.out::println);

        // ── for-each 的本质（脱糖后） ─────────────────────────────────────
        // for (Book b : shelf) { ... }  编译器展开为：
        //   java.util.Iterator<Book> _i = shelf.standardIterator();
        //   while (_i.hasNext()) { Book b = _i.next(); ... }
        // 需要 BookShelf 实现 java.lang.Iterable，见 BookShelf.standardIterator()
    }
}
