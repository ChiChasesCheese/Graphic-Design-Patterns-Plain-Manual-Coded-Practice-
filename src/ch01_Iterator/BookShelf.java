package ch01_Iterator;


public class BookShelf implements Aggregate {
    private final Book[] books;
    private int last;
    public BookShelf(int capacity) {
        this.books = new Book[capacity];
        this.last = 0;
    }

    public void append(Book book) {
        this.books[last++] = book;
    }
    public int getLength() {
        return this.last;
    }

    @Override
    public Iterator iterator() {
        return new BookShelfIterator(this);
    }

    public Book getBookAt(int idx) {
        return books[idx];
    }
}