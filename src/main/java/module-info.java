module com.handmadeapp.handmademarketplaceapp {
    requires javafx.controls;
    requires javafx.fxml;

    opens com.handmadeapp.handmademarketplaceapp to javafx.fxml;
    exports com.handmadeapp.handmademarketplaceapp;
}
