package fr.traqueur.victor.entity;

import fr.traqueur.victor.annotations.Column;
import fr.traqueur.victor.annotations.Id;
import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.entity.Entity;
import fr.traqueur.victor.model.Priority;
import fr.traqueur.victor.model.TypesModel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Exercises the scalar type mappings (BigDecimal, date/time, short/byte, enum, UUID). */
@Table(table = "types_sample")
public record TypesEntity(
    @Id Long id,
    @Column BigDecimal priceAmount,
    @Column LocalDate eventDate,
    @Column LocalTime eventTime,
    @Column Short smallVal,
    @Column Byte tinyVal,
    @Column(length = 20) Priority priority,
    @Column UUID externalId
) implements Entity<TypesModel> {

    @Override
    public TypesModel toModel() {
        TypesModel m = new TypesModel();
        m.setId(id);
        m.setPriceAmount(priceAmount);
        m.setEventDate(eventDate);
        m.setEventTime(eventTime);
        m.setSmallVal(smallVal);
        m.setTinyVal(tinyVal);
        m.setPriority(priority);
        m.setExternalId(externalId);
        return m;
    }

    public static TypesEntity fromModel(TypesModel m) {
        return new TypesEntity(
            m.getId(),
            m.getPriceAmount(),
            m.getEventDate(),
            m.getEventTime(),
            m.getSmallVal(),
            m.getTinyVal(),
            m.getPriority(),
            m.getExternalId()
        );
    }
}
