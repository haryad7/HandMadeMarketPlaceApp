package com.handmadeapp.handmademarketplaceapp;

/**
 * Holds in-flight checkout data while the user moves between
 * Order -> Payment -> OrderConfirmation screens.
 *
 * For a real cart you would replace `cartTotal` with a List<CartItem>.
 */
public class CheckoutContext {
    private static int    shippingAddressId = 0;
    private static int    shopId            = 0;
    private static double cartTotal         = 0.0;
    private static int    orderId           = 0;
    private static int    paymentId         = 0;

    public static int    getShippingAddressId()           { return shippingAddressId; }
    public static void   setShippingAddressId(int id)     { shippingAddressId = id; }

    public static int    getShopId()                      { return shopId; }
    public static void   setShopId(int id)                { shopId = id; }

    public static double getCartTotal()                   { return cartTotal; }
    public static void   setCartTotal(double t)           { cartTotal = t; }

    public static int    getOrderId()                     { return orderId; }
    public static void   setOrderId(int id)               { orderId = id; }

    public static int    getPaymentId()                   { return paymentId; }
    public static void   setPaymentId(int id)             { paymentId = id; }

    public static void reset() {
        shippingAddressId = 0; shopId = 0; cartTotal = 0.0; orderId = 0; paymentId = 0;
    }
}
