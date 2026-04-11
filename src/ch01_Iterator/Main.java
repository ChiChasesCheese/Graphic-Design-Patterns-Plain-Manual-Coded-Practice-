package ch01_Iterator;


public class Main {
    public static void main(String[] args) {
        Book book1 = new Book("AAA");
        Book book2 = new Book("BBB");
        Book book3 = new Book("CCC");
        Book book4 = new Book("DDD");
        var bookShelf = new BookShelf(5);
        bookShelf.append(book1);
        bookShelf.append(book2);
        bookShelf.append(book3);
        bookShelf.append(book4);
        book1.setName("NEW");
        bookShelf.append(book1);
        Iterator it = bookShelf.iterator();
        while (it.hasNext()) {
            System.out.println(((Book)it.next()).getName());
        }
        it.resetIdx();
        while (it.hasNext()) {
            System.out.println(((Book)it.next()).getName());
        }
    }
}