package fr.traqueur.victor.entity;

import fr.traqueur.victor.annotations.Column;
import fr.traqueur.victor.annotations.Id;
import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.annotations.VictorIndex;
import fr.traqueur.victor.entity.Entity;
import fr.traqueur.victor.model.Product;

@Table(table = "products")
@VictorIndex(columns = {"category", "price"}, name = "idx_products_category_price")
public record ProductEntity(
    @Id Long id,
    @Column(nullable = false, length = 100) String name,
    @Column(length = 50) @VictorIndex String category,
    @Column Double price,
    @Column(nullable = false, unique = true, length = 50) String sku
) implements Entity<Product> {

    @Override
    public Product toModel() {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setCategory(category);
        product.setPrice(price);
        product.setSku(sku);
        return product;
    }
}