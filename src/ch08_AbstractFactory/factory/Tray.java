package ch08_AbstractFactory.factory;


import java.util.ArrayList;

public abstract class Tray extends Item {
    public ArrayList<Item> tray = new ArrayList<>();
    public Tray(String caption) {
        super(caption);
    }
    public void add(Item item) {
        tray.add(item);
    }
}