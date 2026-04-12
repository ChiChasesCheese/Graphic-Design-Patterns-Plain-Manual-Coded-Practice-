package ch01_Iterator;

// ── 原始写法 ──────────────────────────────────────────────────────────────
// public class BookShelfIterator implements Iterator {
//     public Object next() {
//         return bookShelf.getBookAt(idx++);  ← 返回 Object，调用方必须强转
//     }
// }
// ─────────────────────────────────────────────────────────────────────────

// 泛型版本：实现 Iterator<Book>，next() 直接返回 Book，无需强转
public class BookShelfIterator implements Iterator<Book> {

    private final BookShelf bookShelf;
    private int idx;

    public BookShelfIterator(BookShelf bookShelf) {
        this.bookShelf = bookShelf;
        this.idx = 0;
    }

    @Override
    public boolean hasNext() {
        return idx < bookShelf.getLength();
    }

    @Override
    public Book next() {
        // 防御性检查：Iterator 契约要求 next() 在无元素时抛异常而非返回 null
        // 返回 null 会让 bug 延迟到调用方使用该值时才暴露，更难调试
        if (!hasNext()) {
            throw new java.util.NoSuchElementException(
                "No more books at index " + idx
            );
        }
        // ── idx++ vs ++idx ────────────────────────────────────────────
        // idx++ : 先返回当前值再自增 → 取第 idx 本书，然后 idx+1 指向下一本
        // ++idx : 先自增再返回新值 → 会永远跳过第 0 本书
        // ─────────────────────────────────────────────────────────────
        return bookShelf.getBookAt(idx++);
    }
}
