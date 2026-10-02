package kaspe.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Satu baris laporan: gabungan header transaksi + detail, siap tampil di tabel/cetak. */
public class ReportRow {
    private int transactionId;
    private int detailId;
    private LocalDate date;
    private String plate;
    private String rentalName;
    private BigDecimal fieldWeight;
    private BigDecimal factoryWeight;
    private BigDecimal refractionPercent;
    private BigDecimal netWeight;
    private LocalDate paymentDate;
    private BigDecimal price;
    private BigDecimal totalAmount;

    public int getTransactionId() { return transactionId; }
    public void setTransactionId(int transactionId) { this.transactionId = transactionId; }
    public int getDetailId() { return detailId; }
    public void setDetailId(int detailId) { this.detailId = detailId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getPlate() { return plate; }
    /**
     * Plat truk, selalu dalam bentuk seragam.
     *
     * <p>Diseragamkan di sini, bukan hanya saat truk disimpan. Database yang sudah dipakai
     * bisa menyimpan plat dengan ejaan lama, dan baris laporan dibaca mentah dari sana —
     * tanpa ini, satu truk tampil dengan dua ejaan berbeda: daftar truk menulis
     * "BE 5555 XX" sedangkan laporan dan hasil cetaknya menulis "be 5555 xx".
     */
    public void setPlate(String plate) { this.plate = Truck.normalizePlate(plate); }
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
