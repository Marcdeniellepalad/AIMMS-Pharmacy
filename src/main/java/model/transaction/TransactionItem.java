package model.transaction;

import java.math.BigDecimal;

public class TransactionItem {

    private final String transactionItemID;
    private final String transactionID;
    private final String itemID;
    private final String batchID;
    private final String prescriptionItemID;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal subtotal;

    public TransactionItem(
            String transactionItemID,
            String transactionID,
            String itemID,
            String batchID,
            String prescriptionItemID,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal) {
        this.transactionItemID = transactionItemID;
        this.transactionID = transactionID;
        this.itemID = itemID;
        this.batchID = batchID;
        this.prescriptionItemID = prescriptionItemID;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
    }

    public String getTransactionItemID() {
        return transactionItemID;
    }

    public String getTransactionID() {
        return transactionID;
    }

    public String getItemID() {
        return itemID;
    }

    public String getBatchID() {
        return batchID;
    }

    public String getPrescriptionItemID() {
        return prescriptionItemID;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}
