package ch06_Prototype.framework;

import java.util.HashMap;

public class Manager {
    private final HashMap<String, Product> protoMap = new HashMap<>();

//    public Manager() {
//
//    }
    public void register(String name, Product p) {
        protoMap.put(name, p);
    }

    public Product create(String protoName) {
        return protoMap.get(protoName).createClone();
    }
}