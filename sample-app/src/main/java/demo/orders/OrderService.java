package demo.orders;

import java.math.BigDecimal;
import java.util.List;

/** Sample code with a deliberate bug. It is the specimen the debugging agent investigates. */
public class OrderService {

    private final DiscountService discounts;

    public OrderService(DiscountService discounts) {
        this.discounts = discounts;
    }

    public BigDecimal checkout(List<BigDecimal> itemPrices, String couponCode) {
        BigDecimal total = itemPrices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return discounts.applyCoupon(total, couponCode);
    }

    public int averageItemPriceCents(List<Integer> itemPricesCents) {
        int sum = 0;
        for (int price : itemPricesCents) {
            sum += price;
        }
        return sum / itemPricesCents.size();
    }
}
