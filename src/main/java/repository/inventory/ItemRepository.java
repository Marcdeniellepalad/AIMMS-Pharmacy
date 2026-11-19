package repository.inventory;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import model.inventory.Item;
import model.inventory.ItemStatus;
import repository.csv.CsvDataException;
import repository.csv.CsvFileManager;

public class ItemRepository {

    private static final Path FILE_PATH = Path.of("data/runtime/inventory/itemsDB.csv");
    private static final List<String> HEADERS = List.of(
            "itemID", "itemName", "category", "price", "prescriptionRequired", "status");
    private final CsvFileManager csvFileManager = new CsvFileManager();

    public List<Item> findAll() {
        return readItems();
    }

    public Optional<Item> findByID(String itemID) {
        if (itemID == null) {
            return Optional.empty();
        }
        return readItems().stream()
                .filter(item -> itemID.equals(item.getItemID()))
                .findFirst();
    }

    public List<Item> findByStatus(ItemStatus status) {
        return readItems().stream()
                .filter(item -> item.getStatus() == status)
                .toList();
    }

    public void save(Item item) {
        Objects.requireNonNull(item, "item must not be null");
        List<Item> items = readItems();
        if (items.stream().anyMatch(existing -> existing.getItemID().equals(item.getItemID()))) {
            throw new IllegalArgumentException("Item ID already exists: " + item.getItemID());
        }
        items.add(item);
        writeItems(items);
    }

    public void update(Item item) {
        Objects.requireNonNull(item, "item must not be null");
        List<Item> items = readItems();
        int existingIndex = findIndexByID(items, item.getItemID());
        if (existingIndex < 0) {
            throw new IllegalArgumentException("Cannot update unknown item ID: " + item.getItemID());
        }
        items.set(existingIndex, item);
        writeItems(items);
    }

    private List<Item> readItems() {
        List<Map<String, String>> records = csvFileManager.readRecords(FILE_PATH, HEADERS);
        List<Item> items = new ArrayList<>(records.size());
        Set<String> itemIDs = new HashSet<>();
        for (int index = 0; index < records.size(); index++) {
            Map<String, String> record = records.get(index);
            int recordNumber = index + 1;
            try {
                Item item = new Item(
                        required(record, "itemID"),
                        required(record, "itemName"),
                        required(record, "category"),
                        new BigDecimal(required(record, "price")),
                        parseBoolean(record.get("prescriptionRequired")),
                        ItemStatus.valueOf(required(record, "status")));
                if (!itemIDs.add(item.getItemID())) {
                    throw new IllegalArgumentException("duplicate itemID: " + item.getItemID());
                }
                items.add(item);
            } catch (IllegalArgumentException | NullPointerException ex) {
                throw new CsvDataException(FILE_PATH, recordNumber,
                        "invalid item record: " + ex.getMessage(), ex);
            }
        }
        return items;
    }

    private void writeItems(List<Item> items) {
        List<Map<String, String>> records = new ArrayList<>(items.size());
        for (Item item : items) {
            Objects.requireNonNull(item.getPrice(), "item price must not be null");
            Objects.requireNonNull(item.getStatus(), "item status must not be null");
            records.add(Map.of(
                    "itemID", nonBlank(item.getItemID(), "itemID"),
                    "itemName", nonBlank(item.getItemName(), "itemName"),
                    "category", nonBlank(item.getCategory(), "category"),
                    "price", item.getPrice().toPlainString(),
                    "prescriptionRequired", Boolean.toString(item.isPrescriptionRequired()),
                    "status", item.getStatus().name()));
        }
        csvFileManager.writeRecords(FILE_PATH, HEADERS, records);
    }

    private static String required(Map<String, String> record, String field) {
        String value = record.get(field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("prescriptionRequired must be true or false");
    }

    private static String nonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static int findIndexByID(List<Item> items, String itemID) {
        for (int index = 0; index < items.size(); index++) {
            if (Objects.equals(items.get(index).getItemID(), itemID)) {
                return index;
            }
        }
        return -1;
    }
}
