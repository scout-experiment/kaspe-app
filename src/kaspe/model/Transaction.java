package kaspe.model;

import java.time.LocalDate;

/** Header transaksi: satu nota, ditandai tanggalnya. */
public class Transaction {
    private int transactionId;
    private LocalDate date;

    public int getTransactionId() { return transactionId; }
    public void setTransactionId(int transactionId) { this.transactionId = transactionId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    @Override
    public String toString() { return date == null ? "" : date.toString(); }
}
