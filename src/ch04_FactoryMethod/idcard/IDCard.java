package ch04_FactoryMethod.idcard;

import ch04_FactoryMethod.framework.Product;

public class IDCard extends Product {
    private final String owner;

    IDCard(String name) {
        System.out.println("IDCard class 正在制作 " + name + "的 card");
        this.owner = name;
    }



    @Override
    public void use() {
        System.out.printf("We're using card {%s}: now%n", owner);
    }

    public String getOwner() {
        return owner;
    }
}