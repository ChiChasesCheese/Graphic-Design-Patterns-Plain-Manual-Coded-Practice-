package ch04_FactoryMethod;

import ch04_FactoryMethod.framework.Product;
import ch04_FactoryMethod.idcard.IDCard;
import ch04_FactoryMethod.idcard.IDCardFactory;

import static java.lang.System.out;

public class Main {
    public static void main(String[] args) {
        IDCardFactory factory = new IDCardFactory();
        var card1 = factory.create("AAA");
        var card2 = factory.create("BBB");
        var card3 = factory.create("CCC");
        var card4 = factory.create("DDD");
        card1.use();
        card3.use();
        card2.use();
        factory.getOwners().forEach(owner -> {
            System.out.println(((IDCard) owner).getOwner());
        });
    }
}