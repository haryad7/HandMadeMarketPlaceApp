module com.handmadeapp.handmademarketplaceapp {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.base;
    requires java.sql;
    opens com.handmadeapp.handmademarketplaceapp to javafx.fxml;
    exports com.handmadeapp.handmademarketplaceapp;
    requires jbcrypt;
}
