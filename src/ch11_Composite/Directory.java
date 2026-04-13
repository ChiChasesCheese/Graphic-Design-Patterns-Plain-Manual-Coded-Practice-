package ch11_Composite;

import java.util.ArrayList;
import java.util.Iterator;

import static java.lang.IO.print;
import static java.lang.IO.println;

public class Directory extends Entry {
    private final String name;
    private final ArrayList<Entry> directory = new ArrayList<>();

    public Directory(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getSize() {
        int size = 0;
        for (Entry entry : directory) {
            size += entry.getSize();
        }
        return size;
    }

    @Override
    public void printList(String prefix) {
        println(prefix + "/" + this);
        for (var entry: directory) {
            entry.printList(prefix + "/" + name);
        }
    }

    public Entry add(Entry entry) {
        directory.add(entry);
        return entry;
    }
}