
package com.campus.canteen.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Form data transfer object (DTO) used to bind cart submissions during checkout.
 *
 * <p>Security &amp; Design Notes:
 * <ul>
 *   <li>Supports indexed form binding in Spring MVC (e.g. {@code items[0].itemId}, {@code items[0].quantity}).</li>
 *   <li>Deliberately omits price fields: pricing is never accepted from the client and is instead
 *       resolved authoritatively from the database inside {@link com.campus.canteen.controller.OrderController}
 *       to prevent client-side price tampering.</li>
 * </ul>
 */
public class CheckoutForm {

    /** List of line items submitted by the client during cart checkout. */
    private List<CartLine> items = new ArrayList<>();

    public List<CartLine> getItems() {
        return items;
    }

    public void setItems(List<CartLine> items) {
        this.items = items;
    }

    /**
     * Represents a single menu item and requested quantity submitted from the shopping cart.
     */
    public static class CartLine {

        /** The menu item identifier. */
        private String itemId;

        /** The requested quantity of this item. */
        private int quantity;

        public String getItemId() {
            return itemId;
        }

        public void setItemId(String itemId) {
            this.itemId = itemId;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}
