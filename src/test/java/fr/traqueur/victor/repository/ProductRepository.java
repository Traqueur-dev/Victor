package fr.traqueur.victor.repository;

import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.dto.ProductDto;
import fr.traqueur.victor.entities.Product;

public interface ProductRepository extends Repository<ProductDto, Product, Long> {
}
