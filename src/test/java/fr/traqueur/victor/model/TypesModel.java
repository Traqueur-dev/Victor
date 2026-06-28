package fr.traqueur.victor.model;

import fr.traqueur.victor.entity.Model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public class TypesModel implements Model<Long> {

    private Long id;
    private BigDecimal priceAmount;
    private LocalDate eventDate;
    private LocalTime eventTime;
    private Short smallVal;
    private Byte tinyVal;
    private Priority priority;
    private UUID externalId;

    public TypesModel() {}

    @Override
    public Long getId() { return id; }

    @Override
    public void setId(Long id) { this.id = id; }

    public BigDecimal getPriceAmount() { return priceAmount; }
    public void setPriceAmount(BigDecimal priceAmount) { this.priceAmount = priceAmount; }

    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }

    public LocalTime getEventTime() { return eventTime; }
    public void setEventTime(LocalTime eventTime) { this.eventTime = eventTime; }

    public Short getSmallVal() { return smallVal; }
    public void setSmallVal(Short smallVal) { this.smallVal = smallVal; }

    public Byte getTinyVal() { return tinyVal; }
    public void setTinyVal(Byte tinyVal) { this.tinyVal = tinyVal; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    public UUID getExternalId() { return externalId; }
    public void setExternalId(UUID externalId) { this.externalId = externalId; }
}
