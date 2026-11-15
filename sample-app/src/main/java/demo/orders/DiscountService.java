package demo.orders;

import java.math.BigDecimal;
import java.util.Map;

/** Sample code with a deliberate bug. It is the specimen the debugging agent investigates. */
public class DiscountService {

    private final Map<String, Coupon> coupons;

    public DiscountService(Map<String, Coupon> coupons) {
        this.coupons = coupons;
    }

    public BigDecimal applyCoupon(BigDecimal total, String couponCode) {
        Coupon coupon = coupons.get(couponCode);
        BigDecimal percent = coupon.percentOff();
        return total.subtract(total.multiply(percent).movePointLeft(2));
    }

    public record Coupon(String code, BigDecimal percentOff) {}
}
