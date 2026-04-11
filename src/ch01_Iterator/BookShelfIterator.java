package ch01_Iterator;

public class BookShelfIterator implements Iterator {
    private int idx;
    private final BookShelf bookShelf;

    public BookShelfIterator(BookShelf bookShelf) {
        this.bookShelf = bookShelf;
        this.idx = 0;
    }
    @Override
    public void resetIdx() {
        this.idx = 0;
    }

    @Override
    public boolean hasNext() {
        return this.bookShelf.getLength() > idx;
    }

    @Override
    public Object next() {
        return bookShelf.getBookAt(idx++);
    }
}