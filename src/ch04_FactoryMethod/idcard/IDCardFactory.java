package ch04_FactoryMethod.idcard;

import ch04_FactoryMethod.framework.Factory;
import ch04_FactoryMethod.framework.Product;

import java.util.ArrayList;
import java.util.List;

public class IDCardFactory extends Factory {
    private final List<Product> owners = new ArrayList<>();

    @Override
    protected void registerProduct(Product p) {
        owners.add(p);
        System.out.println("Product " + p + " is registered");
    }

    @Override
    protected Product createProduct(String owner) {
        System.out.println("Product for " + owner + " is created");
        return new IDCard(owner);
    }

    public List<Product> getOwners() {
        return owners;
    }
}