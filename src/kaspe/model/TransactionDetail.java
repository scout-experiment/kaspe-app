package kaspe.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Satu baris buku = satu plat.
 * Urutan kolom buku:
 *   No | Plat | Nama Rental | Bobot Lapak | Bobot Pabrik | Refraksi
 *      | Berat Bersih | Tanggal Lunas | Harga | Jumlah Uang
 */
public class TransactionDetail {
    private int detailId;
    private int transactionId;
    private Integer truckId;
    private String plate;
    private String rentalName;
    private BigDecimal fieldWeight;
    private BigDecimal factoryWeight;
    private BigDecimal refractionPercent;
    private BigDecimal netWeight;
    private LocalDate paymentDate;
    private BigDecimal price;
    private BigDecimal totalAmount;

    public int getDetailId() { return detailId; }
    public void setDetailId(int detailId) { this.detailId = detailId; }

    public int getTransactionId() { return transactionId; }
    public void setTransactionId(int transactionId) { this.transactionId = transactionId; }

    public Integer getTruckId() { return truckId; }
    public void setTruckId(Integer truckId) { this.truckId = truckId; }

    public String getPlate() { return plate; }
    public void setPlate(String plate) { this.plate = plate; }

    public String getRentalName() { return rentalName; }
    public void setRentalName(String rentalName) { this.rentalName = rentalName; }

    public BigDecimal getFieldWeight() { return fieldWeight; }
    public void setFieldWeight(BigDecimal fieldWeight) { this.fieldWeight = fieldWeight; }

    public BigDecimal getFactoryWeight() { return factoryWeight; }
    public void setFactoryWeight(BigDecimal factoryWeight) { this.factoryWeight = factoryWeight; }

    public BigDecimal getRefractionPercent() { return refractionPercent; }
    public void setRefractionPercent(BigDecimal refractionPercent) { this.refractionPercent = refractionPercent; }

    public BigDecimal getNetWeight() { return netWeight; }
    public void setNetWeight(BigDecimal netWeight) { this.netWeight = netWeight; }

    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
}
