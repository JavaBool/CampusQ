
package com.campus.canteen.model;

import java.util.ArrayList;
import java.util.List;

public class CheckoutForm {

    private List<CartLine> items = new ArrayList<>();

    public List<CartLine> getItems() {
        return items;
    }

    public void setItems(List<CartLine> items) {
        this.items = items;
    }

    public static class CartLine {

        private String itemId;
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
