package ch11_Composite;

import static java.lang.IO.print;
import static java.lang.IO.println;

public class File extends Entry {

    private final String name;
    private final int size;

    public File(String name, int size) {
        this.name = name;
        this.size = size;
    }
    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public void printList(String prefix) {
        println(prefix + "/" + this);
    }
}